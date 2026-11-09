package com.weili.overtimetracker.ui.camera

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

data class WatermarkSnapshot(
    val coordinates: GeoCoordinates,
    val capturedAt: LocalDateTime,
)

object WatermarkFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun lines(snapshot: WatermarkSnapshot): List<String> = listOf(
        "经度 ${formatCoordinate(snapshot.coordinates.longitude)}",
        "纬度 ${formatCoordinate(snapshot.coordinates.latitude)}",
        snapshot.capturedAt.format(timeFormatter),
    )

    fun accuracyLabel(coordinates: GeoCoordinates): String =
        "定位精度 ±${coordinates.accuracyMeters.coerceAtLeast(0f).toInt()} 米"

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.ROOT, "%.6f", value)
}
