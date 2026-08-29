package com.example.myapplication.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.MainViewModel
import com.example.myapplication.utils.PermissionHelper

@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()

    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Check if all requested permissions were granted
        permissionsGranted = results.values.all { it }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Offline Disaster Mesh", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (!permissionsGranted) {
            Button(onClick = { permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS.toTypedArray()) }) {
                Text("1. Grant Permissions")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { PermissionHelper.requestBatteryExemption(context) }) {
                Text("2. Disable Battery Optimization")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { viewModel.startMeshService() }) {
                    Text("Start Mesh")
                }
                Button(
                    onClick = { viewModel.stopMeshService() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Stop Mesh")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.broadcastMySos() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("BROADCAST SOS", color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Mesh Activity Log (${messages.size} Messages)", style = MaterialTheme.typography.titleMedium)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // The list of received or locally generated messages
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.syncStatus == 1) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ID: ${msg.messageId} | TTL: ${msg.ttl}", style = MaterialTheme.typography.labelLarge)
                            Text("GPS: ${msg.latitude}, ${msg.longitude}")
                            Text(
                                text = if (msg.syncStatus == 1) "Status: Uploaded to Cloud" else "Status: Relaying Offline...",
                                color = if (msg.syncStatus == 1) Color(0xFF2E7D32) else Color(0xFFE65100),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}