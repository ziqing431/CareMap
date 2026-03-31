package com.example.caremap

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.example.caremap.data.voice.IflytekSparkManager

class CareMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        IflytekSparkManager.ensureInitialized(this)
    }
}
