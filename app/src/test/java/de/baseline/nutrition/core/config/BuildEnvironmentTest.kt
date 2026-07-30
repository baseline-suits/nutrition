package de.baseline.nutrition.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildEnvironmentTest {
    @Test
    fun `ordnet alle bekannten Buildumgebungen zu`() {
        assertEquals(BuildEnvironment.Local, BuildEnvironment.from("local"))
        assertEquals(BuildEnvironment.InternalBeta, BuildEnvironment.from("internalBeta"))
        assertEquals(BuildEnvironment.Production, BuildEnvironment.from("production"))
    }

    @Test(expected = IllegalStateException::class)
    fun `weist unbekannte Buildumgebung zurück`() {
        BuildEnvironment.from("preview")
    }
}

