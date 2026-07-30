package de.baseline.nutrition.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `normalisiert eine gueltige server url`() {
        assertEquals(
            "https://nutrition.example.de/",
            ServerAddress.normalize(" https://nutrition.example.de ", allowCleartext = false),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `verbietet unverschluesseltes http ausser in lokaler buildvariante`() {
        ServerAddress.normalize("http://192.168.1.20:8000", allowCleartext = false)
    }

    @Test
    fun `erlaubt lokale server in der debug buildvariante`() {
        assertEquals(
            "http://192.168.1.20:8000/",
            ServerAddress.normalize("http://192.168.1.20:8000/", allowCleartext = true),
        )
    }
}
