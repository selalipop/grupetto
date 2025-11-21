package com.spop.poverlay

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.releases.Release
import com.spop.poverlay.ui.theme.ErrorColor
import com.spop.poverlay.ui.theme.LatoFontFamily

@Composable
fun ConfigurationPage(viewModel: ConfigurationViewModel) {
    val showPermissionInfo by remember { viewModel.showPermissionInfo }
    val latestRelease by remember { viewModel.latestRelease }

    Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        if (showPermissionInfo) {
            PermissionPage(viewModel::onGrantPermissionClicked)
        } else {
            val timerShownWhenMinimized by
                    viewModel.showTimerWhenMinimized.collectAsStateWithLifecycle(
                            initialValue = true
                    )
            val bleTxEnabled by
                    viewModel.bleTxEnabled.collectAsStateWithLifecycle(initialValue = false)
            val bleFtmsDeviceName by
                    viewModel.bleFtmsDeviceName.collectAsStateWithLifecycle(
                            initialValue = "Grupetto FTMS"
                    )
            StartServicePage(
                    timerShownWhenMinimized,
                    viewModel::onShowTimerWhenMinimizedClicked,
                    bleTxEnabled,
                    viewModel::onBleTxEnabledClicked,
                    bleFtmsDeviceName,
                    viewModel::onStartServiceClicked,
                    viewModel::onRestartClicked,
                    viewModel::onClickedRelease,
                    latestRelease
            )
        }
    }
}

@Composable
private fun StartServicePage(
        timerShownWhenMinimized: Boolean,
        onTimerShownWhenMinimizedToggled: (Boolean) -> Unit,
        bleTxEnabled: Boolean,
        onBleTxEnabledToggled: (Boolean) -> Unit,
        bleFtmsDeviceName: String,
        onClickedStartOverlay: () -> Unit,
        onClickedRestartApp: () -> Unit,
        onClickedRelease: (Release) -> Unit,
        latestRelease: Release?
) {
    Text(
            text = "Grupetto: An overlay for your Peloton bike",
            fontSize = 50.sp,
            fontWeight = FontWeight.Bold
    )
    Text(
            text = "Note: Not endorsed with, associated with, or supported by Peloton",
            fontSize = 25.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(50.dp))
    Button(
            onClick = onClickedStartOverlay,
    ) {
        Text(
                text = "Click here to start the overlay",
                fontSize = 30.sp,
                fontFamily = LatoFontFamily,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Show timer when the overlay minimized?", fontSize = 20.sp)
        Checkbox(
                checked = timerShownWhenMinimized,
                onCheckedChange = onTimerShownWhenMinimizedToggled
        )
    }
    // BLE FTMS Settings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
                text = "Enable BLE TX (Transmission)?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
        )
        Checkbox(checked = bleTxEnabled, onCheckedChange = onBleTxEnabledToggled)
    }

    if (bleTxEnabled) {
        Row {
            Text(text = "BLE TX is enabled and running!", fontSize = 14.sp, color = Color.Green)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                    text = " Look for '$bleFtmsDeviceName' in your fitness app's device list",
                    fontSize = 14.sp
            )
        }
    } else {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = "💡 Enable to broadcast bike data to apps like Zwift, TrainerRoad, etc.",
                fontSize = 14.sp,
                color = Color.Gray
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (latestRelease == null) {
        Text(text = "Couldn't check for updates")
    } else {
        val formattedDate = DateUtils.getRelativeTimeSpanString(latestRelease.createdAt.time)
        val releaseText =
                if (latestRelease.isCurrentlyInstalled) {
                    buildAnnotatedString {
                        "Grupetto is up to date: ${latestRelease.tagName} • $formattedDate • ${latestRelease.friendlyName}"
                    }
                } else {
                    buildAnnotatedString {
                        append("⭐ ")
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(
                                    "New version released $formattedDate: ${latestRelease.friendlyName}."
                            )
                        }
                    }
                }
        ClickableText(
                text = releaseText,
                style =
                        LocalTextStyle.current.copy(
                                fontSize = 20.sp,
                                color = LocalContentColor.current
                        )
        ) { onClickedRelease(latestRelease) }
    }

    Spacer(modifier = Modifier.height(40.dp))
    Button(
            onClick = onClickedRestartApp,
            colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
    ) {
        Text(
                text = "Restart Grupetto",
                fontSize = 20.sp,
                fontStyle = FontStyle.Italic,
                color = Color.White
        )
    }
    Spacer(modifier = Modifier.height(10.dp))

    Text(
            "Device: ${Build.DEVICE}\t" +
                    "SDK: ${Build.VERSION.RELEASE}\t" +
                    "OS Version: ${Build.FINGERPRINT}\t",
            color = LocalContentColor.current.copy(alpha = .5f)
    )
}

@Composable
private fun PermissionPage(onClickedGrantPermission: () -> Unit) {
    Text(
            text = "Grupetto Needs Permission To Draw Over Other Apps",
            fontSize = 40.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold
    )
    Text(
            text = "It uses this permission to draw an overlay with your bike's sensor data",
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(onClick = onClickedGrantPermission) { Text(text = "Grant Permission") }
}
