package com.spop.poverlay

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.releases.Release
import com.spop.poverlay.ui.theme.ErrorColor
import com.spop.poverlay.ui.theme.LatoFontFamily


@Composable
fun ConfigurationPage(
    viewModel: ConfigurationViewModel
) {
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
            val timerShownWhenMinimized by viewModel.showTimerWhenMinimized
                .collectAsStateWithLifecycle(
                    initialValue = true
                )
            StartServicePage(
                timerShownWhenMinimized,
                viewModel::onShowTimerWhenMinimizedClicked,
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
    onClickedStartOverlay: () -> Unit,
    onClickedRestartApp: () -> Unit,
    onClickedRelease: (Release) -> Unit,
    latestRelease: Release?
) {
    Text(
        text = "Grupetto: An overlay for your Peloton bike",
        fontFamily = LatoFontFamily,
        fontSize = 50.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Note: Not endorsed with, associated with, or supported by Peloton",
        fontFamily = LatoFontFamily,
        fontSize = 25.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(100.dp))
    Button(
        onClick = onClickedStartOverlay
    ) {
        Text(
            text = "Click here to start the overlay",
            fontFamily = LatoFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
    }
    Spacer(modifier = Modifier.height(40.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Show Elapsed Time When Minimized?",
            fontFamily = LatoFontFamily,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
        )
        Checkbox(
            checked = timerShownWhenMinimized,
            onCheckedChange = onTimerShownWhenMinimizedToggled
        )
    }


    Spacer(modifier = Modifier.height(100.dp))

    if (latestRelease == null) {
        Text(text = "Couldn't load releases")
    } else {
        val formattedDate = DateUtils.getRelativeTimeSpanString(latestRelease.createdAt.time)
        val releaseName =
            "${latestRelease.tagName} — $formattedDate — ${latestRelease.friendlyName}"
        val text = if (latestRelease.isCurrentlyInstalled) {
            "Grupetto is up to date: $releaseName"
        } else {
            "New release found, click here to open: $releaseName"
        }
        val textColor = if (latestRelease.isCurrentlyInstalled) {
            Color(0.09f, 0.133f, 0.388f, 1.0f)
        } else {
            Color(0.902f, 0.467f, 0.0f, 1.0f)
        }

        ClickableText(
            text = AnnotatedString(text),
            style = TextStyle.Default.copy(color = textColor, fontSize = 20.sp)
        ) {
            onClickedRelease(latestRelease)
        }
    }

    Spacer(modifier = Modifier.height(40.dp))
    Button(
        onClick = onClickedRestartApp,
        colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
    ) {
        Text(
            text = "Restart Grupetto",
            fontFamily = LatoFontFamily,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun PermissionPage(onClickedGrantPermission: () -> Unit) {
    Text(
        text = "Grupetto Needs Permission To Draw Over Other Apps",
        fontFamily = LatoFontFamily,
        fontSize = 40.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "It uses this permission to draw an overlay with your bike's sensor data",
        fontFamily = LatoFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.Normal
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onClickedGrantPermission
    ) {
        Text(text = "Grant Permission")
    }
}