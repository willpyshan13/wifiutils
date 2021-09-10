package com.will.wifiutils

import com.will.wifiutils.utils.VersionUtils.isJellyBeanOrLater
import com.will.wifiutils.utils.SSIDUtils.convertToQuotedString
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import com.will.wifiutils.utils.Elvis
import android.content.ContentResolver
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import android.Manifest.permission
import android.content.Context
import android.net.*
import com.will.wifiutils.wifiConnect.WifiConnectionCallback
import com.will.wifiutils.wifiConnect.DisconnectCallbackHolder
import android.net.ConnectivityManager.NetworkCallback
import com.will.wifiutils.wifiConnect.ConnectionErrorCode
import android.net.wifi.*
import com.will.wifiutils.wifiWps.ConnectionWpsListener
import android.net.wifi.WifiManager.WpsCallback
import android.os.Build
import android.os.PatternMatcher
import android.provider.Settings
import com.will.wifiutils.utils.VersionUtils.isAndroidQOrLater
import com.will.wifiutils.utils.VersionUtils.isLollipopOrLater
import com.will.wifiutils.utils.VersionUtils.isMarshmallowOrLater
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.*

@SuppressLint("MissingPermission")
object ConnectorUtils {
    private const val MAX_PRIORITY = 99999
    fun isAlreadyConnected(wifiManager: WifiManager?, bssid: String?): Boolean {
        WifiUtils.wifiLog("ConnectorUtils connected to: bssid=" + bssid + "  BSSID: " + wifiManager!!.connectionInfo.bssid)
        if (bssid != null) {
            if (wifiManager.connectionInfo != null && wifiManager.connectionInfo.bssid != null && wifiManager.connectionInfo.ipAddress != 0 &&
                bssid == wifiManager.connectionInfo.bssid
            ) {
                WifiUtils.wifiLog("Already connected to: " + wifiManager.connectionInfo.ssid + "  BSSID: " + wifiManager.connectionInfo.bssid)
                return true
            }
        }
        return false
    }

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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun isConnectedToNetworkLollipop(connectivityManager: ConnectivityManager?): Boolean {

//        final ConnectivityManager connMgr =
//                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false
        var isWifiConn = false
        for (network in connectivityManager.allNetworks) {
            val networkInfo = connectivityManager.getNetworkInfo(network)
            if (networkInfo != null && ConnectivityManager.TYPE_WIFI == networkInfo.type) {
                isWifiConn = isWifiConn or networkInfo.isConnected
            }
        }
        return isWifiConn
    }

    fun isAlreadyConnected(connectivityManager: ConnectivityManager?): Boolean {
        return if (isLollipopOrLater) {
            isConnectedToNetworkLollipop(connectivityManager)
        } else Elvis.of(connectivityManager)
            .next { manager: ConnectivityManager? -> manager!!.getNetworkInfo(ConnectivityManager.TYPE_WIFI) }
            .next { obj: NetworkInfo? -> obj!!.state }
            .next { state: NetworkInfo.State -> state == NetworkInfo.State.CONNECTED }.boolean
    }

    @JvmStatic
    fun isAlreadyConnected(
        wifiManager: WifiManager?,
        connectivityManager: ConnectivityManager?,
        ssid: String?
    ): Boolean {
        var result = isAlreadyConnected(connectivityManager)
        if (result) {
            if (ssid != null && wifiManager != null) {
                var quotedSsid: String = ssid
                if (isJellyBeanOrLater) {
                    quotedSsid = convertToQuotedString(ssid)
                }
                val wifiInfo = wifiManager.connectionInfo
                val tempSSID = wifiInfo.ssid
                result = tempSSID != null && tempSSID == quotedSsid
            }
        }
        return result
    }

    private fun checkForExcessOpenNetworkAndSave(
        resolver: ContentResolver,
        wifiMgr: WifiManager
    ): Boolean {
        val configurations = wifiMgr.configuredNetworks
        sortByPriority(configurations)
        var modified = false
        var tempCount = 0
        val numOpenNetworksKept = if (isJellyBeanOrLater) Settings.Secure.getInt(
            resolver,
            Settings.Global.WIFI_NUM_OPEN_NETWORKS_KEPT,
            10
        ) else Settings.Secure.getInt(resolver, Settings.Secure.WIFI_NUM_OPEN_NETWORKS_KEPT, 10)
        for (i in configurations.indices.reversed()) {
            val config = configurations[i]
            if (ConfigSecurities.SECURITY_NONE == ConfigSecurities.getSecurity(config)) {
                tempCount++
                if (tempCount >= numOpenNetworksKept) {
                    modified = true
                    wifiMgr.removeNetwork(config.networkId)
                }
            }
        }
        return !modified || wifiMgr.saveConfiguration()
    }

    private fun getMaxPriority(wifiManager: WifiManager?): Int {
        if (wifiManager == null) {
            return 0
        }
        val configurations = wifiManager.configuredNetworks
        var pri = 0
        for (config in configurations) {
            if (config.priority > pri) {
                pri = config.priority
            }
        }
        return pri
    }

    private fun shiftPriorityAndSave(wifiMgr: WifiManager?): Int {
        if (wifiMgr == null) {
            return 0
        }
        val configurations = wifiMgr.configuredNetworks
        sortByPriority(configurations)
        val size = configurations.size
        for (i in 0 until size) {
            val config = configurations[i]
            config.priority = i
            wifiMgr.updateNetwork(config)
        }
        wifiMgr.saveConfiguration()
        return size
    }

    private fun trimQuotes(str: String?): String? {
        return if (str != null && !str.isEmpty()) {
            str.replace("^\"*".toRegex(), "").replace("\"*$".toRegex(), "")
        } else str
    }

    fun getPowerPercentage(power: Int): Int {
        val i: Int
        i = if (power <= -93) {
            0
        } else if (-25 <= power && power <= 0) {
            100
        } else {
            125 + power
        }
        return i
    }

    @JvmStatic
    fun isHexWepKey(wepKey: String?): Boolean {
        val passwordLen = wepKey?.length ?: 0
        return (passwordLen == 10 || passwordLen == 26 || passwordLen == 58) && wepKey!!.matches(
            Regex("[0-9A-Fa-f]*"))
    }

    private fun sortByPriority(configurations: List<WifiConfiguration>) {
        Collections.sort(configurations) { o1: WifiConfiguration, o2: WifiConfiguration -> o1.priority - o2.priority }
    }

    fun frequencyToChannel(freq: Int): Int {
        return if (2412 <= freq && freq <= 2484) {
            (freq - 2412) / 5 + 1
        } else if (5170 <= freq && freq <= 5825) {
            (freq - 5170) / 5 + 34
        } else {
            -1
        }
    }

    @JvmStatic
    fun registerReceiver(context: Context, receiver: BroadcastReceiver?, filter: IntentFilter) {
        if (receiver != null) {
            try {
                context.registerReceiver(receiver, filter)
            } catch (ignored: Exception) {
                ignored.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver?) {
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    @JvmStatic
    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun connectToWifi(
        context: Context,
        wifiManager: WifiManager?,
        connectivityManager: ConnectivityManager?,
        handler: WeakHandler,
        scanResult: ScanResult,
        password: String,
        wifiConnectionCallback: WifiConnectionCallback,
        patternMatch: Boolean,
        ssid: String?
    ): Boolean {
        if (wifiManager == null || connectivityManager == null) {
            return false
        }
        return if (isAndroidQOrLater) {
            connectAndroidQ(
                wifiManager,
                connectivityManager,
                handler,
                wifiConnectionCallback,
                scanResult,
                password,
                patternMatch,
                ssid
            )
        } else connectPreAndroidQ(
            context,
            wifiManager,
            scanResult,
            password
        )
    }

    @JvmStatic
    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun connectToWifiHidden(
        context: Context,
        wifiManager: WifiManager?,
        connectivityManager: ConnectivityManager?,
        handler: WeakHandler,  //                                       @NonNull final ScanResult scanResult,
        ssid: String,
        type: String?,
        password: String,
        wifiConnectionCallback: WifiConnectionCallback
    ): Boolean {
        if (wifiManager == null || connectivityManager == null || type == null) {
            return false
        }
        return if (isAndroidQOrLater) {
            connectAndroidQHidden(
                wifiManager,
                connectivityManager,
                handler,
                wifiConnectionCallback,
                ssid,
                type,
                password
            )
        } else connectPreAndroidQHidden(
            context,
            wifiManager,
            ssid,
            type,
            password
        )
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    private fun connectPreAndroidQ(
        context: Context,
        wifiManager: WifiManager?,
        scanResult: ScanResult,
        password: String
    ): Boolean {
        if (wifiManager == null) {
            return false
        }
        var config = ConfigSecurities.getWifiConfiguration(wifiManager, scanResult)
        if (config != null && password.isEmpty()) {
            WifiUtils.wifiLog("PASSWORD WAS EMPTY. TRYING TO CONNECT TO EXISTING NETWORK CONFIGURATION")
            return connectToConfiguredNetwork(wifiManager, config, true)
        }
        if (!cleanPreviousConfiguration(wifiManager, config)) {
            WifiUtils.wifiLog("COULDN'T REMOVE PREVIOUS CONFIG, CONNECTING TO EXISTING ONE")
            return connectToConfiguredNetwork(wifiManager, config, true)
        }
        val security = ConfigSecurities.getSecurity(scanResult)
        if (ConfigSecurities.SECURITY_NONE == security) {
            checkForExcessOpenNetworkAndSave(context.contentResolver, wifiManager)
        }
        config = WifiConfiguration()
        config.SSID = convertToQuotedString(scanResult.SSID)
        config.BSSID = scanResult.BSSID
        ConfigSecurities.setupSecurity(config, security, password)
        val id = wifiManager.addNetwork(config)
        WifiUtils.wifiLog("Network ID: $id")
        if (id == -1) {
            return false
        }
        if (!wifiManager.saveConfiguration()) {
            WifiUtils.wifiLog("Couldn't save wifi config")
            return false
        }
        // We have to retrieve the WifiConfiguration after save
        config = ConfigSecurities.getWifiConfiguration(wifiManager, config)
        if (config == null) {
            WifiUtils.wifiLog("Error getting wifi config after save. (config == null)")
            return false
        }
        return connectToConfiguredNetwork(wifiManager, config, true)
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    private fun connectPreAndroidQHidden(
        context: Context,
        wifiManager: WifiManager?,
        ssid: String,
        type: String,
        password: String
    ): Boolean {
        if (wifiManager == null) {
            return false
        }
        //
        var config: WifiConfiguration?
        val security = ConfigSecurities.getSecurity(type)
        if (ConfigSecurities.SECURITY_NONE == security) {
            checkForExcessOpenNetworkAndSave(context.contentResolver, wifiManager)
        }
        config = WifiConfiguration()
        config.SSID = convertToQuotedString(ssid)
        ConfigSecurities.setupSecurityHidden(config, security, password)
        val id = wifiManager.addNetwork(config)
        WifiUtils.wifiLog("Hidden-Network ID: $id")
        if (id == -1) {
            return false
        }
        if (!wifiManager.saveConfiguration()) {
            WifiUtils.wifiLog("Couldn't save wifi config")
            return false
        }
        // We have to retrieve the WifiConfiguration after save
        config = ConfigSecurities.getWifiConfiguration(wifiManager, config)
        if (config == null) {
            WifiUtils.wifiLog("Error getting wifi config after save. (config == null)")
            return false
        }
        return connectToConfiguredNetwork(wifiManager, config, true)
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    private fun connectToConfiguredNetwork(
        wifiManager: WifiManager?,
        config: WifiConfiguration?,
        reassociate: Boolean
    ): Boolean {
        var config = config
        if (config == null || wifiManager == null) {
            return false
        }
        if (isMarshmallowOrLater) {
            return disableAllButOne(
                wifiManager,
                config
            ) && if (reassociate) wifiManager.reassociate() else wifiManager.reconnect()
        }

        // Make it the highest priority.
        var newPri = getMaxPriority(wifiManager) + 1
        if (newPri > MAX_PRIORITY) {
            newPri = shiftPriorityAndSave(wifiManager)
            config = ConfigSecurities.getWifiConfiguration(wifiManager, config)
            if (config == null) {
                return false
            }
        }

        // Set highest priority to this configured network
        config.priority = newPri
        val networkId = wifiManager.updateNetwork(config)
        if (networkId == -1) {
            return false
        }

        // Do not disable others
        if (!wifiManager.enableNetwork(networkId, false)) {
            return false
        }
        if (!wifiManager.saveConfiguration()) {
            return false
        }

        // We have to retrieve the WifiConfiguration after save.
        config = ConfigSecurities.getWifiConfiguration(wifiManager, config)
        return config != null && disableAllButOne(
            wifiManager,
            config
        ) && if (reassociate) wifiManager.reassociate() else wifiManager.reconnect()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectAndroidQ(
        wifiManager: WifiManager?,
        connectivityManager: ConnectivityManager?,
        handler: WeakHandler,
        wifiConnectionCallback: WifiConnectionCallback,
        scanResult: ScanResult,
        password: String,
        patternMatch: Boolean,
        ssid: String?
    ): Boolean {
        if (connectivityManager == null) {
            return false
        }
        val wifiNetworkSpecifierBuilder = WifiNetworkSpecifier.Builder()
        if (patternMatch) {
            wifiNetworkSpecifierBuilder.setSsidPattern(
                PatternMatcher(
                    ssid ?: scanResult.SSID,
                    PatternMatcher.PATTERN_PREFIX
                )
            )
        } else {
            wifiNetworkSpecifierBuilder
                .setSsid(scanResult.SSID)
                .setBssid(MacAddress.fromString(scanResult.BSSID))
        }
        val security = ConfigSecurities.getSecurity(scanResult)
        ConfigSecurities.setupWifiNetworkSpecifierSecurities(
            wifiNetworkSpecifierBuilder,
            security,
            password
        )
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI) //                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(wifiNetworkSpecifierBuilder.build()) //                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        // cleanup previous connections just in case
        DisconnectCallbackHolder.instance?.disconnect()
        val networkCallback: NetworkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                WifiUtils.wifiLog("AndroidQ+ connected to wifi ")

                // TODO: should this actually be in the success listener on WifiUtils?
                // We could pass the networkrequest maybe?

                // bind so all api calls are performed over this new network
                // if we don't bind, connection with the wifi network is immediately dropped
                DisconnectCallbackHolder.instance?.bindProcessToNetwork(network)
                connectivityManager.networkPreference =
                    ConnectivityManager.DEFAULT_NETWORK_PREFERENCE

                // On some Android 10 devices, connection is made and than immediately lost due to a firmware bug,
                // read more here: https://github.com/will/WifiUtils/issues/63.
                handler.postDelayed({
                    if (isAlreadyConnected(
                            wifiManager,
                            Elvis.of(scanResult)
                                .next { scanResult1: ScanResult -> scanResult1.BSSID }
                                .get())
                    ) {
                        wifiConnectionCallback.successfulConnect()
                    } else {
                        wifiConnectionCallback.errorConnect(ConnectionErrorCode.ANDROID_10_IMMEDIATELY_DROPPED_CONNECTION)
                    }
                }, 500)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                WifiUtils.wifiLog("AndroidQ+ could not connect to wifi")
                wifiConnectionCallback.errorConnect(ConnectionErrorCode.USER_CANCELLED)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                WifiUtils.wifiLog("onLost")

                // cancel connecting if needed, this prevents 'request loops' on some oneplus/redmi phones
                DisconnectCallbackHolder.instance?.unbindProcessFromNetwork()
                DisconnectCallbackHolder.instance?.disconnect()
            }
        }
        DisconnectCallbackHolder.instance?.addNetworkCallback(networkCallback, connectivityManager)
        WifiUtils.wifiLog("connecting with Android 10")
        DisconnectCallbackHolder.instance?.requestNetwork(networkRequest)
        return true
    }

    // FIXME: we should use WifiNetworkSuggestion api to connect WLAN on Android 10, I`ll fix it soon.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectAndroidQHidden(
        wifiManager: WifiManager?,
        connectivityManager: ConnectivityManager?,
        handler: WeakHandler,
        wifiConnectionCallback: WifiConnectionCallback,
        ssid: String,
        type: String,
        password: String
    ): Boolean {
        if (connectivityManager == null) {
            return false
        }
        val wifiNetworkSpecifierBuilder = WifiNetworkSpecifier.Builder()
            .setIsHiddenSsid(true)
            .setSsid(ssid)
        val security = ConfigSecurities.getSecurity(type)
        ConfigSecurities.setupWifiNetworkSpecifierSecurities(
            wifiNetworkSpecifierBuilder,
            security,
            password
        )
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI) //                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .setNetworkSpecifier(wifiNetworkSpecifierBuilder.build())
            .build()

//        // cleanup previous connections just in case
        DisconnectCallbackHolder.instance?.disconnect()
        val networkCallback: NetworkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                WifiUtils.wifiLog("AndroidQ+ connected to wifi ")
                // TODO: should this actually be in the success listener on WifiUtils?
                // We could pass the networkrequest maybe?

                // bind so all api calls are performed over this new network
                // if we don't bind, connection with the wifi network is immediately dropped
                DisconnectCallbackHolder.instance?.bindProcessToNetwork(network)
                connectivityManager.networkPreference =
                    ConnectivityManager.DEFAULT_NETWORK_PREFERENCE

                // On some Android 10 devices, connection is made and than immediately lost due to a firmware bug,
                // read more here: https://github.com/will/WifiUtils/issues/63.
                handler.postDelayed({
                    if (isAlreadyConnected(wifiManager, ssid)) {
                        wifiConnectionCallback.successfulConnect()
                    } else {
                        wifiConnectionCallback.errorConnect(ConnectionErrorCode.ANDROID_10_IMMEDIATELY_DROPPED_CONNECTION)
                    }
                }, 500)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                WifiUtils.wifiLog("AndroidQ+ could not connect to wifi")
                wifiConnectionCallback.errorConnect(ConnectionErrorCode.USER_CANCELLED)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                WifiUtils.wifiLog("onLost")

                // cancel connecting if needed, this prevents 'request loops' on some oneplus/redmi phones
                DisconnectCallbackHolder.instance?.unbindProcessFromNetwork()
                DisconnectCallbackHolder.instance?.disconnect()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                WifiUtils.wifiLog("onLost")
            }
        }
        DisconnectCallbackHolder.instance?.addNetworkCallback(networkCallback, connectivityManager)
        WifiUtils.wifiLog("connecting with Android 10")
        DisconnectCallbackHolder.instance?.requestNetwork(networkRequest)
        return true
    }

    private fun disableAllButOne(wifiManager: WifiManager?, config: WifiConfiguration?): Boolean {
        if (wifiManager == null) {
            return false
        }
        val configurations = wifiManager.configuredNetworks
        if (configurations == null || config == null || configurations.isEmpty()) {
            return false
        }
        var result = false
        for (wifiConfig in configurations) {
            if (wifiConfig == null) {
                continue
            }
            if (wifiConfig.networkId == config.networkId) {
                result = wifiManager.enableNetwork(wifiConfig.networkId, true)
            } else {
                wifiManager.disableNetwork(wifiConfig.networkId)
            }
        }
        WifiUtils.wifiLog("disableAllButOne $result")
        return result
    }

    private fun disableAllButOne(wifiManager: WifiManager?, scanResult: ScanResult?): Boolean {
        if (wifiManager == null) {
            return false
        }
        val configurations = wifiManager.configuredNetworks
        if (configurations == null || scanResult == null || configurations.isEmpty()) {
            return false
        }
        var result = false
        for (wifiConfig in configurations) {
            if (wifiConfig == null) {
                continue
            }
            if (scanResult.BSSID == wifiConfig.BSSID && scanResult.SSID == trimQuotes(wifiConfig.SSID)) {
                result = wifiManager.enableNetwork(wifiConfig.networkId, true)
            } else {
                wifiManager.disableNetwork(wifiConfig.networkId)
            }
        }
        return result
    }

    @JvmStatic
    fun reEnableNetworkIfPossible(wifiManager: WifiManager?, scanResult: ScanResult?): Boolean {
        if (wifiManager == null) {
            return false
        }
        val configurations = wifiManager.configuredNetworks
        if (configurations == null || scanResult == null || configurations.isEmpty()) {
            return false
        }
        var result = false
        for (wifiConfig in configurations) if (scanResult.BSSID == wifiConfig.BSSID && scanResult.SSID == trimQuotes(
                wifiConfig.SSID
            )
        ) {
            result = wifiManager.enableNetwork(wifiConfig.networkId, true)
            break
        }
        WifiUtils.wifiLog("reEnableNetworkIfPossible $result")
        return result
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun connectWps(
        wifiManager: WifiManager?,
        handler: WeakHandler,
        scanResult: ScanResult,
        pin: String,
        timeOutMillis: Long,
        connectionWpsListener: ConnectionWpsListener
    ) {
        if (wifiManager == null) {
            connectionWpsListener.isSuccessful(false)
            return
        }
        val wpsInfo = WpsInfo()
        val handlerTimeoutRunnable: Runnable = object : Runnable {
            override fun run() {
                wifiManager.cancelWps(null)
                WifiUtils.wifiLog("Connection with WPS has timed out")
                cleanPreviousConfiguration(wifiManager, scanResult)
                connectionWpsListener.isSuccessful(false)
                handler.removeCallbacks(this)
            }
        }
        val wpsCallback: WpsCallback = object : WpsCallback() {
            override fun onStarted(pin: String) {}
            override fun onSucceeded() {
                handler.removeCallbacks(handlerTimeoutRunnable)
                WifiUtils.wifiLog("CONNECTED With WPS successfully")
                connectionWpsListener.isSuccessful(true)
            }

            override fun onFailed(reason: Int) {
                handler.removeCallbacks(handlerTimeoutRunnable)
                val reasonStr: String
                reasonStr = when (reason) {
                    3 -> "WPS_OVERLAP_ERROR"
                    4 -> "WPS_WEP_PROHIBITED"
                    5 -> "WPS_TKIP_ONLY_PROHIBITED"
                    6 -> "WPS_AUTH_FAILURE"
                    7 -> "WPS_TIMED_OUT"
                    else -> reason.toString()
                }
                WifiUtils.wifiLog("FAILED to connect with WPS. Reason: $reasonStr")
                cleanPreviousConfiguration(wifiManager, scanResult)
                reenableAllHotspots(wifiManager)
                connectionWpsListener.isSuccessful(false)
            }
        }
        WifiUtils.wifiLog("Connecting with WPS...")
        wpsInfo.setup = WpsInfo.KEYPAD
        wpsInfo.BSSID = scanResult.BSSID
        wpsInfo.pin = pin
        wifiManager.cancelWps(null)
        if (!cleanPreviousConfiguration(wifiManager, scanResult)) {
            disableAllButOne(wifiManager, scanResult)
        }
        handler.postDelayed(handlerTimeoutRunnable, timeOutMillis)
        wifiManager.startWps(wpsInfo, wpsCallback)
    }

    @JvmStatic
    @RequiresPermission(permission.ACCESS_WIFI_STATE)
    fun disconnectFromWifi(wifiManager: WifiManager): Boolean {
        return wifiManager.disconnect()
    }

    @JvmStatic
    @RequiresPermission(permission.ACCESS_WIFI_STATE)
    fun removeWifi(wifiManager: WifiManager, ssid: String): Boolean {
        val wifiConfiguration = ConfigSecurities.getWifiConfiguration(wifiManager, ssid)
        return cleanPreviousConfiguration(wifiManager, wifiConfiguration)
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun cleanPreviousConfiguration(wifiManager: WifiManager?, scanResult: ScanResult): Boolean {
        if (wifiManager == null) {
            return false
        }
        //On Android 6.0 (API level 23) and above if my app did not create the configuration in the first place, it can not remove it either.
        val config = ConfigSecurities.getWifiConfiguration(wifiManager, scanResult)
        WifiUtils.wifiLog("Attempting to remove previous network config...")
        if (config == null) {
            return true
        }
        if (wifiManager.removeNetwork(config.networkId)) {
            wifiManager.saveConfiguration()
            return true
        }
        return false
    }

    @JvmStatic
    fun cleanPreviousConfiguration(wifiManager: WifiManager?, config: WifiConfiguration?): Boolean {
        //On Android 6.0 (API level 23) and above if my app did not create the configuration in the first place, it can not remove it either.
        if (wifiManager == null) {
            return false
        }
        WifiUtils.wifiLog("Attempting to remove previous network config...")
        if (config == null) {
            return true
        }
        if (wifiManager.removeNetwork(config.networkId)) {
            wifiManager.saveConfiguration()
            return true
        }
        return false
    }

    @JvmStatic
    fun reenableAllHotspots(wifi: WifiManager?) {
        if (wifi == null) {
            return
        }
        val configurations = wifi.configuredNetworks
        if (configurations != null && !configurations.isEmpty()) {
            for (config in configurations) {
                wifi.enableNetwork(config.networkId, false)
            }
        }
    }

    @JvmStatic
    fun matchScanResultSsid(
        ssid: String,
        results: Iterable<ScanResult>,
        mPatternMatch: Boolean
    ): ScanResult? {
        for (result in results) {
            if (if (mPatternMatch) result.SSID.startsWith(ssid) else result.SSID == ssid) {
                return result
            }
        }
        return null
    }

    @JvmStatic
    fun matchScanResult(ssid: String, bssid: String, results: Iterable<ScanResult>): ScanResult? {
        for (result in results) {
            if (result.SSID == ssid && result.BSSID == bssid) {
                return result
            }
        }
        return null
    }

    @JvmStatic
    fun matchScanResultBssid(bssid: String, results: Iterable<ScanResult>): ScanResult? {
        for (result in results) {
            if (result.BSSID == bssid) {
                return result
            }
        }
        return null
    }
}