package com.spop.poverlay.dircon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import timber.log.Timber

class DirConMdnsAdvertiser(private val context: Context) {
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start(port: Int, serviceUuids: List<String>, serialNumber: String) {
        stop()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${DirConConstants.MdnsServiceName}-$serialNumber"
            serviceType = DirConConstants.MdnsServiceType
            setPort(port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("mac-address", serialNumber.toSyntheticMacAddress())
                setAttribute("serial-number", serialNumber)
                setAttribute("ble-service-uuids", serviceUuids.joinToString(","))
            }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Timber.i("DIRCON mDNS registered as ${info.serviceName}.${info.serviceType}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Timber.e("DIRCON mDNS registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Timber.i("DIRCON mDNS unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Timber.e("DIRCON mDNS unregistration failed: $errorCode")
            }
        }

        registrationListener = listener
        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { nsdManager?.unregisterService(listener) }
            .onFailure { Timber.d(it, "DIRCON mDNS was already unregistered") }
    }
}

private fun String.toSyntheticMacAddress(): String {
    val bytes = hashCode().let { hash ->
        byteArrayOf(
            0x02,
            0x47,
            ((hash ushr 24) and 0xFF).toByte(),
            ((hash ushr 16) and 0xFF).toByte(),
            ((hash ushr 8) and 0xFF).toByte(),
            (hash and 0xFF).toByte()
        )
    }
    return bytes.joinToString("-") { "%02X".format(it.toInt() and 0xFF) }
}
