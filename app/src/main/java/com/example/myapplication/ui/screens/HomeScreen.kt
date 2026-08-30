package com.example.myapplication.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
// FIX: Update these 3 imports to point to the SDK
import com.example.meshrelaysdk.data.local.SosEntity
import com.example.meshrelaysdk.network.EmergencyType
import com.example.meshrelaysdk.network.SeverityLevel
import com.example.myapplication.ui.MainViewModel
import com.example.myapplication.utils.PermissionHelper

// --- Dark Bento Theme Palette ---
private val BgBlack = Color(0xFF000000)
private val BoxWhite = Color(0xFFFFFFFF)
private val BoxOrange = Color(0xFFF39C12) // Orangish-Yellow (Active Relay)
private val BoxDarkGray = Color(0xFF1C1C1E)
private val TextMainDark = Color(0xFF000000)
private val TextSubDark = Color(0xFF8E8E93)
private val TextMainLight = Color(0xFFFFFFFF)
private val TextSubLight = Color(0xFFAAAAAA)
private val AccentRed = Color(0xFFFF3B30) // Red (Dismissed/Error)
private val AccentGreen = Color(0xFF34C759) // Green (Uploaded/Online)
private val AccentBlue = Color(0xFF007AFF)

private val WidgetShape = RoundedCornerShape(32.dp)
private val InnerPillShape = RoundedCornerShape(16.dp)

@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    
    var permissionsGranted by remember { mutableStateOf(false) }
    var isMeshActive by remember { mutableStateOf(false) }

    // Real-time network state listener
    val isOnline by viewModel.isOnline.collectAsState()
    
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
            .background(BgBlack)
            .padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // 1. Top Header Pill
        HeaderPill(
            permissionsGranted = permissionsGranted,
            isOnline = isOnline,
            onGrantPermissions = { permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS.toTypedArray()) }
        )

        if (permissionsGranted) {
            // 2. Bento Grid Layout (2x2 Squares)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Top Row: Selections (Squares)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InteractiveSelectionBento(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        title = "Type",
                        items = EmergencyType.entries,
                        selectedItem = selectedType,
                        onItemSelected = { selectedType = it },
                        itemLabel = { it.stringValue }
                    )
                    
                    InteractiveSelectionBento(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        title = "Severity",
                        items = SeverityLevel.entries,
                        selectedItem = selectedSeverity,
                        onItemSelected = { selectedSeverity = it },
                        itemLabel = { it.stringValue }
                    )
                }

                // Bottom Row: Actions (Squares)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MeshControlBento(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        isActive = isMeshActive,
                        onToggle = { active ->
                            isMeshActive = active
                            if (active) viewModel.startMeshService() else viewModel.stopMeshService()
                        }
                    )

                    SosOrangeBento(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { viewModel.broadcastMySos(selectedType.code, selectedSeverity.code) }
                    )
                }
            }

            // 3. Bottom Broadcast Window
            BottomActivityWindow(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                messages = messages,
                onDismiss = { id -> viewModel.dismissBroadcast(id) }
            )
        }
    }
}

@Composable
private fun HeaderPill(permissionsGranted: Boolean, isOnline: Boolean, onGrantPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = WidgetShape,
        colors = CardDefaults.cardColors(containerColor = BoxDarkGray)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Internet Status Dot
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(if (isOnline) AccentGreen else AccentRed)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("NETWORK STATUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSubLight, letterSpacing = 1.sp)
                    Text(if (isOnline) "Online" else "Offline", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextMainLight)
                }
            }
            if (!permissionsGranted) {
                Button(
                    onClick = onGrantPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Fix Perms")
                }
            }
        }
    }
}

@Composable
private fun SosOrangeBento(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos-scale"
    )

    Card(
        modifier = modifier,
        shape = WidgetShape,
        colors = CardDefaults.cardColors(containerColor = BoxOrange)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(AccentRed.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(AccentRed)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("SOS", color = BoxWhite, fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun MeshControlBento(modifier: Modifier, isActive: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = modifier,
        shape = WidgetShape,
        colors = CardDefaults.cardColors(containerColor = BoxWhite)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BoxWhite,
                    checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = BoxWhite,
                    uncheckedTrackColor = TextSubLight
                )
            )
            
            Column {
                Text("Mesh", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextMainDark)
                Text(if (isActive) "Active" else "Offline", fontSize = 16.sp, color = TextSubDark)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun <T> InteractiveSelectionBento(
    modifier: Modifier,
    title: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String
) {
    var isSelecting by remember { mutableStateOf(true) }

    Card(
        modifier = modifier,
        shape = WidgetShape,
        colors = CardDefaults.cardColors(containerColor = BoxWhite)
    ) {
        AnimatedContent(
            targetState = isSelecting,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "selection_transition"
        ) { selecting ->
            if (selecting) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextSubDark, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items) { item ->
                            val isSelected = item == selectedItem
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(InnerPillShape)
                                    .background(if (isSelected) BoxDarkGray else BgBlack.copy(alpha = 0.05f))
                                    .clickable { 
                                        onItemSelected(item)
                                        isSelecting = false 
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = itemLabel(item),
                                    color = if (isSelected) BoxWhite else TextMainDark,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BoxWhite, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp).clickable { isSelecting = true },
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(modifier = Modifier.size(28.dp).background(BgBlack.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Change", tint = TextSubDark, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text(title, fontSize = 14.sp, color = TextSubDark)
                        Text(
                            text = itemLabel(selectedItem),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextMainDark,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActivityWindow(modifier: Modifier = Modifier, messages: List<SosEntity>, onDismiss: (Int) -> Unit) {
    Card(
        modifier = modifier,
        shape = WidgetShape,
        colors = CardDefaults.cardColors(containerColor = BoxDarkGray)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 20.dp)) {
            Text(
                text = "Recent Broadcasts",
                fontWeight = FontWeight.Bold,
                color = TextMainLight,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )
            
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No local mesh traffic.", color = TextSubLight, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        MessagePillRow(msg = msg, onDismiss = { onDismiss(msg.messageId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagePillRow(msg: SosEntity, onDismiss: () -> Unit) {
    // Determine right-edge bar color
    val statusColor = when {
        msg.syncStatus == 1 -> AccentGreen // Uploaded
        msg.isDismissed -> AccentRed       // Dismissed
        else -> BoxOrange                  // Active Relay
    }
    
    // Convert TTL (max 5) into a Hop counter
    val hops = 5 - msg.ttl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) 
            .clip(InnerPillShape)
            .background(BgBlack.copy(alpha = 0.4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Message Content
        Row(
            modifier = Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${EmergencyType.fromCode(msg.emergencyType).stringValue} • ${SeverityLevel.fromCode(msg.severity).stringValue}",
                    color = TextMainLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Hops: $hops • ID: ${msg.messageId}", 
                    color = TextSubLight, 
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                // GPS Coordinates included
                Text(
                    "GPS: ${msg.latitude}, ${msg.longitude}", 
                    color = TextSubLight, 
                    fontSize = 11.sp
                )
            }
            
            // Dismiss Button
            if (!msg.isDismissed && msg.syncStatus == 0) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSubLight, modifier = Modifier.size(18.dp))
                }
            }
        }
        
        // Right Edge Status Bar
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(statusColor)
        )
    }
}