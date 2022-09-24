package com.spop.poverlay

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.ui.theme.LatoFontFamily


@Composable
fun ConfigurationPage(
    viewModel: ConfigurationViewModel
) {
    val showPermissionInfo by remember { viewModel.showPermissionInfo }

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
                viewModel::onStartServiceClicked
            )
        }
    }
}

@Composable
private fun StartServicePage(
    timerShownWhenMinimized: Boolean,
    onTimerShownWhenMinimizedToggled: (Boolean) -> Unit,
    onClickedStartOverlay: () -> Unit
) {
    Text(
        text = "Grupetto: An overlay for your Peloton bike",
        fontFamily = LatoFontFamily,
        fontSize = 50.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Note: Not endorsed with, associated with, or supported by Peloton",
        fontFamily = LatoFontFamily,
        fontSize = 25.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onClickedStartOverlay
    ) {
        Text(
            text = "Click here to start the overlay",
            fontFamily = LatoFontFamily,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
    Row (verticalAlignment = Alignment.CenterVertically){
        Text(
            text = "Show Elapsed Time When Minimized?",
            fontFamily = LatoFontFamily,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
        )
        Checkbox(checked = timerShownWhenMinimized, onCheckedChange = onTimerShownWhenMinimizedToggled)
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