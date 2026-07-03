package com.spop.poverlay.dircon

import java.nio.ByteBuffer
import java.util.UUID

data class DirConMessage(
    val version: Int = DirConConstants.ProtocolVersion,
    val identifier: Int,
    val sequenceNumber: Int,
    val responseCode: Int = DirConConstants.ResponseSuccess,
    val uuid: UUID? = null,
    val uuids: List<UUID> = emptyList(),
    val properties: List<Int> = emptyList(),
    val data: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DirConMessage) return false
        return version == other.version &&
            identifier == other.identifier &&
            sequenceNumber == other.sequenceNumber &&
            responseCode == other.responseCode &&
            uuid == other.uuid &&
            uuids == other.uuids &&
            properties == other.properties &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + identifier
        result = 31 * result + sequenceNumber
        result = 31 * result + responseCode
        result = 31 * result + (uuid?.hashCode() ?: 0)
        result = 31 * result + uuids.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

object DirConCodec {
    fun encode(message: DirConMessage): ByteArray {
        val payload = payloadFor(message)
        return ByteArray(DirConConstants.HeaderLength + payload.size).also { out ->
            out[0] = message.version.toByte()
            out[1] = message.identifier.toByte()
            out[2] = message.sequenceNumber.toByte()
            out[3] = message.responseCode.toByte()
            out[4] = ((payload.size ushr 8) and 0xFF).toByte()
            out[5] = (payload.size and 0xFF).toByte()
            payload.copyInto(out, DirConConstants.HeaderLength)
        }
    }

    fun decode(buffer: ByteArray, offset: Int = 0, available: Int = buffer.size - offset): DecodeResult? {
        if (available < DirConConstants.HeaderLength) return null

        val version = buffer[offset].toInt() and 0xFF
        val identifier = buffer[offset + 1].toInt() and 0xFF
        val sequence = buffer[offset + 2].toInt() and 0xFF
        val response = buffer[offset + 3].toInt() and 0xFF
        val length = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or
            (buffer[offset + 5].toInt() and 0xFF)

        val totalLength = DirConConstants.HeaderLength + length
        if (available < totalLength) return null

        val payloadOffset = offset + DirConConstants.HeaderLength
        val payloadEnd = payloadOffset + length
        var cursor = payloadOffset
        var uuid: UUID? = null
        val uuids = mutableListOf<UUID>()
        val properties = mutableListOf<Int>()
        var data = ByteArray(0)

        when (identifier) {
            DirConConstants.MessageDiscoverServices -> {
                require(length % UuidLength == 0) { "Invalid service discovery payload length" }
                while (cursor < payloadEnd) {
                    uuids.add(readUuid(buffer, cursor))
                    cursor += UuidLength
                }
            }
            DirConConstants.MessageDiscoverCharacteristics -> {
                if (length >= UuidLength) {
                    uuid = readUuid(buffer, cursor)
                    cursor += UuidLength
                    require((payloadEnd - cursor) % (UuidLength + 1) == 0) {
                        "Invalid characteristic discovery payload length"
                    }
                    while (cursor < payloadEnd) {
                        uuids.add(readUuid(buffer, cursor))
                        cursor += UuidLength
                        properties.add(buffer[cursor].toInt() and 0xFF)
                        cursor += 1
                    }
                }
            }
            DirConConstants.MessageReadCharacteristic,
            DirConConstants.MessageWriteCharacteristic,
            DirConConstants.MessageEnableCharacteristicNotifications,
            DirConConstants.MessageUnsolicitedCharacteristicNotification -> {
                if (length >= UuidLength) {
                    uuid = readUuid(buffer, cursor)
                    cursor += UuidLength
                    data = buffer.copyOfRange(cursor, payloadEnd)
                }
            }
        }

        return DecodeResult(
            message = DirConMessage(
                version = version,
                identifier = identifier,
                sequenceNumber = sequence,
                responseCode = response,
                uuid = uuid,
                uuids = uuids,
                properties = properties,
                data = data
            ),
            bytesConsumed = totalLength
        )
    }

    private fun payloadFor(message: DirConMessage): ByteArray {
        if (message.responseCode != DirConConstants.ResponseSuccess) {
            return ByteArray(0)
        }

        return when (message.identifier) {
            DirConConstants.MessageDiscoverServices -> {
                ByteArray(message.uuids.size * UuidLength).also { out ->
                    message.uuids.forEachIndexed { index, uuid ->
                        writeUuid(uuid, out, index * UuidLength)
                    }
                }
            }
            DirConConstants.MessageDiscoverCharacteristics -> {
                val serviceUuid = message.uuid ?: return ByteArray(0)
                ByteArray(UuidLength + message.uuids.size * (UuidLength + 1)).also { out ->
                    writeUuid(serviceUuid, out, 0)
                    var cursor = UuidLength
                    message.uuids.forEachIndexed { index, uuid ->
                        writeUuid(uuid, out, cursor)
                        cursor += UuidLength
                        out[cursor] = (message.properties.getOrElse(index) { 0 } and 0xFF).toByte()
                        cursor += 1
                    }
                }
            }
            DirConConstants.MessageReadCharacteristic,
            DirConConstants.MessageWriteCharacteristic,
            DirConConstants.MessageEnableCharacteristicNotifications,
            DirConConstants.MessageUnsolicitedCharacteristicNotification -> {
                val characteristicUuid = message.uuid ?: return ByteArray(0)
                ByteArray(UuidLength + message.data.size).also { out ->
                    writeUuid(characteristicUuid, out, 0)
                    message.data.copyInto(out, UuidLength)
                }
            }
            else -> ByteArray(0)
        }
    }

    private fun readUuid(bytes: ByteArray, offset: Int): UUID {
        val buffer = ByteBuffer.wrap(bytes, offset, UuidLength)
        return UUID(buffer.long, buffer.long)
    }

    private fun writeUuid(uuid: UUID, bytes: ByteArray, offset: Int) {
        val buffer = ByteBuffer.wrap(bytes, offset, UuidLength)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
    }

    data class DecodeResult(val message: DirConMessage, val bytesConsumed: Int)

    private const val UuidLength = 16
}
