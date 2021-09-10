package com.will.wifiutils.wifiConnect

interface WifiConnectionCallback {
    fun successfulConnect()
    fun errorConnect(connectionErrorCode: ConnectionErrorCode)
}