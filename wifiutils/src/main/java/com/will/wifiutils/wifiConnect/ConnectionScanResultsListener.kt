package com.will.wifiutils.wifiConnect

import android.net.wifi.ScanResult

interface ConnectionScanResultsListener {
    fun onConnectWithScanResult(scanResults: List<ScanResult?>): ScanResult?
}