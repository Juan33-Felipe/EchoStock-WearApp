package com.echostock.wear

import android.app.Application

class EchoStockApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scheduler.scheduleCatalogRefresh()
        // Por si quedaron auditorías en cola de una sesión anterior.
        container.scheduler.requestSync()
    }
}
