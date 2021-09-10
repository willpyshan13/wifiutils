package com.will.wifiutils.wifiDisconnect

interface DisconnectionSuccessListener {
    fun success()
    fun failed(errorCode: DisconnectionErrorCode)
}