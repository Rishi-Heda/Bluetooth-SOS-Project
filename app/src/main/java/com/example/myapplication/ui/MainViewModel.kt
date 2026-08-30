package com.example.myapplication.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// FIX: Point to the new SDK module packages
import com.example.meshrelaysdk.data.local.AppDatabase
import com.example.meshrelaysdk.data.local.SosEntity
import com.example.meshrelaysdk.data.model.SosPacket
import com.example.myapplication.service.MeshForegroundService
import com.example.myapplication.utils.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).sosDao()
    private val locationHelper = LocationHelper(application)
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val messages: StateFlow<List<SosEntity>> = dao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // NEW: Network state flow
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        startNetworkListener()
    }

    private fun startNetworkListener() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        })

        // Initial check on startup
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    fun startMeshService() {
        val intent = Intent(getApplication(), MeshForegroundService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopMeshService() {
        val intent = Intent(getApplication(), MeshForegroundService::class.java)
        getApplication<Application>().stopService(intent)
    }

    fun broadcastMySos(emergencyType: Byte, severity: Byte) {
        viewModelScope.launch {
            val coordinates = locationHelper.getCurrentLocation()
            val lat = coordinates?.first ?: 0.0f
            val lon = coordinates?.second ?: 0.0f

            val mySos = SosEntity(
                messageId = SosPacket.generateId(),
                latitude = lat,
                longitude = lon,
                emergencyType = emergencyType,
                severity = severity,
                ttl = 5,
                syncStatus = 0
            )

            dao.insertPacket(mySos)
        }
    }

    fun dismissBroadcast(messageId: Int) {
        viewModelScope.launch {
            dao.dismissPacket(messageId)
        }
    }
}