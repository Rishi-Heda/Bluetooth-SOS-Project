package com.example.myapplication.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class LocationHelper(context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Fetches the current location.
     * @SuppressLint is used because we strictly check permissions in HomeScreen before this is ever called.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Float, Float>? {
        return try {
            // Priority.PRIORITY_HIGH_ACCURACY forces the GPS chip to turn on for an exact read
            val location: Location? = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()

            if (location != null) {
                Pair(location.latitude.toFloat(), location.longitude.toFloat())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}