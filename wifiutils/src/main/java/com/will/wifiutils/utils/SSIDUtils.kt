package com.will.wifiutils.utils

import android.text.TextUtils

object SSIDUtils {
    @JvmStatic
    fun convertToQuotedString(ssid: String): String {
        if (TextUtils.isEmpty(ssid)) {
            return ""
        }
        val lastPos = ssid.length - 1
        return if (lastPos < 0 || ssid[0] == '"' && ssid[lastPos] == '"') {
            ssid
        } else "\"" + ssid + "\""
    }
}