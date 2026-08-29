package com.example.myapplication.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.local.SosEntity
import com.example.myapplication.ui.MainViewModel
import com.example.myapplication.utils.PermissionHelper
import com.example.myapplication.network.EmergencyType
import com.example.myapplication.network.SeverityLevel

// --- New Light & Airy Color Palette ---
private val AppBackground = Color(0xFFF4F7F9)
private val SurfaceWhite = Color(0xFFFFFFFF)
private val PrimaryBlue = Color(0xFF4A90E2)
private val PrimaryBlueSoft = Color(0xFFEBF3FC)
private val TextDark = Color(0xFF2C3E50)
private val TextSlate = Color(0xFF7F8C8D)
private val DangerRed = Color(0xFFFF4757)
private val DangerRedSoft = Color(0xFFFFEBEE)
private val SuccessGreen = Color(0xFF2ED573)
private val WarningOrange = Color(0xFFFFA502)

@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    
    var permissionsGranted by remember { mutableStateOf(false) }
    var isMeshActive by remember { mutableStateOf(false) }

    var selectedType by remember { mutableStateOf(EmergencyType.SOS_SIGNAL) }
    var selectedSeverity by remember { mutableStateOf(SeverityLevel.CRITICAL) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Header
        Text(
            text = "Disaster Mesh",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark
        )
        Text(
            text = "Peer-to-peer emergency relay",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSlate
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        if (!permissionsGranted) {
            SetupSection(
                onGrantPermissions = {
                    permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS.toTypedArray())
                },
                onDisableBattery = { PermissionHelper.requestBatteryExemption(context) }
            )
        } else {
            // New Toggle Switch for Mesh Control
            MeshToggleCard(
                isActive = isMeshActive,
                onToggle = { active ->
                    isMeshActive = active
                    if (active) viewModel.startMeshService() else viewModel.stopMeshService()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            
            // Redesigned Dropdowns
            SelectionRow(
                selectedType = selectedType,
                selectedSeverity = selectedSeverity,
                onTypeChange = { selectedType = it },
                onSeverityChange = { selectedSeverity = it }
            )

            Spacer(modifier = Modifier.height(28.dp))
            
            SosButton(onClick = { viewModel.broadcastMySos(selectedType.code, selectedSeverity.code) })

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Activity Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No local mesh activity.", color = TextSlate, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Tighter spacing
                ) {
                    items(messages) { msg ->
                        MessageCard(
                            msg = msg,
                            onDismiss = { viewModel.dismissBroadcast(msg.messageId) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SetupSection(onGrantPermissions: () -> Unit, onDisableBattery: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onGrantPermissions,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("1. Grant Permissions")
            }
            OutlinedButton(
                onClick = onDisableBattery,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("2. Disable Battery Optimization", color = TextDark)
            }
        }
    }
}

@Composable
private fun MeshToggleCard(isActive: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Mesh Network", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
                Text(
                    text = if (isActive) "Actively listening & relaying" else "Offline",
                    color = if (isActive) SuccessGreen else TextSlate,
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SurfaceWhite,
                    checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = TextSlate,
                    uncheckedTrackColor = AppBackground
                )
            )
        }
    }
}

@Composable
private fun SelectionRow(
    selectedType: EmergencyType,
    selectedSeverity: SeverityLevel,
    onTypeChange: (EmergencyType) -> Unit,
    onSeverityChange: (SeverityLevel) -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var severityExpanded by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // Type Dropdown
        Box(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Category", fontSize = 11.sp, color = TextSlate)
                        Text(selectedType.stringValue, fontWeight = FontWeight.SemiBold, color = PrimaryBlue, fontSize = 14.sp)
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSlate)
                }
            }
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }, modifier = Modifier.background(SurfaceWhite)) {
                EmergencyType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.stringValue, color = TextDark) },
                        onClick = { onTypeChange(type); typeExpanded = false }
                    )
                }
            }
        }

        // Severity Dropdown
        Box(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { severityExpanded = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Severity", fontSize = 11.sp, color = TextSlate)
                        Text(selectedSeverity.stringValue, fontWeight = FontWeight.SemiBold, color = DangerRed, fontSize = 14.sp)
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSlate)
                }
            }
            DropdownMenu(expanded = severityExpanded, onDismissRequest = { severityExpanded = false }, modifier = Modifier.background(SurfaceWhite)) {
                SeverityLevel.entries.forEach { sev ->
                    DropdownMenuItem(
                        text = { Text(sev.stringValue, color = TextDark) },
                        onClick = { onSeverityChange(sev); severityExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SosButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos-scale"
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(DangerRedSoft)
        )
        
        // Inner clickable button
        Button(
            onClick = onClick,
            modifier = Modifier.size(130.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)
        ) {
            Text("SOS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SurfaceWhite)
        }
    }
}

@Composable
private fun MessageCard(msg: SosEntity, onDismiss: () -> Unit) {
    // Determine dynamic colors for the status pill
    val (statusText, statusBg, statusColor) = when {
        msg.isDismissed -> Triple("Dismissed", Color(0xFFEEEEEE), TextSlate)
        msg.syncStatus == 1 -> Triple("Synced", Color(0xFFE8F5E9), SuccessGreen)
        else -> Triple("Relaying", Color(0xFFFFF3E0), WarningOrange)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        // Slimmer padding (10dp instead of 16dp)
        Column(modifier = Modifier.padding(10.dp)) {
            
            // Top Row: Badges & ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Slim Type Badge
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PrimaryBlueSoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(EmergencyType.fromCode(msg.emergencyType).stringValue, color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    // Slim Severity Badge
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DangerRedSoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(SeverityLevel.fromCode(msg.severity).stringValue, color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Dismiss Button (inline to save vertical space)
                if (!msg.isDismissed && msg.syncStatus == 0) {
                    Box(modifier = Modifier.clickable { onDismiss() }.padding(4.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSlate, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))

            // Middle Row: TTL & ID
            Text(
                "ID: ${msg.messageId}  •  TTL: ${msg.ttl}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            
            Spacer(modifier = Modifier.height(2.dp))

            // Bottom Row: GPS & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GPS: ${msg.latitude}, ${msg.longitude}",
                    fontSize = 11.sp,
                    color = TextSlate
                )
                
                // Slim Status Pill
                Box(modifier = Modifier.clip(CircleShape).background(statusBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}