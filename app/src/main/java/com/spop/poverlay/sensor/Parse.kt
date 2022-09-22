package com.spop.poverlay.sensor

import timber.log.Timber
import java.util.regex.Pattern



val v1ResponseSplitRegex: Pattern = Pattern.compile(" ")
const val WattCommandId = 68
const val v1HeaderSize = 3

fun hexResponseToBytes(response: String): ByteArray? {
    if (response.isEmpty()) {
        return null
    }
    val split = v1ResponseSplitRegex.split(response, 0)
    return split.map { character ->
        character.toIntOrNull(16)?.toByte() ?: return null
    }.toByteArray()
}

fun parseResponseV1(response : String): Float? {
    val byteData = hexResponseToBytes(response) ?: return null
    if (byteData.size < 3) {
        Timber.v("failed to parse V1 response, too short")
        return null
    }
    val commandId = byteData[1].toInt()
    val payloadSize = byteData[2].toInt()

    if (commandId < 0 || payloadSize < 1) {
        Timber.v("failed to parse V1 response",
            byteData.joinToString(",") { it.toString(16) })
        return null
    }

    //Watt command is the only decimal formatted command
    val isDecimal = commandId == WattCommandId
    return parseV1Bytes(byteData, payloadSize, isDecimal)
}

fun parseV1Bytes(
    data: ByteArray,
    payloadSize: Int,
    isDecimal: Boolean,
    headerOffset: Int = v1HeaderSize
): Float? {
    val lastDataIndex = payloadSize + headerOffset
    var floatValue = 0.0f
    if (data.size < lastDataIndex) {
        return null
    }
    var intValue = 0
    var valueMultiplier = 1
    for (currentByte in headerOffset until lastDataIndex) {
        val digit = data[currentByte] - 48
        if (digit < 0 || digit > 9) {
            return null
        }
        if (!isDecimal || currentByte != headerOffset) {
            valueMultiplier *= 10
            intValue += digit * valueMultiplier
        } else {
            floatValue = valueMultiplier.toFloat() * digit.toFloat() / 10.0f
        }
    }
    // Even if the value is not a float, floatValue is used
    // as it is always contains the first byte of data
    return intValue.toFloat() + floatValue
}
