package com.spop.poverlay.dircon

import org.junit.Assert.assertEquals
import org.junit.Test

class DirConMdnsServiceUuidListTest {
    @Test
    fun advertisesFitnessServicesInCompatibilityOrder() {
        val uuids = listOf("1826", "1818", "1816", "180a")

        assertEquals(
            listOf("1816", "1818", "180d", "1826"),
            uuids.toDirConMdnsServiceUuidList()
        )
    }

    @Test
    fun omitsNonFitnessServicesFromMdnsHint() {
        val uuids = listOf("180a")

        assertEquals(
            emptyList<String>(),
            uuids.toDirConMdnsServiceUuidList()
        )
    }
}
