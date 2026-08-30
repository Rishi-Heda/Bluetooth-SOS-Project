package com.example.meshrelaysdk

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

    // Rotates through the pending-relay queue so a single stuck/slow-to-upload
    // message doesn't monopolize every advertise cycle and starve newer messages.
    private var relayIndex = 0

    // How long each message gets "on air" per advertise slot before we rotate
    // to the next pending message. Shorter = more messages get a turn during a
    // brief encounter, at the cost of slightly more BLE start/stop churn.
    private val ADVERTISE_SLOT_MS = 2000L
    private val EMPTY_QUEUE_BACKOFF_MS = 1000L

    /**
     * Starts the mesh: scanning runs continuously for the whole session,
     * advertising is time-sliced across whatever messages are pending.
     *
     * Scanning is intentionally NOT stopped/restarted every cycle — Android
     * silently throttles apps that call startScan()/stopScan() too many times
     * in a short window, which would make detection *worse*, not better, if
     * we cycled scanning on and off frequently. A phone can scan and advertise
     * at the same time, so there's no need to pause scanning while advertising.
     */
    fun startMesh() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("Mesh", "Bluetooth is disabled or not supported.")
            return
        }

        if (isMeshRunning.value) return
        isMeshRunning.value = true

        // Start scanning ONCE for the whole mesh session.
        scanForPeers()

        // The loop now only handles rotating the advertised message.
        meshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val packetsToRelay = dao.getPacketsForRelay()
                    if (packetsToRelay.isNotEmpty()) {
                        // Round-robin through ALL currently pending messages (no longer
                        // capped at 3 — see SosDao.getPacketsForRelay), so every queued
                        // SOS eventually gets airtime instead of only the first few.
                        val packetEntity = packetsToRelay[relayIndex % packetsToRelay.size]
                        relayIndex++

                        advertisePacket(packetEntity)
                        delay(ADVERTISE_SLOT_MS)
                        stopAdvertising()
                    } else {
                        relayIndex = 0
                        delay(EMPTY_QUEUE_BACKOFF_MS)
                    }
                } catch (e: Exception) {
                    // A single bad iteration (DB hiccup, BLE API throwing unexpectedly,
                    // etc.) should not silently kill the whole mesh loop forever.
                    Log.e("Mesh", "Loop iteration failed, continuing anyway", e)
                    delay(EMPTY_QUEUE_BACKOFF_MS)
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
        Log.d("Mesh", "STATE: Scanning (continuous)...")

        // Filter strictly for our Manufacturer ID so the OS doesn't wake us up for random smartwatches
        val filter = ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID, null)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
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
                val packet = SosPacket.fromByteArray(manufacturerData)

                if (packet != null) {
                    val newTtl = (packet.ttl - 1).coerceAtLeast(0)

                    if (newTtl <= 0 && packet.ttl <= 0) {
                        Log.d("Mesh", "RX: Dropped ID=${packet.messageId}, TTL exhausted.")
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        val entity = SosEntity(
                            messageId = packet.messageId,
                            latitude = packet.latitude,
                            longitude = packet.longitude,
                            emergencyType = packet.emergencyType, // NEW: Must map to Entity
                            severity = packet.severity,
                            ttl = newTtl.toByte(),
                            syncStatus = 0 
                        )

                        val rowId = dao.insertPacket(entity)
                        if (rowId != -1L) {
                            Log.d("Mesh", "RX: New SOS! ID=${packet.messageId} Saved to Queue. TTL now $newTtl")
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
        Log.d("Mesh", "STATE: Advertising ID=${entity.messageId}, TTL=${entity.ttl}...")

        // NOTE: TTL is intentionally NOT decremented here. Advertising is just an
        // "offer" broadcast into the air — it says nothing about whether any peer
        // actually received it. TTL only drops when a receiving device (see
        // scanCallback.onScanResult above) actually catches the packet, since
        // that's the only point representing a genuine hop.

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