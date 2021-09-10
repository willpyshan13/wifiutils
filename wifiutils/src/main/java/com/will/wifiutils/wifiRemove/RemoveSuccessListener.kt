package com.will.wifiutils.wifiRemove

interface RemoveSuccessListener {
    fun success()
    fun failed(errorCode: RemoveErrorCode)
}