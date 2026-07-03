package com.spop.poverlay

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import com.spop.poverlay.releases.Release
import com.spop.poverlay.sensor.heartrate.HeartRateDevice
import com.spop.poverlay.sensor.heartrate.HeartRateManager
import com.spop.poverlay.ui.theme.ErrorColor
import kotlin.math.max
import kotlin.math.min

private data class UiScale(
                val value: Float
) {
        fun sp(base: Float) = max(base * value, 16f).sp
        fun dp(base: Float) = (base * value).dp
}

@Composable
fun ConfigurationPage(viewModel: ConfigurationViewModel) {
    val showPermissionInfo by remember { viewModel.showPermissionInfo }
    val latestRelease by remember { viewModel.latestRelease }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthScale = maxWidth.value / 1200f
        val heightScale = maxHeight.value / 800f
        val rawScale = min(widthScale, heightScale)
        val uiScale = UiScale(max(0.58f, min(rawScale, 1.15f)))

        Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            if (showPermissionInfo) {
                PermissionPage(
                        onClickedGrantPermission = viewModel::onGrantPermissionClicked,
                        uiScale = uiScale
                )
            } else {
                val timerShownWhenMinimized by
                        viewModel.showTimerWhenMinimized.collectAsStateWithLifecycle(
                                initialValue = true
                        )
                val bleTxEnabled by
                        viewModel.bleTxEnabled.collectAsStateWithLifecycle(initialValue = false)
                val dirConEnabled by
                        viewModel.dirConEnabled.collectAsStateWithLifecycle(initialValue = true)
                val bleFtmsDeviceName by
                        viewModel.bleFtmsDeviceName.collectAsStateWithLifecycle(
                                initialValue = "Grupetto FTMS"
                        )
                val hrConnectedDevice by
                        viewModel.hrConnectedDevice.collectAsStateWithLifecycle(initialValue = null)
                val hrDiscoveredDevices by
                        viewModel.hrDiscoveredDevices.collectAsStateWithLifecycle(initialValue = emptyList())
                val hrSavedDevices by
                        viewModel.hrSavedDevices.collectAsStateWithLifecycle(initialValue = emptyList())
                val hrIsScanning by
                        viewModel.hrIsScanning.collectAsStateWithLifecycle(initialValue = false)
                val hrMatchByName by
                        viewModel.hrMatchByName.collectAsStateWithLifecycle(initialValue = false)
                val isOverlayRunning by
                        viewModel.isOverlayRunning.collectAsStateWithLifecycle(initialValue = false)
                StartServicePage(
                        timerShownWhenMinimized,
                        viewModel::onShowTimerWhenMinimizedClicked,
                        bleTxEnabled,
                        viewModel::onBleTxEnabledClicked,
                        dirConEnabled,
                        viewModel::onDirConEnabledClicked,
                        bleFtmsDeviceName,
                        hrConnectedDevice,
                        hrDiscoveredDevices,
                        hrSavedDevices,
                        hrIsScanning,
                        hrMatchByName,
                        viewModel::startHeartRateDiscovery,
                        viewModel::stopHeartRateDiscovery,
                        viewModel::connectHeartRateDevice,
                        viewModel::disconnectHeartRateDevice,
                        viewModel::forgetHeartRateDevice,
                        viewModel::setHrMatchByName,
                        isOverlayRunning,
                        uiScale,
                        viewModel::onStartServiceClicked,
                        viewModel::onQuitClicked,
                        viewModel::onClickedRelease,
                        latestRelease
                )
            }
        }
    }
}

@Composable
private fun StartServicePage(
        timerShownWhenMinimized: Boolean,
        onTimerShownWhenMinimizedToggled: (Boolean) -> Unit,
        bleTxEnabled: Boolean,
        onBleTxEnabledToggled: (Boolean) -> Unit,
        dirConEnabled: Boolean,
        onDirConEnabledToggled: (Boolean) -> Unit,
        bleFtmsDeviceName: String,
        hrConnectedDevice: HeartRateDevice?,
        hrDiscoveredDevices: List<HeartRateDevice>,
        hrSavedDevices: List<HeartRateDevice>,
        hrIsScanning: Boolean,
        hrMatchByName: Boolean,
        onStartHeartRateDiscovery: () -> Unit,
        onStopHeartRateDiscovery: () -> Unit,
        onConnectHeartRateDevice: (HeartRateDevice) -> Unit,
        onDisconnectHeartRateDevice: () -> Unit,
        onForgetHeartRateDevice: (String) -> Unit,
        onSetHrMatchByName: (Boolean) -> Unit,
        isOverlayRunning: Boolean,
        uiScale: UiScale,
        onClickedStartOverlay: () -> Unit,
        onClickedQuitApp: () -> Unit,
        onClickedRelease: (Release) -> Unit,
        latestRelease: Release?
) {
    var showHeartRateDialog by remember { mutableStateOf(false) }

    val contentWidth = 900.dp
    val cardPadding = uiScale.dp(14f)
    val cardColor = Color(0xFF1E1E1E)
    val headingColor = Color(0xFFF2F2F2)
    val bodyColor = Color(0xFFD0D0D0)
    val accentColor = Color(0xFF4DA3FF)
    val hrStatus = hrConnectedDevice?.let { "Connected to ${it.name ?: it.address}" } ?: "Disconnected"

    CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontSize = uiScale.sp(16f))
    ) {
        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = uiScale.dp(16f))
                        .widthIn(max = contentWidth),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
                text = "Grupetto",
                fontSize = uiScale.sp(40f),
                fontWeight = FontWeight.Bold
        )
        Text(
                text = "Overlay for Peloton metrics",
                fontSize = uiScale.sp(18f),
                color = bodyColor
        )
        Spacer(modifier = Modifier.height(uiScale.dp(16f)))

        Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = cardColor,
                elevation = uiScale.dp(4f)
        ) {
            Column(modifier = Modifier.padding(cardPadding)) {
                Text("Overlay", fontSize = uiScale.sp(18f), fontWeight = FontWeight.Bold, color = headingColor)
                Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                Button(
                        onClick = onClickedStartOverlay,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = accentColor)
                ) {
                    Text(
                            text = if (isOverlayRunning) "Restart Overlay" else "Start Overlay",
                            fontSize = uiScale.sp(18f),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(uiScale.dp(12f)))

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(uiScale.dp(12f))
        ) {
            Card(
                    modifier = Modifier.weight(1f),
                    backgroundColor = cardColor,
                    elevation = uiScale.dp(4f)
            ) {
                Column(modifier = Modifier.padding(cardPadding)) {
                    Text("Timer Preference", fontSize = uiScale.sp(18f), fontWeight = FontWeight.Bold, color = headingColor)
                    Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show timer when minimized", fontSize = uiScale.sp(16f), color = bodyColor)
                        Switch(
                                checked = timerShownWhenMinimized,
                                onCheckedChange = onTimerShownWhenMinimizedToggled,
                                colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF22C55E),
                                        checkedTrackColor = Color(0xFF22C55E)
                                )
                        )
                    }
                }
            }

            Card(
                    modifier = Modifier.weight(1f),
                    backgroundColor = cardColor,
                    elevation = uiScale.dp(4f)
            ) {
                Column(modifier = Modifier.padding(cardPadding)) {
                    Text("Broadcast", fontSize = uiScale.sp(18f), fontWeight = FontWeight.Bold, color = headingColor)
                    Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BLE", fontSize = uiScale.sp(16f), color = bodyColor)
                        Switch(
                                checked = bleTxEnabled,
                                onCheckedChange = onBleTxEnabledToggled,
                                colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF22C55E),
                                        checkedTrackColor = Color(0xFF22C55E)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DIRCON WiFi", fontSize = uiScale.sp(16f), color = bodyColor)
                        Switch(
                                checked = dirConEnabled,
                                onCheckedChange = onDirConEnabledToggled,
                                colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF22C55E),
                                        checkedTrackColor = Color(0xFF22C55E)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                    if (bleTxEnabled || dirConEnabled) {
                        Text(
                                text = "Broadcasting as",
                                fontSize = uiScale.sp(14f),
                                color = bodyColor
                        )
                        Text(
                                text = bleFtmsDeviceName,
                                fontSize = uiScale.sp(20f),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )
                    } else {
                        Text(
                                text = "Enable BLE or DIRCON to broadcast bike data to apps like Zwift or TrainerRoad.",
                                fontSize = uiScale.sp(13f),
                                color = bodyColor
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(uiScale.dp(12f)))

        Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = cardColor,
                elevation = uiScale.dp(4f)
        ) {
            Column(modifier = Modifier.padding(cardPadding)) {
                Text("Heart Rate Monitors", fontSize = uiScale.sp(18f), fontWeight = FontWeight.Bold, color = headingColor)
                Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                OutlinedButton(
                        onClick = { showHeartRateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heart Rate",
                                tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(uiScale.dp(10f)))
                        Text("Manage Heart Rate Monitors")
                    }
                }
                Spacer(modifier = Modifier.height(uiScale.dp(6f)))
                if (hrConnectedDevice != null) {
                    Text(
                            text = "Connected to",
                            fontSize = uiScale.sp(14f),
                            color = bodyColor
                    )
                    Text(
                            text = hrConnectedDevice.name ?: hrConnectedDevice.address,
                            fontSize = uiScale.sp(20f),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                            text = hrStatus,
                            fontSize = uiScale.sp(13f),
                            color = bodyColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(uiScale.dp(12f)))

        Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = cardColor,
                elevation = uiScale.dp(4f)
        ) {
            Column(modifier = Modifier.padding(cardPadding)) {
                Text("Updates", fontSize = uiScale.sp(18f), fontWeight = FontWeight.Bold, color = headingColor)
                Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                if (latestRelease == null) {
                    Text("Couldn't check for updates", color = bodyColor)
                } else {
                    val formattedDate = DateUtils.getRelativeTimeSpanString(latestRelease.createdAt.time)
                    val text = if (latestRelease.isCurrentlyInstalled) {
                        "Up to date: ${latestRelease.tagName} • $formattedDate • ${latestRelease.friendlyName}"
                    } else {
                        "New version: ${latestRelease.friendlyName} • $formattedDate"
                    }
                    Text(text, color = bodyColor, fontSize = uiScale.sp(16f))
                    TextButton(onClick = { onClickedRelease(latestRelease) }) {
                        Text("View release", color = accentColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(uiScale.dp(12f)))

        Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = cardColor,
                elevation = uiScale.dp(4f)
        ) {
            Column(modifier = Modifier.padding(cardPadding)) {
                Text("App Actions", fontSize = uiScale.sp(18f), fontWeight = FontWeight.Bold, color = headingColor)
                Spacer(modifier = Modifier.height(uiScale.dp(8f)))
                Button(
                        onClick = onClickedQuitApp,
                        colors = ButtonDefaults.buttonColors(backgroundColor = ErrorColor),
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Quit App", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(uiScale.dp(10f)))
        Text(
                "Device: ${Build.DEVICE} • SDK: ${Build.VERSION.RELEASE}",
                fontSize = uiScale.sp(12f),
                color = LocalContentColor.current.copy(alpha = .55f)
        )
        }
    }

    if (showHeartRateDialog) {
        HeartRateManagerDialog(
                connectedDevice = hrConnectedDevice,
                discoveredDevices = hrDiscoveredDevices,
                savedDevices = hrSavedDevices,
                isScanning = hrIsScanning,
                matchByName = hrMatchByName,
                onStartDiscovery = onStartHeartRateDiscovery,
                onStopDiscovery = onStopHeartRateDiscovery,
                onConnectDevice = onConnectHeartRateDevice,
                onDisconnectConnectedDevice = onDisconnectHeartRateDevice,
                onForgetDevice = onForgetHeartRateDevice,
                onSetMatchByName = onSetHrMatchByName,
                onDismiss = { showHeartRateDialog = false }
        )
    }

}

@Composable
private fun PermissionPage(onClickedGrantPermission: () -> Unit, uiScale: UiScale) {
    Text(
            text = "Grupetto Needs Permission To Draw Over Other Apps",
            fontSize = uiScale.sp(40f),
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold
    )
    Text(
            text = "It uses this permission to draw an overlay with your bike's sensor data",
            fontSize = uiScale.sp(20f),
            fontWeight = FontWeight.Normal
    )
    Spacer(modifier = Modifier.height(uiScale.dp(10f)))
    Button(onClick = onClickedGrantPermission) { Text(text = "Grant Permission") }
}

@Composable
private fun HeartRateManagerDialog(
                connectedDevice: HeartRateDevice?,
                discoveredDevices: List<HeartRateDevice>,
                savedDevices: List<HeartRateDevice>,
                isScanning: Boolean,
                matchByName: Boolean,
                onStartDiscovery: () -> Unit,
                onStopDiscovery: () -> Unit,
                onConnectDevice: (HeartRateDevice) -> Unit,
                onDisconnectConnectedDevice: () -> Unit,
                onForgetDevice: (String) -> Unit,
                onSetMatchByName: (Boolean) -> Unit,
                onDismiss: () -> Unit
) {
        val zone12 = remember { mutableStateOf("") }
        val zone23 = remember { mutableStateOf("") }
        val zone34 = remember { mutableStateOf("") }
        val zone45 = remember { mutableStateOf("") }
        val cardColor = Color(0xFF1E1E1E)
        val headingColor = Color(0xFFF2F2F2)
        val bodyColor = Color(0xFFD0D0D0)
        val accentColor = Color(0xFF4DA3FF)
        val currentHeartRate by HeartRateManager.heartRate.collectAsStateWithLifecycle(initialValue = null)
        val savedZone12 by HeartRateManager.zone12.collectAsStateWithLifecycle(initialValue = null)
        val savedZone23 by HeartRateManager.zone23.collectAsStateWithLifecycle(initialValue = null)
        val savedZone34 by HeartRateManager.zone34.collectAsStateWithLifecycle(initialValue = null)
        val savedZone45 by HeartRateManager.zone45.collectAsStateWithLifecycle(initialValue = null)

        androidx.compose.runtime.LaunchedEffect(savedZone12, savedZone23, savedZone34, savedZone45) {
                if (zone12.value.isBlank() && savedZone12 != null) zone12.value = savedZone12.toString()
                if (zone23.value.isBlank() && savedZone23 != null) zone23.value = savedZone23.toString()
                if (zone34.value.isBlank() && savedZone34 != null) zone34.value = savedZone34.toString()
                if (zone45.value.isBlank() && savedZone45 != null) zone45.value = savedZone45.toString()
        }

        fun parseZone(value: String): Int? = value.toIntOrNull()
        fun updateZones() {
                HeartRateManager.setHeartRateZones(
                        parseZone(zone12.value),
                        parseZone(zone23.value),
                        parseZone(zone34.value),
                        parseZone(zone45.value)
                )
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
                onStartDiscovery()
                HeartRateManager.setManaging(true)
        }
        androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                        onStopDiscovery()
                        HeartRateManager.setManaging(false)
                }
        }

        CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontSize = 16.sp)
        ) {
        AlertDialog(
                        onDismissRequest = onDismiss,
                        backgroundColor = cardColor,
                        title = { Text("Manage Heart Rate Monitors", color = headingColor) },
                        text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                        Card(
                                                        backgroundColor = Color(0xFF252525),
                                                        modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                        SectionHeader("Connected")
                                        if (connectedDevice == null) {
                                                Text("None", color = bodyColor)
                                        } else {
                                                Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                         Column(modifier = Modifier.weight(1f)) {
                                                                 Text(
                                                                                 text = connectedDevice.name ?: "Unknown",
                                                                                 fontSize = 16.sp,
                                                                                 color = headingColor
                                                                  )
                                                                  Text(
                                                                                 text = connectedDevice.address,
                                                                                 fontSize = 16.sp,
                                                                                 color = bodyColor
                                                                  )
                                                                 Row(
                                                                                 verticalAlignment = Alignment.CenterVertically
                                                                 ) {
                                                                         Icon(
                                                                                         imageVector = Icons.Default.Favorite,
                                                                                         contentDescription = "Heart rate",
                                                                                         tint = Color.Red,
                                                                                         modifier = Modifier.size(16.dp)
                                                                         )
                                                                         Spacer(modifier = Modifier.width(6.dp))
                                                                         Text(
                                                                                         text = "${currentHeartRate ?: "--"} bpm",
                                                                                         fontSize = 16.sp,
                                                                                         fontWeight = FontWeight.Bold,
                                                                                         color = headingColor
                                                                          )
                                                                 }
                                                         }
                                                         Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                 TextButton(onClick = onDisconnectConnectedDevice) {
                                                                         Text("Disconnect", color = accentColor)
                                                                 }
                                                                TextButton(onClick = { onForgetDevice(connectedDevice.address) }) {
                                                                        Text("Forget", color = accentColor)
                                                                }
                                                        }
                                                }
                                        }
                                                }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = bodyColor.copy(alpha = 0.25f))

                                        Card(
                                                        backgroundColor = Color(0xFF252525),
                                                        modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                        SectionHeader("Discovered")
                                        if (discoveredDevices.isEmpty()) {
                                                Text(
                                                                text = if (isScanning) "Scanning..." else "None",
                                                                color = bodyColor
                                                )
                                        } else {
                                                discoveredDevices.forEach { device ->
                                                        HeartRateDeviceRow(
                                                                        device = device,
                                                                        actionLabel = "Connect",
                                                                        onAction = { onConnectDevice(device) },
                                                                        titleColor = headingColor,
                                                                        subtitleColor = bodyColor,
                                                                        actionColor = accentColor
                                                        )
                                                }
                                        }
                                                }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = bodyColor.copy(alpha = 0.25f))

                                        Card(
                                                        backgroundColor = Color(0xFF252525),
                                                        modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                        SectionHeader("Saved")
                                        val filteredSaved = savedDevices.filter { it.address != connectedDevice?.address }
                                        if (filteredSaved.isEmpty()) {
                                                Text("None", color = bodyColor)
                                        } else {
                                                filteredSaved.forEach { device ->
                                                        Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                                        text = device.name ?: "Unknown",
                                                                                        fontSize = 16.sp,
                                                                                        color = headingColor
                                                                        )
                                                                        Text(
                                                                                        text = device.address,
                                                                                        fontSize = 16.sp,
                                                                                        color = bodyColor
                                                                        )
                                                                }
                                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        TextButton(onClick = { onConnectDevice(device) }) {
                                                                                Text("Connect", color = accentColor)
                                                                        }
                                                                        TextButton(onClick = { onForgetDevice(device.address) }) {
                                                                                Text("Forget", color = accentColor)
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                                }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = bodyColor.copy(alpha = 0.25f))

                                        Card(
                                                        backgroundColor = Color(0xFF252525),
                                                        modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                        SectionHeader("Connection Options")
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                        Text("Match by name only", fontSize = 16.sp, color = headingColor)
                                                                        Text(
                                                                                "Reconnect using device name instead of MAC address. Useful when the MAC changes.",
                                                                                fontSize = 13.sp,
                                                                                color = bodyColor
                                                                        )
                                                                }
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Switch(
                                                                        checked = matchByName,
                                                                        onCheckedChange = onSetMatchByName,
                                                                        colors = SwitchDefaults.colors(
                                                                                checkedThumbColor = Color(0xFF22C55E),
                                                                                checkedTrackColor = Color(0xFF22C55E)
                                                                        )
                                                                )
                                                        }
                                                }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = bodyColor.copy(alpha = 0.25f))

                                        Card(
                                                        backgroundColor = Color(0xFF252525),
                                                        modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                        SectionHeader("Heart Rate Zone Transitions")
                                        Row(
                                                modifier = Modifier.wrapContentWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Zone 1-2", fontSize = 16.sp, color = bodyColor)
                                                        TextField(
                                                                value = zone12.value,
                                                                onValueChange = {
                                                                        zone12.value = it.filter { ch -> ch.isDigit() }
                                                                        updateZones()
                                                                },
                                                                modifier = Modifier.width(72.dp),
                                                                placeholder = { Text("bpm") },
                                                                singleLine = true
                                                        )
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Zone 2-3", fontSize = 16.sp, color = bodyColor)
                                                        TextField(
                                                                value = zone23.value,
                                                                onValueChange = {
                                                                        zone23.value = it.filter { ch -> ch.isDigit() }
                                                                        updateZones()
                                                                },
                                                                modifier = Modifier.width(72.dp),
                                                                placeholder = { Text("bpm") },
                                                                singleLine = true
                                                        )
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Zone 3-4", fontSize = 16.sp, color = bodyColor)
                                                        TextField(
                                                                value = zone34.value,
                                                                onValueChange = {
                                                                        zone34.value = it.filter { ch -> ch.isDigit() }
                                                                        updateZones()
                                                                },
                                                                modifier = Modifier.width(72.dp),
                                                                placeholder = { Text("bpm") },
                                                                singleLine = true
                                                        )
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Zone 4-5", fontSize = 16.sp, color = bodyColor)
                                                        TextField(
                                                                value = zone45.value,
                                                                onValueChange = {
                                                                        zone45.value = it.filter { ch -> ch.isDigit() }
                                                                        updateZones()
                                                                },
                                                                modifier = Modifier.width(72.dp),
                                                                placeholder = { Text("bpm") },
                                                                singleLine = true
                                                        )
                                                }
                                        }
                                                }
                                        }
                                }
                        },
                        confirmButton = {
                                Button(
                                                onClick = onDismiss,
                                                colors = ButtonDefaults.buttonColors(backgroundColor = accentColor)
                                ) {
                                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                        }
        )
        }
}

@Composable
private fun SectionHeader(title: String) {
        Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFF2F2F2)
        )
        Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun HeartRateDeviceRow(
                device: HeartRateDevice,
                actionLabel: String,
                onAction: () -> Unit,
                titleColor: Color = Color.White,
                subtitleColor: Color = Color(0xFFD0D0D0),
                actionColor: Color = Color(0xFF4DA3FF)
) {
        Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Column(modifier = Modifier.weight(1f)) {
                        Text(text = device.name ?: "Unknown", fontSize = 16.sp, color = titleColor)
                        Text(
                                        text = device.address,
                                        fontSize = 16.sp,
                                        color = subtitleColor
                        )
                }
                TextButton(onClick = onAction) {
                        Text(actionLabel, color = actionColor)
                }
        }
}
