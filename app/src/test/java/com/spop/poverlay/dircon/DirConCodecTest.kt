package com.spop.poverlay.dircon

import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DirConCodecTest {
    @Test
    fun encodesDiscoverServicesResponseWithBigEndianUuidBytes() {
        val uuid = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val encoded = DirConCodec.encode(
            DirConMessage(
                identifier = DirConConstants.MessageDiscoverServices,
                sequenceNumber = 7,
                uuids = listOf(uuid)
            )
        )

        assertArrayEquals(
            byteArrayOf(
                0x01, 0x01, 0x07, 0x00, 0x00, 0x10,
                0x00, 0x00, 0x18, 0x26, 0x00, 0x00, 0x10, 0x00,
                0x80.toByte(), 0x00, 0x00, 0x80.toByte(), 0x5f, 0x9b.toByte(),
                0x34, 0xfb.toByte()
            ),
            encoded
        )
    }

    @Test
    fun decodesMultipleFramesFromBufferedPayload() {
        val firstUuid = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val secondUuid = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        val first = DirConCodec.encode(
            DirConMessage(
                identifier = DirConConstants.MessageDiscoverCharacteristics,
                sequenceNumber = 1,
                uuid = firstUuid
            )
        )
        val second = DirConCodec.encode(
            DirConMessage(
                identifier = DirConConstants.MessageReadCharacteristic,
                sequenceNumber = 2,
                uuid = secondUuid
            )
        )
        val combined = first + second

        val decodedFirst = DirConCodec.decode(combined)!!
        val decodedSecond = DirConCodec.decode(
            combined,
            decodedFirst.bytesConsumed,
            combined.size - decodedFirst.bytesConsumed
        )!!

        assertEquals(firstUuid, decodedFirst.message.uuid)
        assertEquals(secondUuid, decodedSecond.message.uuid)
        assertEquals(first.size, decodedFirst.bytesConsumed)
        assertEquals(second.size, decodedSecond.bytesConsumed)
    }
}
