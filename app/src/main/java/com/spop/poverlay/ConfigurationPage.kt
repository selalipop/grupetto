package com.spop.poverlay

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.ui.theme.LatoFontFamily


@Composable
fun ConfigurationPage(viewModel: ConfigurationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val showPermission = remember { viewModel.showPermissionInfo }
        if (showPermission.value) {
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
                onClick = viewModel::onGrantPermissionClicked
            ) {
                Text(text = "Grant Permission")
            }
        } else {

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
            Button(
                onClick = {
                    viewModel.onStartService()
                }) {
                Text(text = "Start Overlay")
            }
        }
    }
}