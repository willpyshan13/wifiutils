package com.will.wifiutils.wifiConnect

interface ConnectionSuccessListener {
    fun success()
    fun failed(errorCode: ConnectionErrorCode)
}