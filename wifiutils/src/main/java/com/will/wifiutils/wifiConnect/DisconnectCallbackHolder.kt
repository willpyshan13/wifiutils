package com.will.wifiutils.wifiConnect

import androidx.annotation.RequiresApi
import android.net.ConnectivityManager.NetworkCallback
import android.net.ConnectivityManager
import android.net.Network
import com.will.wifiutils.WifiUtils
import android.net.NetworkRequest
import android.os.Build
import com.will.wifiutils.wifiConnect.DisconnectCallbackHolder

/**
 * Singleton Class to keep references of [ConnectivityManager] and [ConnectivityManager.NetworkCallback]
 * so that we can easily bind/unbiding process from Network and disconnect on Android 10+.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DisconnectCallbackHolder private constructor() {
    private var mNetworkCallback: NetworkCallback? = null
    private var mConnectivityManager: ConnectivityManager? = null

    /**
     * Checks whether [DisconnectCallbackHolder.addNetworkCallback]
     * is called
     *
     * @return true if networkcallback is initialized false otherwise.
     */
    var isNetworkcallbackAdded = false
        private set

    /**
     * Checks whether [DisconnectCallbackHolder.bindProcessToNetwork]
     * is called
     *
     * @return true if bound false otherwise.
     */
    var isProcessBoundToNetwork = false
        private set

    /**
     * Keeps a reference of [ConnectivityManager] and [ConnectivityManager.NetworkCallback]
     * This method must be called before anything else.
     *
     * @param networkCallback     the networkcallback class to keep a reference of
     * @param connectivityManager the ConnectivityManager
     */
    fun addNetworkCallback(
        networkCallback: NetworkCallback,
        connectivityManager: ConnectivityManager
    ) {
        mNetworkCallback = networkCallback
        mConnectivityManager = connectivityManager
        isNetworkcallbackAdded = true
    }

    /**
     * Disconnects from Network and nullifies networkcallback meaning you will have to
     * call [DisconnectCallbackHolder.addNetworkCallback] again
     * next time you want to connect again.
     */
    fun disconnect() {
        if (mNetworkCallback != null && mConnectivityManager != null) {
            WifiUtils.wifiLog("Disconnecting on Android 10+")
            mConnectivityManager!!.unregisterNetworkCallback(mNetworkCallback!!)
            mNetworkCallback = null
            isNetworkcallbackAdded = false
        }
    }

    /**
     * See [ConnectivityManager.requestNetwork]
     *
     * @param networkRequest [NetworkRequest]
     */
    fun requestNetwork(networkRequest: NetworkRequest?) {
        if (mNetworkCallback != null && mConnectivityManager != null) {
            mConnectivityManager!!.requestNetwork(networkRequest!!, mNetworkCallback!!)
        } else {
            WifiUtils.wifiLog("NetworkCallback has not been added yet. Please call addNetworkCallback method first")
        }
    }

    /**
     * Unbinds the previously bound Network from the process.
     */
    fun unbindProcessFromNetwork() {
        if (mConnectivityManager != null) {
            mConnectivityManager!!.bindProcessToNetwork(null)
            isProcessBoundToNetwork = false
        } else {
            WifiUtils.wifiLog("ConnectivityManager is null. Did you call addNetworkCallback method first?")
        }
    }

    /**
     * binds so all api calls performed over this new network
     * if we don't bind, connection with the wifi network is immediately dropped
     */
    fun bindProcessToNetwork(network: Network) {
        if (mConnectivityManager != null) {
            mConnectivityManager!!.bindProcessToNetwork(network)
            isProcessBoundToNetwork = true
        } else {
            WifiUtils.wifiLog("ConnectivityManager is null. Did you call addNetworkCallback method first?")
        }
    }

    companion object {
        @Volatile
        private var sInstance: DisconnectCallbackHolder? = null

        /**
         * Gets a Singleton instance of DisconnectCallbackHolder.
         * This is a Lazy and Thread safe Singleton with Double-check locking
         *
         * @return DisconnectCallbackHolder Singleton instance
         */
        @JvmStatic
        val instance: DisconnectCallbackHolder?
            get() {
                if (sInstance == null) {
                    synchronized(DisconnectCallbackHolder::class.java) {
                        if (sInstance == null) {
                            sInstance = DisconnectCallbackHolder()
                        }
                    }
                }
                return sInstance
            }
    }
}