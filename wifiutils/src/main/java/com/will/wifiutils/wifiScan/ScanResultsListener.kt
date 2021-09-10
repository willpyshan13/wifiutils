package com.will.wifiutils.wifiScan

import android.net.wifi.ScanResult

interface ScanResultsListener {
    fun onScanResults(scanResults: List<ScanResult?>)
}