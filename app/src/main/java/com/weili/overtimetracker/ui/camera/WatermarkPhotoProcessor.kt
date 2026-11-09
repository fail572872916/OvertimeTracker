package com.weili.overtimetracker.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.UUID

object WatermarkPhotoProcessor {
    private const val DIRECTORY_NAME = "watermark_camera"
    private const val MAX_DIMENSION = 1_920
    private const val JPEG_QUALITY = 88
    private val exifTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun createRawCaptureFile(context: Context): File = File(
        temporaryDirectory(context),
        "capture-${UUID.randomUUID()}.jpg",
    )

    fun addWatermark(
        context: Context,
        rawFile: File,
        snapshot: WatermarkSnapshot,
    ): File {
        val output = File(temporaryDirectory(context), "watermarked-${UUID.randomUUID()}.jpg")
        val upright = decodeUprightBitmap(rawFile) ?: error("无法读取相机照片")
        val mutable = if (upright.isMutable && upright.config == Bitmap.Config.ARGB_8888) {
            upright
        } else {
            upright.copy(Bitmap.Config.ARGB_8888, true).also { upright.recycle() }
        }

        try {
            drawWatermark(mutable, WatermarkFormatter.lines(snapshot))
            output.outputStream().buffered().use { stream ->
                check(mutable.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    "无法保存水印照片"
                }
            }
            ExifInterface(output).apply {
                setLatLong(snapshot.coordinates.latitude, snapshot.coordinates.longitude)
                setAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    snapshot.capturedAt.format(exifTimeFormatter),
                )
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            mutable.recycle()
            rawFile.delete()
        }
    }

    fun deleteIfTemporary(context: Context, uri: Uri) {
        if (uri.scheme != "file") return
        val file = uri.path?.let(::File) ?: return
        val directory = temporaryDirectory(context).canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (candidate.parentFile == directory) candidate.delete()
    }

    private fun temporaryDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    private fun decodeUprightBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DIMENSION * 2 ||
            bounds.outHeight / sampleSize > MAX_DIMENSION * 2
        ) {
            sampleSize *= 2
        }
        val sampled = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val transformed = transformForExif(sampled, orientation)
        if (transformed !== sampled) sampled.recycle()

        if (transformed.width <= MAX_DIMENSION && transformed.height <= MAX_DIMENSION) {
            return transformed
        }
        val factor = minOf(
            MAX_DIMENSION.toFloat() / transformed.width,
            MAX_DIMENSION.toFloat() / transformed.height,
        )
        return transformed.scale(
            (transformed.width * factor).toInt().coerceAtLeast(1),
            (transformed.height * factor).toInt().coerceAtLeast(1),
        ).also { scaled ->
            if (scaled !== transformed) transformed.recycle()
        }
    }

    private fun transformForExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun drawWatermark(bitmap: Bitmap, lines: List<String>) {
        val shortSide = minOf(bitmap.width, bitmap.height).toFloat()
        val textSize = (shortSide * 0.035f).coerceIn(28f, 54f)
        val padding = textSize * 0.62f
        val margin = (shortSide * 0.026f).coerceAtLeast(18f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(178, 12, 18, 24)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 193, 7)
        }
        val metrics = textPaint.fontMetrics
        val lineHeight = (metrics.descent - metrics.ascent) * 1.12f
        val contentWidth = lines.maxOf(textPaint::measureText)
        val boxWidth = contentWidth + padding * 2f + textSize * 0.18f
        val boxHeight = lineHeight * lines.size + padding * 2f
        val left = margin
        val bottom = bitmap.height - margin
        val top = bottom - boxHeight
        val rect = RectF(left, top, left + boxWidth, bottom)
        val radius = textSize * 0.28f
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        canvas.drawRoundRect(
            RectF(left, top, left + textSize * 0.18f, bottom),
            radius,
            radius,
            accentPaint,
        )
        val textLeft = left + padding + textSize * 0.18f
        var baseline = top + padding - metrics.ascent
        lines.forEach { line ->
            canvas.drawText(line, textLeft, baseline, textPaint)
            baseline += lineHeight
        }
    }
}
