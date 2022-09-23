package com.spop.poverlay

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.ui.theme.PTONOverlayTheme
import timber.log.Timber
import timber.log.Timber.DebugTree


class MainActivity : ComponentActivity() {
    private val viewModel: ConfigurationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(DebugTree())

        viewModel.finishActivity.observe(this) {
            finish()
        }
        viewModel.requestOverlayPermission.observe(this) {
            requestScreenPermission()
        }
        setContent {
            PTONOverlayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConfigurationPage(viewModel)
                }
            }
        }
        lifecycleScope.launchWhenResumed {
            viewModel.onResume()
        }
    }

    private val overlayPermissionRequest =
        registerForActivityResult(StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= 23) {
                viewModel.onOverlayPermissionRequestCompleted(
                    Settings.canDrawOverlays(this)
                )
            }
        }

    private fun requestScreenPermission() = Intent(
        "android.settings.action.MANAGE_OVERLAY_PERMISSION",
        Uri.parse("package:${packageName}")
    ).apply {
        overlayPermissionRequest.launch(this)
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PTONOverlayTheme {
        Configuration()
    }
}