package com.weili.overtimetracker.data

import com.weili.overtimetracker.domain.OvertimeCalculator
import com.weili.overtimetracker.domain.SampleEntries
import com.weili.overtimetracker.domain.WorkSchedule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.time.LocalDateTime
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxExporterTest {
    @Test
    fun createsWorkbookWithFormulasAndEmbeddedPhoto() {
        val schedule = WorkSchedule()
        val samplePhoto = proofImage()
        val rows = SampleEntries.july2026.sortedBy { it.startAt }.mapIndexed { index, entry ->
            ExcelRow(
                entry = entry,
                overtimeMinutes = OvertimeCalculator.calculateMinutes(entry, schedule),
                dayType = OvertimeCalculator.resolveDayType(entry.serviceDate, schedule, null),
                photos = if (index == 0) {
                    listOf(ExcelPhoto("现场工作证明.png", "image/png", samplePhoto))
                } else {
                    emptyList()
                },
            )
        }
        val output = ByteArrayOutputStream()

        val result = XlsxExporter.write(
            data = ExcelExportData(
                scopeLabel = "2026年7月",
                rows = rows,
                schedule = schedule,
                generatedAt = LocalDateTime.of(2026, 7, 23, 10, 30),
            ),
            output = output,
        )

        assertEquals(18, result.recordCount)
        assertEquals(1, result.photoCount)
        val entries = unzip(output.toByteArray())
        assertTrue("xl/media/image1.png" in entries)
        assertTrue("xl/drawings/drawing1.xml" in entries)
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("SUM(F9:F26)"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("<v>121.5</v>"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("照片1"))
        assertTrue(entries.getValue("xl/drawings/drawing1.xml").contains("<xdr:col>10</xdr:col>"))

        val exportDirectory = File("build/test-exports").apply { mkdirs() }
        File(exportDirectory, "overtime-sample.xlsx").writeBytes(output.toByteArray())
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun proofImage(): ByteArray {
        val width = 240
        val height = 140
        val raw = ByteArrayOutputStream()
        for (y in 0 until height) {
            raw.write(0)
            for (x in 0 until width) {
                val border = x < 12 || x >= width - 12 || y < 12 || y >= height - 12
                val stripe = y in 58..81 && x in 32..207
                val (red, green, blue) = when {
                    border -> Triple(23, 107, 91)
                    stripe -> Triple(238, 183, 64)
                    else -> Triple(232, 242, 239)
                }
                raw.write(red); raw.write(green); raw.write(blue)
            }
        }
        val compressed = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(raw.toByteArray()) }
        }.toByteArray()
        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
                data.writePngChunk("IHDR", ByteArrayOutputStream().also { header ->
                    DataOutputStream(header).use {
                        it.writeInt(width); it.writeInt(height)
                        it.write(byteArrayOf(8, 2, 0, 0, 0))
                    }
                }.toByteArray())
                data.writePngChunk("IDAT", compressed)
                data.writePngChunk("IEND", byteArrayOf())
            }
            output.toByteArray()
        }
    }

    private fun DataOutputStream.writePngChunk(type: String, bytes: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeInt(bytes.size)
        write(typeBytes)
        write(bytes)
        val crc = CRC32().apply { update(typeBytes); update(bytes) }
        writeInt(crc.value.toInt())
    }
}
