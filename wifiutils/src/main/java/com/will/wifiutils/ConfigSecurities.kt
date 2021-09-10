package com.will.wifiutils

import com.will.wifiutils.ConnectorUtils.isHexWepKey
import com.will.wifiutils.utils.SSIDUtils.convertToQuotedString
import androidx.annotation.RequiresPermission
import android.Manifest.permission
import android.net.wifi.WifiConfiguration
import android.annotation.TargetApi
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.os.Build
import java.util.ArrayList

internal object ConfigSecurities {
    const val SECURITY_NONE = "OPEN"
    const val SECURITY_WEP = "WEP"
    const val SECURITY_PSK = "PSK"
    const val SECURITY_EAP = "EAP"

    /**
     * Fill in the security fields of WifiConfiguration config.
     *
     * @param config   The object to fill.
     * @param security If is OPEN, password is ignored.
     * @param password Password of the network if security is not OPEN.
     */
    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun setupSecurity(config: WifiConfiguration, security: String, password: String) {
        config.allowedAuthAlgorithms.clear()
        config.allowedGroupCiphers.clear()
        config.allowedKeyManagement.clear()
        config.allowedPairwiseCiphers.clear()
        config.allowedProtocols.clear()
        WifiUtils.wifiLog("Setting up security $security")
        when (security) {
            SECURITY_NONE -> {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            }
            SECURITY_WEP -> {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                if (isHexWepKey(password)) {
                    config.wepKeys[0] = password
                } else {
                    config.wepKeys[0] = convertToQuotedString(password)
                }
            }
            SECURITY_PSK -> {
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                if (password.matches(Regex("[0-9A-Fa-f]{64}"))) {
                    config.preSharedKey = password
                } else {
                    config.preSharedKey = convertToQuotedString(password)
                }
            }
            SECURITY_EAP -> {
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
                config.preSharedKey = convertToQuotedString(password)
            }
            else -> WifiUtils.wifiLog("Invalid security type: $security")
        }
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun setupSecurityHidden(config: WifiConfiguration, security: String, password: String) {
        config.hiddenSSID = true
        setupSecurity(config, security, password)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun setupWifiNetworkSpecifierSecurities(
        wifiNetworkSpecifierBuilder: WifiNetworkSpecifier.Builder,
        security: String,
        password: String
    ) {
        WifiUtils.wifiLog("Setting up WifiNetworkSpecifier.Builder $security")
        when (security) {
            SECURITY_NONE -> {
            }
            SECURITY_WEP -> {
            }
            SECURITY_PSK, SECURITY_EAP -> wifiNetworkSpecifierBuilder.setWpa2Passphrase(password)
            else -> WifiUtils.wifiLog("Invalid security type: $security")
        }
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun getWifiConfiguration(
        wifiMgr: WifiManager,
        configToFind: WifiConfiguration
    ): WifiConfiguration? {
        val ssid = configToFind.SSID
        if (ssid == null || ssid.isEmpty()) {
            return null
        }
        val bssid = if (configToFind.BSSID != null) configToFind.BSSID else ""
        val security = getSecurity(configToFind)
        val configurations = wifiMgr.configuredNetworks
        if (configurations == null) {
            WifiUtils.wifiLog("NULL configs")
            return null
        }
        for (config in configurations) {
            if (bssid == config.BSSID || ssid == config.SSID) {
                val configSecurity = getSecurity(config)
                if (security == configSecurity) {
                    return config
                }
            }
        }
        WifiUtils.wifiLog("Couldn't find $ssid")
        return null
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun getWifiConfiguration(wifiManager: WifiManager, ssid: String): WifiConfiguration? {
        val configuredNetworks = wifiManager.configuredNetworks
        val findSSID = '"'.toString() + ssid + '"'
        for (wifiConfiguration in configuredNetworks) {
            if (wifiConfiguration.SSID == findSSID) {
                return wifiConfiguration
            }
        }
        return null
    }

    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
    fun getWifiConfiguration(wifiManager: WifiManager, scanResult: ScanResult): WifiConfiguration? {
        if (scanResult.BSSID == null || scanResult.SSID == null || scanResult.SSID.isEmpty() || scanResult.BSSID.isEmpty()) {
            return null
        }
        val ssid = convertToQuotedString(scanResult.SSID)
        val bssid = scanResult.BSSID
        val security = getSecurity(scanResult)
        val configurations = wifiManager.configuredNetworks ?: return null
        for (config in configurations) {
            if (bssid == config.BSSID || ssid == config.SSID) {
                val configSecurity = getSecurity(config)
                if (security == configSecurity) {
                    return config
                }
            }
        }
        return null
    }

    fun getSecurity(config: WifiConfiguration): String {
        var security = SECURITY_NONE
        val securities: MutableCollection<String> = ArrayList()
        if (config.allowedKeyManagement[WifiConfiguration.KeyMgmt.NONE]) {
            // If we never set group ciphers, wpa_supplicant puts all of them.
            // For open, we don't set group ciphers.
            // For WEP, we specifically only set WEP40 and WEP104, so CCMP
            // and TKIP should not be there.
            security = if (config.wepKeys[0] != null) {
                SECURITY_WEP
            } else {
                SECURITY_NONE
            }
            securities.add(security)
        }
        if (config.allowedKeyManagement[WifiConfiguration.KeyMgmt.WPA_EAP] ||
            config.allowedKeyManagement[WifiConfiguration.KeyMgmt.IEEE8021X]
        ) {
            security = SECURITY_EAP
            securities.add(security)
        }
        if (config.allowedKeyManagement[WifiConfiguration.KeyMgmt.WPA_PSK]) {
            security = SECURITY_PSK
            securities.add(security)
        }
        WifiUtils.wifiLog("Got Security Via WifiConfiguration $securities")
        return security
    }

    fun getSecurity(result: ScanResult): String {
        var security = SECURITY_NONE
        if (result.capabilities.contains(SECURITY_WEP)) {
            security = SECURITY_WEP
        }
        if (result.capabilities.contains(SECURITY_PSK)) {
            security = SECURITY_PSK
        }
        if (result.capabilities.contains(SECURITY_EAP)) {
            security = SECURITY_EAP
        }
        WifiUtils.wifiLog("ScanResult capabilities " + result.capabilities)
        WifiUtils.wifiLog("Got security via ScanResult $security")
        return security
    }

    fun getSecurity(result: String): String {
        var security = SECURITY_NONE
        if (result.contains(SECURITY_WEP)) {
            security = SECURITY_WEP
        }
        if (result.contains(SECURITY_PSK)) {
            security = SECURITY_PSK
        }
        if (result.contains(SECURITY_EAP)) {
            security = SECURITY_EAP
        }
        return security
    }

    /**
     * @return The security of a given [ScanResult].
     */
    fun getSecurityPrettyPlusWps(scanResult: ScanResult?): String {
        if (scanResult == null) {
            return ""
        }
        var result = getSecurity(scanResult)
        if (scanResult.capabilities.contains("WPS")) {
            result = "$result, WPS"
        }
        return result
    }
}