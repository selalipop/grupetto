package com.spop.poverlay

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.ui.theme.LatoFontFamily


@Composable
fun ConfigurationPage(
    showPermissionInfoState: State<Boolean>,
    onClickedGrantPermission: () -> Unit,
    onClickedStartOverlay: () -> Unit,
) {
    val showPermissionInfo by remember { showPermissionInfoState }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showPermissionInfo) {
            PermissionPage(onClickedGrantPermission)
        } else {
            StartServicePage(onClickedStartOverlay)
        }
    }
}

@Composable
private fun StartServicePage(onClickedStartOverlay: () -> Unit) {
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
        fontSize = 20.sp,
        fontStyle = FontStyle.Italic,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onClickedStartOverlay
    ) {
        Text(text = "Click here to start the overlay")
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