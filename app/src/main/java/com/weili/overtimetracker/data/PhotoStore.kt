package com.weili.overtimetracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.weili.overtimetracker.domain.PhotoAttachment
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val photoDirectory = File(appContext.filesDir, PHOTO_DIRECTORY).apply { mkdirs() }

    suspend fun import(uri: Uri, entryId: Long): PhotoAttachment = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val target = File(photoDirectory, "$id.jpg")
        val decoded = decodeSampledBitmap(uri)
            ?: error("无法读取所选照片")
        val sourceExif = readExif(uri)
        val rotated = rotateForExif(decoded, sourceExif.rotationDegrees)
        try {
            target.outputStream().buffered().use { output ->
                check(rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "无法压缩照片"
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
        }
        copyExifMetadata(target, sourceExif)
        deleteImportedCacheFile(uri)

        PhotoAttachment(
            id = id,
            entryId = entryId,
            relativePath = target.name,
            displayName = queryDisplayName(uri) ?: "工作证明.jpg",
            mimeType = "image/jpeg",
            addedAt = LocalDateTime.now(),
        )
    }

    suspend fun readBytes(attachment: PhotoAttachment): ByteArray = withContext(Dispatchers.IO) {
        resolve(attachment).readBytes()
    }

    suspend fun delete(attachment: PhotoAttachment) = withContext(Dispatchers.IO) {
        resolve(attachment).delete()
    }

    fun resolve(attachment: PhotoAttachment): File = File(photoDirectory, attachment.relativePath)

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DIMENSION * 2 ||
            bounds.outHeight / sampleSize > MAX_DIMENSION * 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = open(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        if (sampled.width <= MAX_DIMENSION && sampled.height <= MAX_DIMENSION) return sampled

        val scale = minOf(
            MAX_DIMENSION.toFloat() / sampled.width,
            MAX_DIMENSION.toFloat() / sampled.height,
        )
        return sampled.scale(
            (sampled.width * scale).toInt().coerceAtLeast(1),
            (sampled.height * scale).toInt().coerceAtLeast(1),
        ).also { scaled ->
            if (scaled !== sampled) sampled.recycle()
        }
    }

    private fun readExif(uri: Uri): SourceExif = runCatching {
        open(uri)?.use { input ->
            val exif = ExifInterface(input)
            SourceExif(
                rotationDegrees = when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                },
                latLong = exif.latLong,
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            )
        } ?: SourceExif()
    }.getOrDefault(SourceExif())

    private fun copyExifMetadata(target: File, source: SourceExif) {
        runCatching {
            ExifInterface(target).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                source.latLong?.let { setLatLong(it[0], it[1]) }
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, source.dateTimeOriginal)
                saveAttributes()
            }
        }
    }

    private fun rotateForExif(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.name
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun open(uri: Uri): InputStream? =
        if (uri.scheme == "file") uri.path?.let(::File)?.inputStream()
        else resolver.openInputStream(uri)

    private fun deleteImportedCacheFile(uri: Uri) {
        if (uri.scheme != "file") return
        val source = uri.path?.let(::File) ?: return
        val cacheDirectory = appContext.cacheDir.canonicalFile
        val candidate = runCatching { source.canonicalFile }.getOrNull() ?: return
        if (candidate.toPath().startsWith(cacheDirectory.toPath())) candidate.delete()
    }

    companion object {
        const val PHOTO_DIRECTORY = "overtime_photos"
        private const val MAX_DIMENSION = 1_600
        private const val JPEG_QUALITY = 84
    }

    private data class SourceExif(
        val rotationDegrees: Float = 0f,
        val latLong: DoubleArray? = null,
        val dateTimeOriginal: String? = null,
    )
}
