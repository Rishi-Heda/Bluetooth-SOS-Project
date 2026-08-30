package com.example.myapplication.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// FIX: Point to the new SDK module packages
import com.example.meshrelaysdk.data.local.AppDatabase
import com.example.meshrelaysdk.data.local.SosEntity
import com.example.meshrelaysdk.data.model.SosPacket
import com.example.myapplication.service.MeshForegroundService
import com.example.myapplication.utils.LocationHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).sosDao()
    private val locationHelper = LocationHelper(application)

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