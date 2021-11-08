package com.will.wifiutils

import com.will.wifiutils.ConnectorUtils.unregisterReceiver
import com.will.wifiutils.ConnectorUtils.registerReceiver
import com.will.wifiutils.ConnectorUtils.matchScanResultBssid
import com.will.wifiutils.ConnectorUtils.connectWps
import com.will.wifiutils.ConnectorUtils.matchScanResult
import com.will.wifiutils.ConnectorUtils.matchScanResultSsid
import com.will.wifiutils.ConnectorUtils.connectToWifi
import com.will.wifiutils.ConnectorUtils.connectToWifiHidden
import com.will.wifiutils.wifiConnect.DisconnectCallbackHolder.Companion.instance
import com.will.wifiutils.ConnectorUtils.reenableAllHotspots
import com.will.wifiutils.ConnectorUtils.disconnectFromWifi
import com.will.wifiutils.ConnectorUtils.removeWifi
import com.will.wifiutils.ConnectorUtils.cleanPreviousConfiguration
import com.will.wifiutils.ConnectorUtils.isAlreadyConnected
import android.annotation.SuppressLint
import android.content.Context
import com.will.wifiutils.WifiConnectorBuilder.WifiUtilsBuilder
import com.will.wifiutils.WifiConnectorBuilder.WifiSuccessListener
import com.will.wifiutils.WifiConnectorBuilder.WifiWpsSuccessListener
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import com.will.wifiutils.wifiState.WifiStateReceiver
import com.will.wifiutils.wifiScan.WifiScanReceiver
import com.will.wifiutils.wifiScan.ScanResultsListener
import com.will.wifiutils.wifiState.WifiStateListener
import com.will.wifiutils.wifiWps.ConnectionWpsListener
import com.will.wifiutils.wifiState.WifiStateCallback
import com.will.wifiutils.utils.Elvis
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.os.Build
import android.util.Log
import com.will.wifiutils.wifiScan.WifiScanCallback
import com.will.wifiutils.wifiDisconnect.DisconnectionSuccessListener
import com.will.wifiutils.wifiDisconnect.DisconnectionErrorCode
import com.will.wifiutils.wifiRemove.RemoveSuccessListener
import com.will.wifiutils.wifiRemove.RemoveErrorCode
import androidx.annotation.RequiresApi
import com.will.wifiutils.utils.VersionUtils.isAndroidQOrLater
import com.will.wifiutils.utils.VersionUtils.isLollipopOrLater
import com.will.wifiutils.wifiConnect.*
import java.util.ArrayList

@SuppressLint("MissingPermission")
class WifiUtils private constructor(private val mContext: Context) : WifiConnectorBuilder,
    WifiUtilsBuilder, WifiSuccessListener, WifiWpsSuccessListener {
    private val mWifiManager by lazy { mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val mConnectivityManager by lazy { mContext.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private var mWpsTimeoutMillis: Long = 30000
    private var mTimeoutMillis: Long = 30000
    private lateinit var mHandler: WeakHandler
    private lateinit var mWifiStateReceiver: WifiStateReceiver
    private lateinit var mWifiConnectionReceiver: WifiConnectionReceiver
    private lateinit var mTimeoutHandler: TimeoutHandler
    private lateinit var mWifiScanReceiver: WifiScanReceiver
    private var mSsid: String? = null
    private var type: String? = null
    private var mBssid: String? = null
    private var mPassword: String? = null
    private var mSingleScanResult: ScanResult? = null
    private var mScanResultsListener: ScanResultsListener? = null
    private var mConnectionScanResultsListener: ConnectionScanResultsListener? = null
    private var mConnectionSuccessListener: ConnectionSuccessListener? = null
    private var mWifiStateListener: WifiStateListener? = null
    private var mConnectionWpsListener: ConnectionWpsListener? = null
    private var mPatternMatch = false
    private val mWifiStateCallback: WifiStateCallback = object : WifiStateCallback {
        override fun onWifiEnabled() {
            wifiLog("WIFI ENABLED...")
            unregisterReceiver(mContext, mWifiStateReceiver)
            Elvis.of(mWifiStateListener)
                .ifPresent { stateListener: WifiStateListener? -> stateListener!!.isSuccess(true) }
            if (mScanResultsListener != null || mPassword != null) {
                wifiLog("START SCANNING....")
                if (mWifiManager.startScan()) {
                    registerReceiver(
                        mContext,
                        mWifiScanReceiver,
                        IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    )
                } else {
                    Elvis.of(mScanResultsListener)
                        .ifPresent { resultsListener: ScanResultsListener? ->
                            resultsListener!!.onScanResults(
                                ArrayList()
                            )
                        }
                    Elvis.of(mConnectionWpsListener)
                        .ifPresent { wpsListener: ConnectionWpsListener? ->
                            wpsListener!!.isSuccessful(false)
                        }
                    mWifiConnectionCallback.errorConnect(ConnectionErrorCode.COULD_NOT_SCAN)
                    wifiLog("ERROR COULDN'T SCAN")
                }
            }
        }
    }
    private val mWifiScanResultsCallback: WifiScanCallback = object : WifiScanCallback {
        override fun onScanResultsReady() {
            wifiLog("GOT SCAN RESULTS")
            unregisterReceiver(mContext, mWifiScanReceiver)
            val scanResultList = mWifiManager.scanResults
            Elvis.of(mScanResultsListener).ifPresent { resultsListener: ScanResultsListener? ->
                resultsListener!!.onScanResults(scanResultList)
            }
            Elvis.of(mConnectionScanResultsListener)
                .ifPresent { connectionResultsListener: ConnectionScanResultsListener? ->
                    mSingleScanResult =
                        connectionResultsListener!!.onConnectWithScanResult(scanResultList)
                }
            if (mConnectionWpsListener != null && mBssid != null && mPassword != null) {
                mSingleScanResult = matchScanResultBssid(mBssid!!, scanResultList)
                if (mSingleScanResult != null && isLollipopOrLater) {
                    connectWps(
                        mWifiManager,
                        mHandler,
                        mSingleScanResult!!,
                        mPassword!!,
                        mWpsTimeoutMillis,
                        mConnectionWpsListener!!
                    )
                } else {
                    if (mSingleScanResult == null) {
                        wifiLog("Couldn't find network. Possibly out of range")
                    }
                    mConnectionWpsListener!!.isSuccessful(false)
                }
                return
            }
            if (mSsid != null) {
                mSingleScanResult = if (mBssid != null) {
                    matchScanResult(mSsid!!, mBssid!!, scanResultList)
                } else {
                    matchScanResultSsid(mSsid!!, scanResultList, mPatternMatch)
                }
            }
            if (mSingleScanResult != null && mPassword != null) {
                if (connectToWifi(
                        mContext,
                        mWifiManager,
                        mConnectivityManager,
                        mHandler,
                        mSingleScanResult!!,
                        mPassword!!,
                        mWifiConnectionCallback,
                        mPatternMatch,
                        mSsid
                    )
                ) {
                    registerReceiver(
                        mContext, mWifiConnectionReceiver.connectWith(
                            mSingleScanResult!!, mPassword!!, mConnectivityManager!!
                        ),
                        IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
                    )
                    registerReceiver(
                        mContext, mWifiConnectionReceiver,
                        connectionFilter
                    )
                    mTimeoutHandler.startTimeout(mSingleScanResult, mTimeoutMillis)
                } else {
                    mWifiConnectionCallback.errorConnect(ConnectionErrorCode.COULD_NOT_CONNECT)
                }
            } else if(mSsid != null && mPassword != null){
                if (connectToWifiHidden(
                        mContext,
                        mWifiManager,
                        mConnectivityManager,
                        mHandler,
                        mSsid!!,
                        type,
                        mPassword!!,
                        mWifiConnectionCallback
                    )
                ) {
                    registerReceiver(
                        mContext,
                        mWifiConnectionReceiver.connectWith(
                            mSsid!!,
                            mPassword!!,
                            mConnectivityManager!!
                        ),
                        IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
                    )
                    registerReceiver(
                        mContext, mWifiConnectionReceiver,
                        connectionFilter
                    )
                    mTimeoutHandler.startTimeout(mSingleScanResult, mTimeoutMillis)
                } else {
                    mWifiConnectionCallback.errorConnect(ConnectionErrorCode.COULD_NOT_CONNECT)
                }
            }
        }
    }
    private val connectionFilter: IntentFilter
        private get() {
            val filter = IntentFilter()
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            return filter
        }
    private val mWifiConnectionCallback: WifiConnectionCallback = object : WifiConnectionCallback {
        override fun successfulConnect() {
            wifiLog("CONNECTED SUCCESSFULLY")
            unregisterReceiver(mContext, mWifiConnectionReceiver)
            mTimeoutHandler.stopTimeout()

            //reenableAllHotspots(mWifiManager);
            Elvis.of(mConnectionSuccessListener)
                .ifPresent { obj: ConnectionSuccessListener? -> obj!!.success() }
        }

        override fun errorConnect(connectionErrorCode: ConnectionErrorCode) {
            unregisterReceiver(mContext, mWifiConnectionReceiver)
            mTimeoutHandler.stopTimeout()
            if (isAndroidQOrLater) {
                instance!!.disconnect()
            }
            reenableAllHotspots(mWifiManager)
            //if (mSingleScanResult != null)
            //cleanPreviousConfiguration(mWifiManager, mSingleScanResult);
            Elvis.of(mConnectionSuccessListener)
                .ifPresent { successListener: ConnectionSuccessListener? ->
                    successListener!!.failed(connectionErrorCode)
                    wifiLog("DIDN'T CONNECT TO WIFI $connectionErrorCode")
                }
        }
    }

    override fun enableWifi(wifiStateListener: WifiStateListener?) {
        mWifiStateListener = wifiStateListener
        if (mWifiManager!!.isWifiEnabled) {
            mWifiStateCallback.onWifiEnabled()
        } else {
            if (mWifiManager.setWifiEnabled(true)) {
                registerReceiver(
                    mContext,
                    mWifiStateReceiver,
                    IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
                )
            } else {
                Elvis.of(wifiStateListener).ifPresent { stateListener: WifiStateListener? ->
                    stateListener!!.isSuccess(false)
                }
                Elvis.of(mScanResultsListener).ifPresent { resultsListener: ScanResultsListener? ->
                    resultsListener!!.onScanResults(
                        ArrayList()
                    )
                }
                Elvis.of(mConnectionWpsListener).ifPresent { wpsListener: ConnectionWpsListener? ->
                    wpsListener!!.isSuccessful(false)
                }
                mWifiConnectionCallback.errorConnect(ConnectionErrorCode.COULD_NOT_ENABLE_WIFI)
                wifiLog("COULDN'T ENABLE WIFI")
            }
        }
    }

    override fun enableWifi() {
        enableWifi(null)
    }

    override fun scanWifi(scanResultsListener: ScanResultsListener?): WifiConnectorBuilder {
        mScanResultsListener = scanResultsListener
        return this
    }

    @Deprecated("")
    override fun disconnectFrom(
        ssid: String,
        disconnectionSuccessListener: DisconnectionSuccessListener
    ) {
        disconnect(disconnectionSuccessListener)
    }

    override fun disconnect(disconnectionSuccessListener: DisconnectionSuccessListener) {
        if (mConnectivityManager == null) {
            disconnectionSuccessListener.failed(DisconnectionErrorCode.COULD_NOT_GET_CONNECTIVITY_MANAGER)
            return
        }
        if (mWifiManager == null) {
            disconnectionSuccessListener.failed(DisconnectionErrorCode.COULD_NOT_GET_WIFI_MANAGER)
            return
        }
        if (isAndroidQOrLater) {
            instance!!.unbindProcessFromNetwork()
            instance!!.disconnect()
            disconnectionSuccessListener.success()
        } else {
            if (disconnectFromWifi(mWifiManager)) {
                disconnectionSuccessListener.success()
            } else {
                disconnectionSuccessListener.failed(DisconnectionErrorCode.COULD_NOT_DISCONNECT)
            }
        }
    }

    override fun remove(ssid: String, removeSuccessListener: RemoveSuccessListener) {
        if (mConnectivityManager == null) {
            removeSuccessListener.failed(RemoveErrorCode.COULD_NOT_GET_CONNECTIVITY_MANAGER)
            return
        }
        if (mWifiManager == null) {
            removeSuccessListener.failed(RemoveErrorCode.COULD_NOT_GET_WIFI_MANAGER)
            return
        }
        if (isAndroidQOrLater) {
            instance!!.disconnect()
            removeSuccessListener.success()
        } else {
            if (removeWifi(mWifiManager, ssid)) {
                removeSuccessListener.success()
            } else {
                removeSuccessListener.failed(RemoveErrorCode.COULD_NOT_REMOVE)
            }
        }
    }

    override fun patternMatch(): WifiUtilsBuilder {
        mPatternMatch = true
        return this
    }

    override fun connectWith(ssid: String): WifiSuccessListener {
        mSsid = ssid
        mPassword = "" // FIXME: Cover no password case
        return this
    }

    override fun connectWith(ssid: String, password: String): WifiSuccessListener {
        mSsid = ssid
        mPassword = password
        return this
    }

    override fun connectWith(ssid: String, password: String, hide: Boolean): WifiSuccessListener {
        mSsid = ssid
        mPassword = password
        if (hide) {
            this.type = TypeEnum.PSK.name
        }
        return this
    }


    override fun connectWith(ssid: String, password: String, type: TypeEnum): WifiSuccessListener {
        mSsid = ssid
        mPassword = password
        this.type = type.name
        return this
    }

    override fun connectWith(ssid: String, bssid: String, password: String): WifiSuccessListener {
        mSsid = ssid
        mBssid = bssid
        mPassword = password
        return this
    }

    override fun connectWithScanResult(
        password: String,
        connectionScanResultsListener: ConnectionScanResultsListener?
    ): WifiSuccessListener {
        mConnectionScanResultsListener = connectionScanResultsListener
        mPassword = password
        return this
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun connectWithWps(bssid: String, password: String): WifiWpsSuccessListener {
        mBssid = bssid
        mPassword = password
        return this
    }

    override fun cancelAutoConnect() {
        unregisterReceiver(mContext, mWifiStateReceiver)
        unregisterReceiver(mContext, mWifiScanReceiver)
        unregisterReceiver(mContext, mWifiConnectionReceiver)
        Elvis.of(mSingleScanResult).ifPresent { scanResult: ScanResult? ->
            cleanPreviousConfiguration(
                mWifiManager,
                scanResult!!
            )
        }
        reenableAllHotspots(mWifiManager)
    }

    override fun isWifiConnected(ssid: String): Boolean {
        return isAlreadyConnected(mWifiManager, mConnectivityManager, ssid)
    }

    override val isWifiConnected: Boolean
        get() = isAlreadyConnected(mConnectivityManager)

    override fun setTimeout(timeOutMillis: Long): WifiSuccessListener {
        mTimeoutMillis = timeOutMillis
        return this
    }

    override fun setWpsTimeout(timeOutMillis: Long): WifiWpsSuccessListener {
        mWpsTimeoutMillis = timeOutMillis
        return this
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onConnectionWpsResult(successListener: ConnectionWpsListener?): WifiConnectorBuilder {
        mConnectionWpsListener = successListener
        return this
    }

    override fun onConnectionResult(successListener: ConnectionSuccessListener?): WifiConnectorBuilder {
        mConnectionSuccessListener = successListener
        return this
    }

    override fun start() {
        unregisterReceiver(mContext, mWifiStateReceiver)
        unregisterReceiver(mContext, mWifiScanReceiver)
        unregisterReceiver(mContext, mWifiConnectionReceiver)
        enableWifi(null)
    }

    override fun disableWifi() {
        if (mWifiManager.isWifiEnabled) {
            mWifiManager.isWifiEnabled = false
            unregisterReceiver(mContext, mWifiStateReceiver)
            unregisterReceiver(mContext, mWifiScanReceiver)
            unregisterReceiver(mContext, mWifiConnectionReceiver)
        }
        wifiLog("WiFi Disabled")
    }

    companion object {
        private val TAG = WifiUtils::class.java.simpleName
        private var mEnableLog = true
        private var customLogger: Logger? = null
        fun withContext(context: Context): WifiUtilsBuilder {
            return WifiUtils(context)
        }

        fun wifiLog(text: String?) {
            if (mEnableLog) {
                val logger = Elvis.of(customLogger).orElse(
                    object : Logger {
                        override fun log(priority: Int, tag: String?, message: String?) {
                            Log.println(
                                priority,
                                TAG,
                                message!!
                            )
                        }
                    })
                logger.log(Log.VERBOSE, TAG, text)
            }
        }

        fun enableLog(enabled: Boolean) {
            mEnableLog = enabled
        }

        /**
         * Send logs to a custom logging implementation. If none specified, defaults to logcat.
         *
         * @param logger custom logger
         */
        fun forwardLog(logger: Logger?) {
            customLogger = logger
        }
    }

    init {
        mWifiStateReceiver = WifiStateReceiver(mWifiStateCallback)
        mWifiScanReceiver = WifiScanReceiver(mWifiScanResultsCallback)
        mHandler = WeakHandler()
        mWifiConnectionReceiver = WifiConnectionReceiver(mWifiConnectionCallback, mWifiManager)
        mTimeoutHandler = TimeoutHandler(mWifiManager, mHandler, mWifiConnectionCallback)
    }
}