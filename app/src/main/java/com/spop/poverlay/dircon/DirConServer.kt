package com.spop.poverlay.dircon

import android.content.Context
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class DirConServer(
    context: Context,
    private val bridge: DirConGattBridge,
    private val serialNumberProvider: () -> String,
    private val port: Int = DirConConstants.TcpPort
) : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    private val mdnsAdvertiser = DirConMdnsAdvertiser(context.applicationContext)
    private val clients = Collections.synchronizedSet(mutableSetOf<ClientSession>())
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (acceptJob != null) return

        val serviceUuids = bridge.services().map { it.uuid.toShortUuidString() }
        mdnsAdvertiser.start(port, serviceUuids, serialNumberProvider())

        acceptJob = launch {
            runCatching {
                ServerSocket(port).use { socket ->
                    serverSocket = socket
                    Timber.i("DIRCON TCP server listening on port $port")
                    while (isActive) {
                        val client = socket.accept()
                        ClientSession(client).also { session ->
                            clients.add(session)
                            launch { session.run() }
                        }
                    }
                }
            }.onFailure { error ->
                if (error !is SocketException || isActive) {
                    Timber.e(error, "DIRCON TCP server stopped unexpectedly")
                }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null

        synchronized(clients) {
            clients.forEach { it.close() }
            clients.clear()
        }
        mdnsAdvertiser.stop()
        coroutineContext.cancelChildren()
    }

    fun notifyCharacteristicChanged(uuid: UUID, value: ByteArray) {
        val message = DirConMessage(
            identifier = DirConConstants.MessageUnsolicitedCharacteristicNotification,
            sequenceNumber = 0,
            uuid = uuid,
            data = value
        )
        val encoded = DirConCodec.encode(message)

        synchronized(clients) {
            clients
                .filter { it.isSubscribed(uuid) }
                .forEach { it.write(encoded) }
        }
    }

    private fun handle(message: DirConMessage, session: ClientSession): DirConMessage {
        if (message.version != DirConConstants.ProtocolVersion) {
            return errorResponse(message, DirConConstants.ResponseUnknownProtocol)
        }

        return when (message.identifier) {
            DirConConstants.MessageDiscoverServices -> {
                DirConMessage(
                    identifier = message.identifier,
                    sequenceNumber = message.sequenceNumber,
                    uuids = bridge.services().map { it.uuid }
                )
            }
            DirConConstants.MessageDiscoverCharacteristics -> {
                val serviceUuid = message.uuid
                    ?: return errorResponse(message, DirConConstants.ResponseServiceNotFound)
                val service = bridge.services().firstOrNull { it.uuid == serviceUuid }
                    ?: return errorResponse(message, DirConConstants.ResponseServiceNotFound)
                DirConMessage(
                    identifier = message.identifier,
                    sequenceNumber = message.sequenceNumber,
                    uuid = service.uuid,
                    uuids = service.characteristics.map { it.uuid },
                    properties = service.characteristics.map { it.properties }
                )
            }
            DirConConstants.MessageReadCharacteristic -> {
                val characteristicUuid = message.uuid
                    ?: return errorResponse(message, DirConConstants.ResponseCharacteristicNotFound)
                val value = bridge.readCharacteristic(characteristicUuid)
                    ?: return errorResponse(message, DirConConstants.ResponseCharacteristicNotFound)
                DirConMessage(
                    identifier = message.identifier,
                    sequenceNumber = message.sequenceNumber,
                    uuid = characteristicUuid,
                    data = value
                )
            }
            DirConConstants.MessageWriteCharacteristic -> {
                val characteristicUuid = message.uuid
                    ?: return errorResponse(message, DirConConstants.ResponseCharacteristicNotFound)
                if (!bridge.writeCharacteristic(characteristicUuid, message.data)) {
                    return errorResponse(message, DirConConstants.ResponseCharacteristicWriteFailed)
                }
                DirConMessage(
                    identifier = message.identifier,
                    sequenceNumber = message.sequenceNumber,
                    uuid = characteristicUuid
                )
            }
            DirConConstants.MessageEnableCharacteristicNotifications -> {
                val characteristicUuid = message.uuid
                    ?: return errorResponse(message, DirConConstants.ResponseCharacteristicNotFound)
                val exists = bridge.services().any { service ->
                    service.characteristics.any { it.uuid == characteristicUuid }
                }
                if (!exists) {
                    return errorResponse(message, DirConConstants.ResponseCharacteristicNotFound)
                }

                val enabled = message.data.firstOrNull()?.toInt()?.and(0xFF) != 0
                if (enabled) session.subscribe(characteristicUuid) else session.unsubscribe(characteristicUuid)
                DirConMessage(
                    identifier = message.identifier,
                    sequenceNumber = message.sequenceNumber,
                    uuid = characteristicUuid
                )
            }
            else -> errorResponse(message, DirConConstants.ResponseUnknownMessageType)
        }
    }

    private fun errorResponse(message: DirConMessage, code: Int): DirConMessage =
        DirConMessage(
            identifier = message.identifier,
            sequenceNumber = message.sequenceNumber,
            responseCode = code
        )

    private inner class ClientSession(private val socket: Socket) {
        private val subscriptions = Collections.synchronizedSet(mutableSetOf<UUID>())

        fun run() {
            Timber.i("DIRCON client connected from ${socket.remoteSocketAddress}")
            try {
                val input = socket.getInputStream()
                val readBuffer = ByteArray(512)
                var pending = ByteArray(0)

                while (isActive && !socket.isClosed) {
                    val read = input.read(readBuffer)
                    if (read < 0) throw EOFException()
                    pending += readBuffer.copyOf(read)

                    var offset = 0
                    while (offset < pending.size) {
                        val decoded = runCatching {
                            DirConCodec.decode(pending, offset, pending.size - offset)
                        }.getOrElse { error ->
                            Timber.w(error, "Invalid DIRCON frame")
                            null
                        } ?: break

                        val response = handle(decoded.message, this)
                        write(DirConCodec.encode(response))
                        offset += decoded.bytesConsumed
                    }
                    pending = pending.copyOfRange(offset, pending.size)
                }
            } catch (_: EOFException) {
                Timber.i("DIRCON client disconnected")
            } catch (_: SocketException) {
                Timber.i("DIRCON client socket closed")
            } catch (error: Exception) {
                Timber.e(error, "DIRCON client failed")
            } finally {
                clients.remove(this)
                close()
            }
        }

        fun subscribe(uuid: UUID) {
            subscriptions.add(uuid)
        }

        fun unsubscribe(uuid: UUID) {
            subscriptions.remove(uuid)
        }

        fun isSubscribed(uuid: UUID): Boolean = subscriptions.contains(uuid)

        fun write(bytes: ByteArray) {
            runCatching {
                synchronized(socket) {
                    socket.getOutputStream().write(bytes)
                    socket.getOutputStream().flush()
                }
            }.onFailure { Timber.d(it, "Failed to write DIRCON frame") }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }
}

private fun UUID.toShortUuidString(): String {
    val text = toString()
    return if (text.endsWith("-0000-1000-8000-00805f9b34fb", ignoreCase = true)) {
        text.substring(4, 8).lowercase()
    } else {
        text.lowercase()
    }
}
