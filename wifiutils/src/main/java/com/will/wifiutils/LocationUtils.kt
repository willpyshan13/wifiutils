package com.will.wifiutils

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import com.will.wifiutils.utils.Elvis
import android.util.Log

object LocationUtils {
    private val TAG = LocationUtils::class.java.simpleName
    const val GOOD_TO_GO = 1000
    const val NO_LOCATION_AVAILABLE = 1111
    const val LOCATION_DISABLED = 1112
    fun checkLocationAvailability(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packMan = context.packageManager
            if (packMan.hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
                if (!isLocationEnabled(context)) {
                    Log.d(TAG, "Location DISABLED")
                    return LOCATION_DISABLED
                }
            } else {
                Log.d(TAG, "NO GPS SENSOR")
                return NO_LOCATION_AVAILABLE
            }
        }
        Log.d(TAG, "GPS GOOD TO GO")
        return GOOD_TO_GO
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return Elvis.of(manager).next { locationManager: LocationManager ->
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.boolean ||
                Elvis.of(manager).next { locationManager: LocationManager ->
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }.boolean
    }
}