@file:OptIn(FlowPreview::class, ExperimentalUnsignedTypes::class)

package com.spop.poverlay.sensor

import kotlinx.coroutines.FlowPreview


enum class SensorBundleKey(val bundleKey: String) {
    HexResponse("responseHexString"),
    RequestId("requestId")
}

