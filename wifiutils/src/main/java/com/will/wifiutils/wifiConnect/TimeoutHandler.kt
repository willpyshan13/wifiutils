package com.will.wifiutils.wifiConnect

import android.net.wifi.ScanResult
import com.will.wifiutils.ConnectorUtils.reEnableNetworkIfPossible
import com.will.wifiutils.ConnectorUtils.isAlreadyConnected
import android.net.wifi.WifiManager
import com.will.wifiutils.WeakHandler
import com.will.wifiutils.WifiUtils
import com.will.wifiutils.utils.Elvis
import com.will.wifiutils.utils.VersionUtils.isAndroidQOrLater

class TimeoutHandler(
    private val mWifiManager: WifiManager,
    private val mHandler: WeakHandler,
    private val mWifiConnectionCallback: WifiConnectionCallback
) {
    private var mScanResult: ScanResult? = null
    private val timeoutCallback: Runnable = object : Runnable {
        override fun run() {
            WifiUtils.wifiLog("Connection Timed out...")
            if (!isAndroidQOrLater) {
                reEnableNetworkIfPossible(mWifiManager, mScanResult)
            }
            if (isAlreadyConnected(
                    mWifiManager,
                    Elvis.of(mScanResult).next { scanResult: ScanResult? -> scanResult!!.BSSID }
                        .get())
            ) {
                mWifiConnectionCallback.successfulConnect()
            } else {
                mWifiConnectionCallback.errorConnect(ConnectionErrorCode.TIMEOUT_OCCURRED)
            }
            mHandler.removeCallbacks(this)
        }
    }

    fun startTimeout(scanResult: ScanResult?, timeout: Long) {
        // cleanup previous connection timeout handler
        mHandler.removeCallbacks(timeoutCallback)
        mScanResult = scanResult
        mHandler.postDelayed(timeoutCallback, timeout)
    }

    fun stopTimeout() {
        mHandler.removeCallbacks(timeoutCallback)
    }
}