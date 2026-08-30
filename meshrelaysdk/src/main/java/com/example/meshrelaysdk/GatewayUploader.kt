package com.example.meshrelaysdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.SosDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GatewayUploader(
    context: Context,
    private val dao: SosDao
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val uploadScope = CoroutineScope(Dispatchers.IO + Job())
    private val uploadMutex = Mutex()
    private var dbObserverJob: Job? = null 
    
    private var isNetworkAvailable = false 

    private val apiService = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MeshApiService::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            isNetworkAvailable = false
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            isNetworkAvailable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (isNetworkAvailable) {
                flushQueueToCloud()
            }
        }
    }

    fun startListening() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        dbObserverJob?.cancel()
        dbObserverJob = uploadScope.launch {
            dao.observePendingUploads().collect { pendingMessages ->
                if (pendingMessages.isNotEmpty() && isNetworkAvailable) {
                    flushQueueToCloud()
                }
            }
        }
    }

    fun stopListening() {
        try {
            dbObserverJob?.cancel()
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("Gateway", "Error unregistering network callback: ${e.message}")
        }
    }

    private fun flushQueueToCloud() {
        uploadScope.launch {
            uploadMutex.withLock {
                val pendingMessages = dao.getPendingUploads()

                if (pendingMessages.isEmpty()) return@withLock

                try {
                    val dtos = pendingMessages.map { entity ->
                        SosPacketDto(
                            messageId = entity.messageId.toString(),
                            originalTimestamp = entity.timestamp / 1000, 
                            lat = entity.latitude,
                            lon = entity.longitude,
                            severity = SeverityLevel.fromCode(entity.severity).stringValue,
                            requestType = EmergencyType.fromCode(entity.emergencyType).stringValue,
                            ttl = entity.ttl.toInt()
                        )
                    }

                    val response = apiService.uploadBatch(dtos)

                    if (response.isSuccessful) {
                        val uploadedIds = pendingMessages.map { it.messageId }
                        dao.markAsUploaded(uploadedIds)
                        Log.d("Gateway", "Successfully uploaded ${uploadedIds.size} messages.")
                    } else {
                        Log.e("Gateway", "Server returned HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("Gateway", "Upload failed. Will retry next time internet connects.", e)
                }
            }
        }
    }
}