package com.spop.poverlay.dircon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import timber.log.Timber

class DirConMdnsAdvertiser(private val context: Context) {
    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start(port: Int, serviceUuids: List<String>, serialNumber: String) {
        stop()
        val identity = serialNumber.toMdnsIdentity()
        val bleServiceUuidList = serviceUuids.joinToString(",") { it.toDirConTxtUuid() }
        val instanceName = DirConConstants.MdnsServiceName
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = instanceName
            serviceType = DirConConstants.MdnsServiceType
            this.port = port
            setAttribute("ble-service-uuids", bleServiceUuidList)
            setAttribute("serial-number", identity.serialNumber)
            setAttribute("mac-address", identity.macAddress)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredServiceInfo: NsdServiceInfo) {
                Timber.i(
                    "DIRCON mDNS registered as ${registeredServiceInfo.serviceName}." +
                        "${registeredServiceInfo.serviceType} mac=${identity.macAddress} " +
                        "serial=${identity.serialNumber} services=$bleServiceUuidList"
                )
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.w("DIRCON mDNS registration failed: $errorCode")
                registrationListener = null
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Timber.i("DIRCON mDNS unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.w("DIRCON mDNS unregistration failed: $errorCode")
            }
        }

        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        Timber.i(
            "DIRCON mDNS registering as $instanceName.${DirConConstants.MdnsServiceType} " +
                "mac=${identity.macAddress} serial=${identity.serialNumber} services=$bleServiceUuidList"
        )
    }

    fun stop() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
                .onFailure { Timber.d(it, "DIRCON mDNS unregister skipped") }
        }
        registrationListener = null
    }
}

private data class MdnsIdentity(val macAddress: String, val serialNumber: String)

private fun String.toMdnsIdentity(): MdnsIdentity {
    val macBytes = hashCode().let { hash ->
        byteArrayOf(
            0x02, // Locally administered, unicast address.
            0x47, // "G" for Grupetto.
            ((hash ushr 24) and 0xFF).toByte(),
            ((hash ushr 16) and 0xFF).toByte(),
            ((hash ushr 8) and 0xFF).toByte(),
            (hash and 0xFF).toByte()
        )
    }
    val octets = macBytes.map { "%02X".format(it.toInt() and 0xFF) }
    val macAddress = octets.joinToString("-")
    return MdnsIdentity(
        macAddress = macAddress,
        serialNumber = macAddress.toSmartSpinStyleSerialNumber()
    )
}

private fun String.toSmartSpinStyleSerialNumber(): String {
    val sourceIndexes = intArrayOf(0, 1, 3, 4, 6, 7)
    return sourceIndexes.joinToString("") { index ->
        "%02X".format(this[index].code)
    }
}

private fun String.toDirConTxtUuid(): String {
    val normalized = removePrefix("0x").removePrefix("0X").lowercase()
    return if (normalized.length == 4) {
        "0x$normalized"
    } else {
        normalized
    }
}
