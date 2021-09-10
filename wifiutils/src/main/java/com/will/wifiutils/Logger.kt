package com.will.wifiutils

interface Logger {
    fun log(priority: Int, tag: String?, message: String?)
}