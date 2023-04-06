package com.will.wifiutils.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

object VersionUtils {
    val isJellyBeanOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
    val isLollipopOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    val isMarshmallowOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    val isAndroidQOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @SuppressLint("AnnotateVersionCheck")
    fun is29AndAbove() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q


    @RequiresApi(Build.VERSION_CODES.Q)
    fun getPanelIntent()  =  Intent(Settings.Panel.ACTION_WIFI)
}