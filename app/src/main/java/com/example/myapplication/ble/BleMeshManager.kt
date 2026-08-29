package com.example.myapplication.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import com.example.myapplication.data.local.SosDao
import com.example.myapplication.data.local.SosEntity
import com.example.myapplication.data.model.SosPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("MissingPermission")
class BleMeshManager(
    private val context: Context,
    private val dao: SosDao
) {
    // 0xFFFF is a reserved "Testing" Manufacturer ID.
    // We use this to identify our mesh packets without wasting 16 bytes on a Service UUID.
    private val MANUFACTURER_ID = 0xFFFF

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val bleAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private var meshJob: Job? = null
    val isMeshRunning = MutableStateFlow(false)

    /**
     * Starts the Time-Slicer heartbeat loop.
     */
    fun startMesh() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("Mesh", "Bluetooth is disabled or not supported.")
            return
        }

        if (isMeshRunning.value) return
        isMeshRunning.value = true

        // Launch the time-slicer on a background I/O thread
        meshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                // 1. SCAN PHASE (6 Seconds)
                scanForPeers()
                delay(6000)
                stopScanning()

                // 2. ADVERTISE PHASE (3 Seconds)
                val packetsToRelay = dao.getPacketsForRelay()
                if (packetsToRelay.isNotEmpty()) {
                    // We pick the oldest unsent packet to advertise in this time slice
                    val packetEntity = packetsToRelay.first()
                    advertisePacket(packetEntity)
                    delay(3000)
                    stopAdvertising()
                } else {
                    // If local queue is empty, take a short breather to save battery
                    delay(1000)
                }
            }
        }
    }

    fun stopMesh() {
        isMeshRunning.value = false
        meshJob?.cancel()
        stopScanning()
        stopAdvertising()
    }

    // =======================================================================
    // SCANNING LOGIC
    // =======================================================================

    private fun scanForPeers() {
        Log.d("Mesh", "STATE: Scanning...")

        // Filter strictly for our Manufacturer ID so the OS doesn't wake us up for random smartwatches
        val filter = ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID, null)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // High power for short burst
            .build()

        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)
            if (manufacturerData != null) {
                // We caught a packet! Try to deserialize it.
                val packet = SosPacket.fromByteArray(manufacturerData)

                if (packet != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val entity = SosEntity(
                            messageId = packet.messageId,
                            latitude = packet.latitude,
                            longitude = packet.longitude,
                            severity = packet.severity,
                            ttl = packet.ttl,
                            syncStatus = 0 // Needs to be uploaded when we hit the gateway
                        )

                        // Deduplication Engine: Room drops this if the ID already exists
                        val rowId = dao.insertPacket(entity)
                        if (rowId != -1L) {
                            Log.d("Mesh", "RX: New SOS! ID=${packet.messageId} Saved to Queue.")
                        }
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("Mesh", "Scan failed with error: $errorCode")
        }
    }

    // =======================================================================
    // ADVERTISING LOGIC
    // =======================================================================

    private var currentAdvertiseCallback: AdvertiseCallback? = null

    private suspend fun advertisePacket(entity: SosEntity) {
        Log.d("Mesh", "STATE: Advertising ID=${entity.messageId}...")

        // Decrement TTL in the database so it eventually dies out
        dao.decrementTtl(entity.messageId)

        val packet = entity.toSosPacket()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Max range
            .setConnectable(false) // Pure broadcast, no pairing allowed
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Save space
            .setIncludeTxPowerLevel(false) // Save space
            .addManufacturerData(MANUFACTURER_ID, packet.toByteArray())
            .build()

        currentAdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("Mesh", "TX: Broadcasting packet successfully.")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("Mesh", "TX: Broadcast failed. Error: $errorCode")
            }
        }

        bleAdvertiser?.startAdvertising(settings, data, currentAdvertiseCallback)
    }

    private fun stopAdvertising() {
        currentAdvertiseCallback?.let {
            bleAdvertiser?.stopAdvertising(it)
            currentAdvertiseCallback = null
        }
    }
}