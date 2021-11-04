package com.will.wifiutils

import android.os.Build
import com.will.wifiutils.wifiState.WifiStateListener
import com.will.wifiutils.wifiScan.ScanResultsListener
import com.will.wifiutils.wifiDisconnect.DisconnectionSuccessListener
import com.will.wifiutils.wifiRemove.RemoveSuccessListener
import com.will.wifiutils.wifiConnect.ConnectionScanResultsListener
import androidx.annotation.RequiresApi
import com.will.wifiutils.wifiConnect.ConnectionSuccessListener
import com.will.wifiutils.wifiWps.ConnectionWpsListener

interface WifiConnectorBuilder {
    fun start()
    interface WifiUtilsBuilder {
        fun enableWifi(wifiStateListener: WifiStateListener?)
        fun enableWifi()
        fun disableWifi()
        fun scanWifi(scanResultsListener: ScanResultsListener?): WifiConnectorBuilder
        fun connectWith(ssid: String): WifiSuccessListener
        fun connectWith(ssid: String, password: String): WifiSuccessListener
        fun connectWith(ssid: String, password: String,hide:Boolean = true): WifiSuccessListener
        fun connectWith(ssid: String, bssid: String, password: String): WifiSuccessListener
        fun connectWith(ssid: String, password: String, type: TypeEnum): WifiSuccessListener
        fun patternMatch(): WifiUtilsBuilder

        @Deprecated("")
        fun disconnectFrom(ssid: String, disconnectionSuccessListener: DisconnectionSuccessListener)
        fun disconnect(disconnectionSuccessListener: DisconnectionSuccessListener)
        fun remove(ssid: String, removeSuccessListener: RemoveSuccessListener)
        fun connectWithScanResult(
            password: String,
            connectionScanResultsListener: ConnectionScanResultsListener?
        ): WifiSuccessListener

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        fun connectWithWps(bssid: String, password: String): WifiWpsSuccessListener
        fun cancelAutoConnect()
        fun isWifiConnected(ssid: String): Boolean
        val isWifiConnected: Boolean
    }

    interface WifiSuccessListener {
        fun setTimeout(timeOutMillis: Long): WifiSuccessListener
        fun onConnectionResult(successListener: ConnectionSuccessListener?): WifiConnectorBuilder
    }

    interface WifiWpsSuccessListener {
        fun setWpsTimeout(timeOutMillis: Long): WifiWpsSuccessListener

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        fun onConnectionWpsResult(successListener: ConnectionWpsListener?): WifiConnectorBuilder
    }
}