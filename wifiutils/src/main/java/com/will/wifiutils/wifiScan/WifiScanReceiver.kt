package com.will.wifiutils.wifiScan

import com.will.wifiutils.wifiDisconnect.DisconnectionErrorCode
import com.will.wifiutils.wifiRemove.RemoveErrorCode
import com.will.wifiutils.wifiScan.WifiScanCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.will.wifiutils.wifiState.WifiStateCallback
import android.net.wifi.WifiManager

class WifiScanReceiver(private val callback: WifiScanCallback) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        callback.onScanResultsReady()
    }
}