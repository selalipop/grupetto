package com.spop.poverlay

import android.content.res.Configuration
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
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
    }
}

@Composable
fun ConfigurationPage(viewModel: ConfigurationViewModel = viewModel()) {
    Box {
        Button(
            modifier = Modifier.align(Alignment.Center),
            onClick = {
            viewModel.onStartService()
        }) {
            Text(text = "Start Overlay")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PTONOverlayTheme {
        Configuration()
    }
}