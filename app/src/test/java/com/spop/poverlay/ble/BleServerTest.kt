package com.spop.poverlay.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.spop.poverlay.sensor.interfaces.SensorInterface
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class BleServerTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var sensorInterface: SensorInterface
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var bleServer: BleServer

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        sensorInterface = mockk(relaxed = true)
        every { sensorInterface.power } returns flowOf(0f)
        every { sensorInterface.cadence } returns flowOf(0f)
        every { sensorInterface.resistance } returns flowOf(0f)
        timeProvider = FakeTimeProvider()
        // Initialize with default time 0
        timeProvider.currentTime = 0
        bleServer = BleServer(context, bluetoothManager, sensorInterface, timeProvider)
    }

    @Test
    fun `initial values are zero`() {
        assertEquals(0L, bleServer.cscCumulativeWheelRev)
        assertEquals(0, bleServer.cscLastWheelEvtTime)
        assertEquals(0, bleServer.cscCumulativeCrankRev)
        assertEquals(0, bleServer.cscLastCrankEvtTime)
    }

    @Test
    fun `updateWheelAndCrankRev updates wheel revs correctly`() {
        // speed 30km/h = 8.333 m/s. 
        // Wheel size 2.127m
        // RPM = (8.333 / 2.127) * 60 = 235.0728 RPM
        // Revs per second = 3.91788
        
        // Advance time by 1 second
        timeProvider.currentTime = 1000
        bleServer.updateWheelAndCrankRev(30f, 0f)
        
        // Expected revs: floor(3.91788) = 3
        assertEquals(3L, bleServer.cscCumulativeWheelRev)
        
        // Residual should be ~0.91788
        
        // Advance time by another 1 second
        timeProvider.currentTime = 2000
        bleServer.updateWheelAndCrankRev(30f, 0f)
        
        // Total elapsed 2s. Total revs should be 3.91788 * 2 = 7.83576
        // floor(7.83576) = 7.
        // Logic: 3 (existing) + floor(residual 0.91788 + new 3.91788) 
        // = 3 + floor(4.83576) = 3 + 4 = 7.
        assertEquals(7L, bleServer.cscCumulativeWheelRev)
    }

    @Test
    fun `updateWheelAndCrankRev updates crank revs correctly`() {
        // Cadence 90 RPM
        // Revs per second = 90 / 60 = 1.5
        
        // Advance 1s
        timeProvider.currentTime = 1000
        bleServer.updateWheelAndCrankRev(0f, 90f)
        
        // Expected: floor(1.5) = 1
        assertEquals(1, bleServer.cscCumulativeCrankRev)
        
        // Residual 0.5
        
        // Advance 1s
        timeProvider.currentTime = 2000
        bleServer.updateWheelAndCrankRev(0f, 90f)
        
        // New delta: 1.5. Total residual: 0.5 + 1.5 = 2.0.
        // Add 2. Total 1 + 2 = 3.
        assertEquals(3, bleServer.cscCumulativeCrankRev)
    }

    @Test
    fun `no updates when speed and cadence are zero`() {
        timeProvider.currentTime = 1000
        bleServer.updateWheelAndCrankRev(0f, 0f)
        assertEquals(0L, bleServer.cscCumulativeWheelRev)
        assertEquals(0, bleServer.cscCumulativeCrankRev)
    }

    @Test
    fun `updates event time correctly`() {
        // Cadence 60 RPM -> 1 rev per second.
        // Period = 1s = 1024 ticks (since unit is 1/1024s)
        
        timeProvider.currentTime = 1000
        bleServer.updateWheelAndCrankRev(0f, 60f)
        
        // 1 rev added.
        // Last event time should increase by Period * 1 = 1024.
        assertEquals(1024, bleServer.cscLastCrankEvtTime)
        
        // Advance another second
        timeProvider.currentTime = 2000
        bleServer.updateWheelAndCrankRev(0f, 60f)
        
        assertEquals(2048, bleServer.cscLastCrankEvtTime)
    }
    
    @Test
    fun `handle event time wrapping`() {
        // Force event time to near limit
        // We can't set it directly as it's private set, but we can fast forward
        // However, simulating 65536 worth of ticks takes a bit of simulated time.
        // 65536 ticks / 1024 ticks/s = 64 seconds at 60rpm.
        
        // Let's do 70 seconds at 60 RPM
        timeProvider.currentTime = 70_000
        bleServer.updateWheelAndCrankRev(0f, 60f)
        
        // 70 revs. 70 * 1024 = 71680 ticks.
        // Wrapped: 71680 % 65536 = 6144
        assertEquals(6144, bleServer.cscLastCrankEvtTime)
    }

    @Test
    fun `concurrent starts cannot overlap GATT server registrations`() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_GRANTED
        val adapter = mockk<BluetoothAdapter>(relaxed = true)
        val advertiser = mockk<BluetoothLeAdvertiser>(relaxed = true)
        every { bluetoothManager.adapter } returns adapter
        every { adapter.bluetoothLeAdvertiser } returns advertiser
        val activeOpens = AtomicInteger(0)
        val maximumActiveOpens = AtomicInteger(0)
        every { bluetoothManager.openGattServer(context, any()) } answers {
            val active = activeOpens.incrementAndGet()
            maximumActiveOpens.updateAndGet { current -> maxOf(current, active) }
            Thread.sleep(50)
            activeOpens.decrementAndGet()
            null
        }

        try {
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val workers = List(2) {
                Thread {
                    ready.countDown()
                    start.await()
                    bleServer.start()
                }.also { it.start() }
            }

            ready.await()
            start.countDown()
            workers.forEach { it.join() }

            assertEquals(1, maximumActiveOpens.get())
        } finally {
            unmockkStatic(ContextCompat::class)
        }
    }

    @Test
    fun `stop closes GATT server without clearing services first`() {
        val gattServer = mockk<BluetoothGattServer>(relaxed = true)
        BleServer::class.java.getDeclaredField("gattServer").apply {
            isAccessible = true
            set(bleServer, gattServer)
        }

        bleServer.stop()

        verify(exactly = 0) { gattServer.clearServices() }
        verify(exactly = 1) { gattServer.close() }
    }
}

class FakeTimeProvider : TimeProvider {
    var currentTime: Long = 0
    override fun elapsedRealtime(): Long = currentTime
}
