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
import com.example.myapplication.ble.BleMeshManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.network.GatewayUploader

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
        gatewayUploader = GatewayUploader(this, database.sosDao())

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MeshService", "Service Started")

        // 1. Build the persistent notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Disaster Mesh Active")
            .setContentText("Relaying SOS signals in the background...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Replace with your own app icon later
            .setOngoing(true)
            .build()

        // 2. Start the Foreground Service safely specifying the Connected Device type (Required for Android 14+)
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

        // 3. Kick off the Scan/Advertise Time-Slicer heartbeat
        bleMeshManager.startMesh()

        // 4. Start listening for internet connections to flush the queue
        gatewayUploader.startListening()

        // START_STICKY tells the OS: "If you must kill this service for memory, restart it ASAP"
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MeshService", "Service Destroyed")

        // Gracefully shut down hardware listeners to prevent battery drain and memory leaks
        bleMeshManager.stopMesh()
        gatewayUploader.stopListening()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We return null because this is a "Started" service, not a "Bound" service.
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Network Service Channel",
                NotificationManager.IMPORTANCE_LOW // LOW importance = no sound/vibration, just the icon
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}