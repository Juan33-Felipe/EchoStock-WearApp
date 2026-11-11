package com.echostock.wear

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.echostock.wear.data.AuditRepository
import com.echostock.wear.data.ProductRepository
import com.echostock.wear.data.ServerStatus
import com.echostock.wear.data.ThemePreferences
import com.echostock.wear.data.local.LocalDatabase
import com.echostock.wear.data.remote.EchoStockApi
import com.echostock.wear.data.sync.WorkManagerSyncScheduler
import com.echostock.wear.platform.Haptics

/** Inyección de dependencias manual: un único grafo de objetos por proceso. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val serverStatus = ServerStatus()
    private val api = EchoStockApi.create(BuildConfig.API_BASE_URL, BuildConfig.API_KEY, BuildConfig.DEBUG)
    private val db = LocalDatabase.create(appContext)
    val scheduler = WorkManagerSyncScheduler(appContext)

    val products = ProductRepository(api, db.products(), serverStatus)
    val audits = AuditRepository(api, db.pendingAudits(), scheduler, serverStatus)

    val haptics = Haptics(appContext)
    val theme = ThemePreferences(appContext)

    val operatorCode: String = BuildConfig.OPERATOR_CODE

    @SuppressLint("HardwareIds")
    val deviceId: String = "wear-" +
        (Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "desconocido")
}
