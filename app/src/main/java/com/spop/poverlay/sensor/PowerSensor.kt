package com.spop.poverlay.sensor

import android.os.IBinder
import timber.log.Timber

/**
 * At low power values the sensor can sometimes send huge spikes in values returned
 *
 * These spikes are referred to as "Spurious Readings" in this implementation, and rejected
 */
class PowerSensor(binder: IBinder) : RepeatingFloatV1Sensor(Command.GetPowerRepeating, binder) {
    companion object {
        // Spurious readings tend to happen at low power values,
        // so only we only reject values when power is below
        private const val SpuriousReadingThreshold = 40

        // How large of an increase between two reported values
        // is needed before a reading is rejected
        private const val SpuriousReadingDelta = 100

        // Max number of consecutive readings that will be rejected
        // Accounts for cases where sensor data really *is* spiking up and down
        private const val SpuriousReadingMaxRejections = 30
    }

    var lastReading = 0f
    var consecutiveRejections = 0

    override fun mapFloat(value: Float): Float {
        val currentReading = value / 10f
        val isRejected =
            if (lastReading > SpuriousReadingThreshold ||
                consecutiveRejections > SpuriousReadingMaxRejections) {
                false
            } else {
                currentReading - lastReading > SpuriousReadingDelta
            }

        return if (isRejected) {
            consecutiveRejections++
            Timber.w("Ignoring spurious sensor data #$consecutiveRejections, $currentReading")
            lastReading
        } else {
            consecutiveRejections = 0
            lastReading = currentReading
            currentReading
        }
    }
}