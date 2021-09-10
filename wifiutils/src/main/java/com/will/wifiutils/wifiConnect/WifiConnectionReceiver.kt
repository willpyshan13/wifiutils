package com.will.wifiutils.wifiConnect

import com.will.wifiutils.ConnectorUtils.isAlreadyConnected
import com.will.wifiutils.ConnectorUtils.reEnableNetworkIfPossible
import android.net.wifi.WifiManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.will.wifiutils.WifiUtils
import android.net.wifi.SupplicantState
import com.will.wifiutils.utils.Elvis
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import com.will.wifiutils.utils.VersionUtils.isAndroidQOrLater

class WifiConnectionReceiver(
    private val mWifiConnectionCallback: WifiConnectionCallback,
    private val mWifiManager: WifiManager
) : BroadcastReceiver() {
    private var mScanResult: ScanResult? = null
    private var ssid: String? = null
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        WifiUtils.wifiLog("Connection Broadcast action: $action")
        if (isAndroidQOrLater) {
            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION == action) {
                val isConnectWithResult =
                    mScanResult != null && isAlreadyConnected2(mWifiManager, mScanResult!!.SSID)
                val isConnectWithSsid =
                    mScanResult == null && isAlreadyConnected2(mWifiManager, ssid)
                val state = intent.getParcelableExtra<SupplicantState>(WifiManager.EXTRA_NEW_STATE)
                WifiUtils.wifiLog("state=$state mScanResult $mScanResult  isConnectWithResult=$isConnectWithResult")
                val suppl_error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1)
                WifiUtils.wifiLog("Connection Broadcast state: $state")
                WifiUtils.wifiLog("suppl_error: $suppl_error")
                if (isConnectWithResult || isConnectWithSsid) {
                    mWifiConnectionCallback.successfulConnect()
                }
                if (state == SupplicantState.DISCONNECTED && suppl_error == WifiManager.ERROR_AUTHENTICATING) {
                    mWifiConnectionCallback.errorConnect(ConnectionErrorCode.AUTHENTICATION_ERROR_OCCURRED)
                }
            }
        } else {
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION == action) {
                /*
                    Note here we don't check if has internet connectivity, because we only validate
                    if the connection to the hotspot is active, and not if the hotspot has internet.
                 */
                if (isAlreadyConnected(
                        mWifiManager,
                        Elvis.of(mScanResult).next { scanResult: ScanResult? -> scanResult!!.BSSID }
                            .get())
                ) {
                    mWifiConnectionCallback.successfulConnect()
                }
            } else if (ConnectivityManager.CONNECTIVITY_ACTION == action) {
                WifiUtils.wifiLog("Connection CONNECTIVITY_ACTION state: ")
                //获取联网状态的NetworkInfo对象
                val info =
                    intent.getParcelableExtra<NetworkInfo>(ConnectivityManager.EXTRA_NETWORK_INFO)
                if (info != null) {
                    WifiUtils.wifiLog("Connection CONNECTIVITY_ACTION start check: ")
                    //如果当前的网络连接成功并且网络连接可用
                    if (NetworkInfo.State.CONNECTED == info.state && info.isAvailable) {
                        mWifiConnectionCallback.successfulConnect()
                    }
                }
            } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION == action) {
                val state = intent.getParcelableExtra<SupplicantState>(WifiManager.EXTRA_NEW_STATE)
                val supl_error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1)
                if (state == null) {
                    mWifiConnectionCallback.errorConnect(ConnectionErrorCode.COULD_NOT_CONNECT)
                    return
                }
                WifiUtils.wifiLog("Connection Broadcast state: $state")
                when (state) {
                    SupplicantState.COMPLETED, SupplicantState.FOUR_WAY_HANDSHAKE -> {
                        val isConnectWithResult = mScanResult != null && isAlreadyConnected2(
                            mWifiManager,
                            mScanResult!!.SSID
                        )
                        WifiUtils.wifiLog("state=$state mScanResult $mScanResult  isConnectWithResult=$isConnectWithResult")
                        if (isConnectWithResult || isAlreadyConnected2(mWifiManager, ssid)) {
                            mWifiConnectionCallback.successfulConnect()
                        } else if (isAlreadyConnected(
                                mWifiManager,
                                Elvis.of(mScanResult)
                                    .next { scanResult: ScanResult? -> scanResult!!.BSSID }
                                    .get())
                        ) {
                            mWifiConnectionCallback.successfulConnect()
                        }
                    }
                    SupplicantState.DISCONNECTED -> if (supl_error == WifiManager.ERROR_AUTHENTICATING) {
                        WifiUtils.wifiLog("Authentication error...")
                        mWifiConnectionCallback.errorConnect(ConnectionErrorCode.AUTHENTICATION_ERROR_OCCURRED)
                    } else {
                        WifiUtils.wifiLog("Disconnected. Re-attempting to connect...")
                        reEnableNetworkIfPossible(mWifiManager, mScanResult)
                    }
                }
            }
        }
    }

    fun connectWith(
        result: ScanResult,
        password: String,
        connectivityManager: ConnectivityManager
    ): WifiConnectionReceiver {
        mScanResult = result
        ssid = mScanResult!!.SSID
        return this
    }

    fun connectWith(
        ssid: String,
        password: String,
        connectivityManager: ConnectivityManager
    ): WifiConnectionReceiver {
        this.ssid = ssid
        return this
    }

    companion object {
        fun isAlreadyConnected2(wifiManager: WifiManager?, ssid: String?): Boolean {
            if (ssid != null && wifiManager != null) {
                if (wifiManager.connectionInfo != null && wifiManager.connectionInfo.ssid != null && wifiManager.connectionInfo.ipAddress != 0 &&
                    ssid == wifiManager.connectionInfo.ssid
                ) {
                    WifiUtils.wifiLog("Already connected to: " + wifiManager.connectionInfo.ssid + "  BSSID: " + wifiManager.connectionInfo.bssid)
                    return true
                }
            }
            return false
        }
    }
}