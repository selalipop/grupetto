package com.spop.poverlay.releases

import android.net.Uri
import com.spop.poverlay.BuildConfig
import java.util.*

data class Release(
    // Git Tag
    val tagName: String,
    // Release title in Github
    val friendlyName: String,
    val createdAt: Date,
    val url: Uri
) {
    val isCurrentlyInstalled: Boolean
        get() = tagName.equals(BuildConfig.VERSION_NAME, true) || (
                // Forgot to set version name for first release :(
                BuildConfig.VERSION_NAME == "1.0" && tagName.equals("v0.0.1", true)
                )
}