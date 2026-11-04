package com.vrgames.bianzhong.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.bianzhong.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
