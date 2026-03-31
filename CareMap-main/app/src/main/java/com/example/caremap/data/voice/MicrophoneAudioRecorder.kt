package com.example.caremap.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class MicrophoneAudioRecorder(
    private val onAudioData: (ByteArray) -> Unit,
) {
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Result<Unit> {
        if (isRecording.get()) {
            return Result.success(Unit)
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        if (minBufferSize <= 0) {
            return Result.failure(IllegalStateException("麦克风初始化失败"))
        }

        val bufferSize = max(minBufferSize, FRAME_BUFFER_SIZE)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return Result.failure(IllegalStateException("麦克风不可用，请检查录音权限"))
        }

        isRecording.set(true)
        recordingThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(bufferSize)
            try {
                recorder.startRecording()
                while (isRecording.get()) {
                    val size = recorder.read(buffer, 0, buffer.size)
                    if (size > 0 && isRecording.get()) {
                        onAudioData(buffer.copyOf(size))
                    }
                }
            } finally {
                try {
                    recorder.stop()
                } catch (_: IllegalStateException) {
                }
                recorder.release()
            }
        }.apply { start() }

        return Result.success(Unit)
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) {
            return
        }
        recordingThread?.interrupt()
        recordingThread = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_BUFFER_SIZE = 4_096
    }
}
