package com.example.myapplication.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.myapplication.data.local.SosDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GatewayUploader(
    context: Context,
    private val dao: SosDao
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val uploadScope = CoroutineScope(Dispatchers.IO + Job())
    private val uploadMutex = Mutex() // Prevents overlapping duplicate uploads

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)

            // NET_CAPABILITY_INTERNET just means connected to a router.
            // NET_CAPABILITY_VALIDATED means that router actually has a path to the outside world.
            val hasRealInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (hasRealInternet) {
                Log.d("Gateway", "Internet Validated! Triggering upload sweep.")
                flushQueueToCloud()
            }
        }
    }

    /**
     * Registers the listener with the OS. Call this when the foreground service starts.
     */
    fun startListening() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Do an immediate check in case we already have internet when the app starts
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            flushQueueToCloud()
        }
    }

    /**
     * Unregisters the listener. Call this when the foreground service stops.
     */
    fun stopListening() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("Gateway", "Error unregistering network callback: ${e.message}")
        }
    }

    private fun flushQueueToCloud() {
        uploadScope.launch {
            // Mutex ensures if network flickers and triggers this twice rapidly,
            // we don't upload the same batch twice.
            uploadMutex.withLock {
                val pendingMessages = dao.getPendingUploads()

                if (pendingMessages.isEmpty()) {
                    Log.d("Gateway", "No pending messages to upload.")
                    return@withLock
                }

                Log.d("Gateway", "Attempting to upload ${pendingMessages.size} messages...")

                try {
                    // TODO: Replace this block with your actual HTTP POST to Supabase/Firebase/NextJS API
                    // val response = myRetrofitApi.uploadBatch(pendingMessages)

                    // Simulating network delay for the hackathon placeholder
                    kotlinx.coroutines.delay(1500)
                    val isUploadSuccessful = true

                    if (isUploadSuccessful) {
                        // Extract the IDs of the messages we just uploaded
                        val uploadedIds = pendingMessages.map { it.messageId }

                        // Mark them as uploaded in the local DB so they stop rebroadcasting
                        dao.markAsUploaded(uploadedIds)
                        Log.d("Gateway", "Successfully uploaded and marked ${uploadedIds.size} messages.")
                    }
                } catch (e: Exception) {
                    Log.e("Gateway", "Upload failed. Will retry next time internet connects.", e)
                }
            }
        }
    }
}