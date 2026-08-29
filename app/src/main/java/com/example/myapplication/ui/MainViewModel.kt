package com.example.myapplication.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.SosEntity
import com.example.myapplication.data.model.SosPacket
import com.example.myapplication.service.MeshForegroundService
import com.example.myapplication.utils.LocationHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).sosDao()
    private val locationHelper = LocationHelper(application)

    // Automatically feeds the latest DB rows to the Jetpack Compose UI
    val messages: StateFlow<List<SosEntity>> = dao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun startMeshService() {
        val intent = Intent(getApplication(), MeshForegroundService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopMeshService() {
        val intent = Intent(getApplication(), MeshForegroundService::class.java)
        getApplication<Application>().stopService(intent)
    }

    fun broadcastMySos() {
        viewModelScope.launch {
            // Fetch real GPS, fallback to 0.0 if the location request fails
            val coordinates = locationHelper.getCurrentLocation()
            val lat = coordinates?.first ?: 0.0f
            val lon = coordinates?.second ?: 0.0f

            val mySos = SosEntity(
                messageId = SosPacket.generateId(),
                latitude = lat,
                longitude = lon,
                severity = 1,         // 1 = Medical Emergency
                ttl = 5,              // Allow 5 hops across the mesh
                syncStatus = 0        // 0 = PENDING_UPLOAD
            )

            // Inserting it into the DB automatically queues it for the BLE Time-Slicer to advertise
            dao.insertPacket(mySos)
        }
    }
}