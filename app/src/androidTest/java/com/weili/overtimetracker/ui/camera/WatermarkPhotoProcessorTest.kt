package com.weili.overtimetracker.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkPhotoProcessorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val snapshot = WatermarkSnapshot(
        coordinates = GeoCoordinates(31.230416, 121.473701, 6f),
        capturedAt = LocalDateTime.of(2026, 7, 31, 20, 30),
    )

    @Test
    fun burnsWatermarkIntoPortraitPhotoBottomLeft() {
        verifyBottomLeftWatermark(width = 800, height = 1_200)
    }

    @Test
    fun burnsWatermarkIntoLandscapePhotoBottomLeft() {
        verifyBottomLeftWatermark(width = 1_200, height = 800)
    }

    private fun verifyBottomLeftWatermark(width: Int, height: Int) {
        val source = File(context.cacheDir, "processor-test-$width-$height.jpg")
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.rgb(20, 210, 80))
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
        }

        val output = WatermarkPhotoProcessor.addWatermark(context, source, snapshot)
        val result = BitmapFactory.decodeFile(output.absolutePath)
        val untouched = result.getPixel(width - 30, 30)
        val watermarked = result.getPixel(50, height - 45)

        assertTrue(Color.green(untouched) > 170)
        assertTrue(Color.green(watermarked) < 150 || Color.red(watermarked) > 120)
        val latLong = ExifInterface(output).latLong
        assertEquals(snapshot.coordinates.latitude, latLong!![0], 0.000001)
        assertEquals(snapshot.coordinates.longitude, latLong[1], 0.000001)

        result.recycle()
        output.delete()
    }
}
