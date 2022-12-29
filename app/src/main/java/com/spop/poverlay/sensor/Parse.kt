package com.spop.poverlay.sensor

import timber.log.Timber


const val WattCommandId = 68
const val v1HeaderSize = 3

fun hexResponseToBytes(response: String): ByteArray? {
    if (response.isEmpty()) {
        return null
    }
    // Response is space delimited, base 16 ASCII values
    val splitResponse = response.split(" ")
    return splitResponse.map { character ->
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

    if (commandId < 0) {
        Timber.v("failed to parse V1 response, invalid command $commandId $response")
        return null
    }

    if (payloadSize < 1) {
        Timber.v("failed to parse V1 response, invalid payload size $payloadSize $response")
        return null
    }

    // Watt command is the only decimal formatted command
    val isDecimal = commandId == WattCommandId
    return parseV1Bytes(byteData, payloadSize, isDecimal)
}

/***
 * @param data the data sent by the sensor service
 * @param payloadSize the data sent by the sensor service
 * @param isDecimal true if this response comes from one of the sensors that uses Decimal formatting
 * @param headerSize the size of the header sent by the sensor
 */
fun parseV1Bytes(
    data: ByteArray,
    payloadSize: Int,
    isDecimal: Boolean,
    headerSize: Int = v1HeaderSize
): Float? {
    val expectedSize = payloadSize + headerSize
    var floatValue = 0.0f
    if (data.size < expectedSize) {
        return null
    }
    var intValue = 0
    var valueMultiplier = 1

    for (payloadByteIndex in 0 until payloadSize) {
        // Read starting from the end of the header
        val payloadByte = data[headerSize + payloadByteIndex]

        // Convert ASCII digit to integer by subtracting 48
        val digit = payloadByte - 48

        if (digit < 0 || digit > 9) {
            // Was not a valid ASCII digit
            return null
        }

        if (!isDecimal || payloadByteIndex == 0) {
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
