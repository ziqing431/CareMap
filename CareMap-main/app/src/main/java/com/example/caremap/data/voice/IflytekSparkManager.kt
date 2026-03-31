package com.example.caremap.data.voice

import android.content.Context
import com.example.caremap.BuildConfig
import com.iflytek.sparkchain.core.LogLvl
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import java.io.File

object IflytekSparkManager {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context): Result<Unit> {
        if (initialized) {
            return Result.success(Unit)
        }
        if (!BuildConfig.HAS_IFLYTEK_CONFIG) {
            return Result.failure(
                IllegalStateException("请先在 local.properties 中配置 iflytek.appid / iflytek.api_key / iflytek.api_secret")
            )
        }

        val logDir = File(context.filesDir, "iflytek").apply { mkdirs() }
        val config = SparkChainConfig.builder()
            .appID(BuildConfig.IFLYTEK_APP_ID)
            .apiKey(BuildConfig.IFLYTEK_API_KEY)
            .apiSecret(BuildConfig.IFLYTEK_API_SECRET)
            .logPath(File(logDir, "SparkChain.log").absolutePath)
            .logLevel(LogLvl.VERBOSE.getValue())

        val ret = SparkChain.getInst().init(context.applicationContext, config)
        return if (ret == 0) {
            initialized = true
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("讯飞 AIKit 初始化失败($ret)"))
        }
    }
}
