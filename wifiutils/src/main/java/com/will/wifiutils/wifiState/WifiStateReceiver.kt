package com.will.wifiutils.wifiState

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager

class WifiStateReceiver(private val wifiStateCallback: WifiStateCallback) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
        when (wifiState) {
            WifiManager.WIFI_STATE_ENABLED -> wifiStateCallback.onWifiEnabled()
            WifiManager.WIFI_STATE_ENABLING -> {
            }
            WifiManager.WIFI_STATE_DISABLING -> {
            }
            WifiManager.WIFI_STATE_DISABLED -> {
            }
        }
    }
}