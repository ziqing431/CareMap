package com.example.caremap.data.voice

import android.content.Context
import com.example.caremap.domain.service.VoiceRecognizer
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class IflytekAikitVoiceRecognizer(
    context: Context,
) : VoiceRecognizer {
    private val appContext = context.applicationContext
    private val lock = Any()

    private var asr: ASR? = null
    private var recorder: MicrophoneAudioRecorder? = null
    private var continuation: CancellableContinuation<Result<String>>? = null
    private var latestResult = ""
    private var isListening = false
    private var awaitingFinalResult = false

    override suspend fun listenOnce(): Result<String> = withContext(Dispatchers.IO) {
        val initResult = IflytekSparkManager.ensureInitialized(appContext)
        if (initResult.isFailure) {
            return@withContext Result.failure(initResult.exceptionOrNull()!!)
        }

        try {
            withTimeout(20_000L) {
                suspendCancellableCoroutine { cont ->
                    val startResult = synchronized(lock) {
                        stopListeningLocked(resumeCancelled = false)
                        ensureAsrLocked()
                        configureAsrLocked()

                        latestResult = ""
                        isListening = true
                        awaitingFinalResult = false
                        continuation = cont

                        val startCode = asr?.start("caremap-${System.currentTimeMillis()}") ?: -1
                        if (startCode != 0) {
                            Result.failure(IllegalStateException("语音识别启动失败($startCode)"))
                        } else {
                            recorder = MicrophoneAudioRecorder { audioData ->
                                writeAudio(audioData)
                            }
                            recorder?.start()
                                ?: Result.failure(IllegalStateException("麦克风初始化失败"))
                        }
                    }

                    if (startResult.isFailure) {
                        synchronized(lock) {
                            stopListeningLocked(resumeCancelled = false)
                        }
                        cont.resume(Result.failure(startResult.exceptionOrNull()!!))
                        return@suspendCancellableCoroutine
                    }

                    cont.invokeOnCancellation {
                        synchronized(lock) {
                            stopListeningLocked(resumeCancelled = false)
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            stopListening()
            Result.failure(IllegalStateException("没有听清，请再说一次"))
        }
    }

    override fun stopListening() {
        synchronized(lock) {
            stopListeningLocked(resumeCancelled = true)
        }
    }

    private fun ensureAsrLocked() {
        if (asr == null) {
            asr = ASR().apply {
                registerCallbacks(callbacks)
            }
        }
    }

    private fun configureAsrLocked() {
        asr?.language("zh_cn")
        asr?.domain("iat")
        asr?.accent("mandarin")
        asr?.vinfo(true)
        asr?.dwa("wpgs")
    }

    private fun writeAudio(audioData: ByteArray) {
        synchronized(lock) {
            if (!isListening || awaitingFinalResult) return
            val ret = asr?.write(audioData) ?: -1
            if (ret != 0) {
                resumeFailureLocked("语音输入中断，请再试一次")
            }
        }
    }

    private fun finishInputLocked() {
        if (!isListening || awaitingFinalResult) return
        awaitingFinalResult = true
        recorder?.stop()
        recorder = null
        asr?.stop(false)
    }

    private fun stopListeningLocked(resumeCancelled: Boolean) {
        val pending = continuation
        continuation = null
        recorder?.stop()
        recorder = null
        if (isListening) {
            try {
                asr?.stop(true)
            } catch (_: Exception) {
            }
        }
        latestResult = ""
        isListening = false
        awaitingFinalResult = false
        if (resumeCancelled && pending?.isActive == true) {
            pending.resume(Result.failure(IllegalStateException("语音输入已取消")))
        }
    }

    private fun resumeSuccessLocked(text: String) {
        val pending = continuation
        continuation = null
        recorder?.stop()
        recorder = null
        latestResult = ""
        isListening = false
        awaitingFinalResult = false
        if (pending?.isActive == true) {
            pending.resume(Result.success(text))
        }
    }

    private fun resumeFailureLocked(message: String) {
        val pending = continuation
        continuation = null
        recorder?.stop()
        recorder = null
        latestResult = ""
        isListening = false
        awaitingFinalResult = false
        if (pending?.isActive == true) {
            pending.resume(Result.failure(IllegalStateException(message)))
        }
    }

    private val callbacks = object : AsrCallbacks {
        override fun onResult(asrResult: ASR.ASRResult?, userTag: Any?) {
            if (asrResult == null) return
            synchronized(lock) {
                val text = asrResult.bestMatchText?.trim().orEmpty()
                if (text.isNotBlank()) {
                    latestResult = text
                }
                if (asrResult.status == 2) {
                    val finalText = latestResult.ifBlank { text }
                    if (finalText.isBlank()) {
                        resumeFailureLocked("没有听清，请再说一次")
                    } else {
                        resumeSuccessLocked(finalText)
                    }
                }
            }
        }

        override fun onError(asrError: ASR.ASRError?, userTag: Any?) {
            synchronized(lock) {
                val code = asrError?.code ?: -1
                val rawMessage = asrError?.errMsg.orEmpty()
                val message = when {
                    rawMessage.contains("timeout", ignoreCase = true) -> "没有听清，请再说一次"
                    rawMessage.contains("network", ignoreCase = true) -> "网络异常，请检查后重试"
                    code == 10114 || code == 10019 -> "没有听清，请再说一次"
                    else -> "语音识别失败($code)：${rawMessage.ifBlank { "请稍后重试" }}"
                }
                resumeFailureLocked(message)
            }
        }

        override fun onBeginOfSpeech() = Unit

        override fun onEndOfSpeech() {
            synchronized(lock) {
                finishInputLocked()
            }
        }
    }
}
