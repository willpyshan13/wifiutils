package com.will.wifiutils.utils

import android.os.Build

object VersionUtils {
    val isJellyBeanOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
    val isLollipopOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    val isMarshmallowOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    val isAndroidQOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}