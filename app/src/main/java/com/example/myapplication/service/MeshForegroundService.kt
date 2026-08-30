package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.meshrelaysdk.ble.BleMeshManager
import com.example.meshrelaysdk.data.local.AppDatabase
import com.example.meshrelaysdk.network.GatewayUploader

class MeshForegroundService : Service() {

    private val CHANNEL_ID = "MeshServiceChannel"
    private val NOTIFICATION_ID = 1

    private lateinit var database: AppDatabase
    private lateinit var bleMeshManager: BleMeshManager
    private lateinit var gatewayUploader: GatewayUploader

    override fun onCreate() {
        super.onCreate()
        Log.d("MeshService", "Service Created")

        // Initialize the Database
        database = AppDatabase.getDatabase(this)

        // Initialize the sub-modules, passing the Dao they both need
        bleMeshManager = BleMeshManager(this, database.sosDao())
        
        // FIX: Provide the backend URL to the SDK here
        val myBackendUrl = "https://dashboard-bluetooth-sos.onrender.com/api/sos/"
        gatewayUploader = GatewayUploader(this, database.sosDao(), myBackendUrl)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MeshService", "Service Started")

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Disaster Mesh Active")
            .setContentText("Relaying SOS signals in the background...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) 
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        bleMeshManager.startMesh()
        gatewayUploader.startListening()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MeshService", "Service Destroyed")

        bleMeshManager.stopMesh()
        gatewayUploader.stopListening()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Network Service Channel",
                NotificationManager.IMPORTANCE_LOW 
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}