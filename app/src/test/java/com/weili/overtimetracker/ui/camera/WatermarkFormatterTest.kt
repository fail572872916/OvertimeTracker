package com.weili.overtimetracker.ui.camera

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WatermarkFormatterTest {
    @Test
    fun formatsLongitudeLatitudeAndCaptureTime() {
        val snapshot = WatermarkSnapshot(
            coordinates = GeoCoordinates(
                latitude = 31.230416,
                longitude = 121.473701,
                accuracyMeters = 8.9f,
            ),
            capturedAt = LocalDateTime.of(2026, 7, 31, 20, 15, 9),
        )

        assertEquals(
            listOf(
                "经度 121.473701",
                "纬度 31.230416",
                "2026-07-31 20:15:09",
            ),
            WatermarkFormatter.lines(snapshot),
        )
        assertEquals("定位精度 ±8 米", WatermarkFormatter.accuracyLabel(snapshot.coordinates))
    }

    @Test
    fun keepsNegativeCoordinates() {
        val snapshot = WatermarkSnapshot(
            coordinates = GeoCoordinates(-33.86882, -151.20929, 12f),
            capturedAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        )

        assertEquals("经度 -151.209290", WatermarkFormatter.lines(snapshot)[0])
        assertEquals("纬度 -33.868820", WatermarkFormatter.lines(snapshot)[1])
    }
}
