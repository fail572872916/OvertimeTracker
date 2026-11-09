package com.weili.overtimetracker.data

import com.weili.overtimetracker.domain.DayType
import com.weili.overtimetracker.domain.EntrySource
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.WorkSchedule
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExcelPhoto(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ExcelRow(
    val entry: OvertimeEntry,
    val overtimeMinutes: Long,
    val dayType: DayType,
    val photos: List<ExcelPhoto>,
)

data class ExcelExportData(
    val scopeLabel: String,
    val rows: List<ExcelRow>,
    val schedule: WorkSchedule,
    val generatedAt: LocalDateTime,
)

data class ExcelExportResult(
    val recordCount: Int,
    val photoCount: Int,
)

/** Focused OOXML writer for the app's fixed overtime-report workbook. */
object XlsxExporter {
    private const val HEADER_ROW = 8
    private const val FIRST_DATA_ROW = HEADER_ROW + 1
    private const val LAST_COLUMN = "N"

    fun write(data: ExcelExportData, output: OutputStream): ExcelExportResult {
        val photos = data.rows.flatMapIndexed { rowIndex, row ->
            row.photos.take(4).mapIndexed { photoIndex, photo ->
                PositionedPhoto(
                    row = FIRST_DATA_ROW + rowIndex,
                    column = 10 + photoIndex,
                    photo = photo,
                )
            }
        }

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.text("[Content_Types].xml", contentTypes(photos.isNotEmpty()))
            zip.text("_rels/.rels", packageRelationships())
            zip.text("docProps/app.xml", appProperties())
            zip.text("docProps/core.xml", coreProperties(data.generatedAt))
            zip.text("xl/workbook.xml", workbook())
            zip.text("xl/_rels/workbook.xml.rels", workbookRelationships())
            zip.text("xl/styles.xml", styles())
            zip.text("xl/worksheets/sheet1.xml", detailSheet(data, photos.isNotEmpty()))
            zip.text("xl/worksheets/sheet2.xml", notesSheet(data))

            if (photos.isNotEmpty()) {
                zip.text("xl/worksheets/_rels/sheet1.xml.rels", sheetRelationships())
                zip.text("xl/drawings/drawing1.xml", drawing(photos))
                zip.text("xl/drawings/_rels/drawing1.xml.rels", drawingRelationships(photos))
                photos.forEachIndexed { index, positioned ->
                    zip.binary("xl/media/image${index + 1}.${positioned.photo.extension}", positioned.photo.bytes)
                }
            }
        }
        return ExcelExportResult(data.rows.size, photos.size)
    }

    private fun detailSheet(data: ExcelExportData, hasPhotos: Boolean): String = buildString {
        val lastRow = maxOf(HEADER_ROW, FIRST_DATA_ROW + data.rows.lastIndex)
        val totalHours = data.rows.sumOf { it.overtimeMinutes } / 60.0
        xmlDeclaration()
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
        append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        append("<dimension ref=\"A1:$LAST_COLUMN$lastRow\"/>")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"$HEADER_ROW\" topLeftCell=\"A$FIRST_DATA_ROW\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("<selection pane=\"bottomLeft\" activeCell=\"A$FIRST_DATA_ROW\" sqref=\"A$FIRST_DATA_ROW\"/>")
        append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"20\"/>")
        append("<cols>")
        column(1, 1, 13.0); column(2, 3, 20.0); column(4, 7, 13.0)
        column(8, 9, 14.0); column(10, 10, 28.0); column(11, 14, 18.0)
        append("</cols><sheetData>")

        row(1, height = 30.0) {
            inlineCell("A1", "加班统计导出", 1)
        }
        row(2) {
            inlineCell("A2", "导出范围", 2)
            inlineCell("B2", data.scopeLabel, 8)
        }
        row(3) {
            inlineCell("A3", "可计加班（小时）", 2)
            formulaCell("B3", if (data.rows.isEmpty()) "0" else "SUM(F$FIRST_DATA_ROW:F$lastRow)", totalHours, 3)
        }
        row(4) {
            inlineCell("A4", "标准工作日（小时）", 2)
            numberCell("B4", data.schedule.standardDayMinutes / 60.0, 3)
        }
        row(5) {
            inlineCell("A5", "折合工作日", 2)
            formulaCell("B5", "B3/B4", totalHours / (data.schedule.standardDayMinutes / 60.0), 3)
        }
        row(6) {
            inlineCell("A6", "记录 / 照片", 2)
            inlineCell("B6", "${data.rows.size} 条 / ${data.rows.sumOf { it.photos.size.coerceAtMost(4) }} 张", 8)
        }
        row(7) {}
        row(HEADER_ROW, height = 28.0) {
            listOf(
                "归属日期", "开始时间", "结束时间", "原始小时", "排除正常工时",
                "可计小时", "折合工作日", "日期类型", "记录方式", "备注",
                "照片1", "照片2", "照片3", "照片4",
            ).forEachIndexed { index, title -> inlineCell(cellRef(index + 1, HEADER_ROW), title, 4) }
        }

        data.rows.forEachIndexed { index, exportRow ->
            val rowNumber = FIRST_DATA_ROW + index
            val rawMinutes = Duration.between(exportRow.entry.startAt, exportRow.entry.endAt).toMinutes()
            val rawHours = rawMinutes / 60.0
            val excludedHours = (rawMinutes - exportRow.overtimeMinutes).coerceAtLeast(0) / 60.0
            val overtimeHours = exportRow.overtimeMinutes / 60.0
            val standardDays = exportRow.overtimeMinutes.toDouble() / data.schedule.standardDayMinutes
            row(rowNumber, height = if (exportRow.photos.isEmpty()) 24.0 else 88.0) {
                numberCell("A$rowNumber", excelSerial(exportRow.entry.serviceDate.atStartOfDay()), 5)
                numberCell("B$rowNumber", excelSerial(exportRow.entry.startAt), 6)
                numberCell("C$rowNumber", excelSerial(exportRow.entry.endAt), 6)
                formulaCell("D$rowNumber", "(C$rowNumber-B$rowNumber)*24", rawHours, 7)
                numberCell("E$rowNumber", excludedHours, 7)
                formulaCell("F$rowNumber", "D$rowNumber-E$rowNumber", overtimeHours, 7)
                formulaCell("G$rowNumber", "F$rowNumber/${'$'}B${'$'}4", standardDays, 7)
                inlineCell("H$rowNumber", exportRow.dayType.label, 8)
                inlineCell("I$rowNumber", exportRow.entry.source.label, 8)
                inlineCell("J$rowNumber", exportRow.entry.note, 8)
            }
        }
        append("</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A1:${LAST_COLUMN}1\"/></mergeCells>")
        append("<autoFilter ref=\"A$HEADER_ROW:$LAST_COLUMN$lastRow\"/>")
        if (hasPhotos) append("<drawing r:id=\"rId1\"/>")
        append("</worksheet>")
    }

    private fun notesSheet(data: ExcelExportData): String = buildString {
        val workdays = data.schedule.workDays.sortedBy { it.value }.joinToString("、") { "周${it.value}" }
        xmlDeclaration()
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<dimension ref=\"A1:B9\"/><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"/></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"22\"/><cols>")
        column(1, 1, 24.0); column(2, 2, 60.0)
        append("</cols><sheetData>")
        row(1, height = 30.0) { inlineCell("A1", "统计规则与导出信息", 1) }
        val notes = listOf(
            "导出范围" to data.scopeLabel,
            "生成时间" to data.generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "常规工作日" to workdays,
            "正常工作时间" to "${data.schedule.normalStart} - ${data.schedule.normalEnd}",
            "周末默认开始" to data.schedule.weekendDefaultStart.toString(),
            "标准工作日" to "${data.schedule.standardDayMinutes / 60.0} 小时",
            "计算口径" to "工作日排除正常工作时段；休息日及全天加班日按实际记录时长统计。",
            "照片说明" to "证明照片已嵌入明细表。每条记录最多导出 4 张。",
        )
        notes.forEachIndexed { index, (label, value) ->
            val rowNumber = index + 2
            row(rowNumber, height = if (rowNumber >= 8) 36.0 else 22.0) {
                inlineCell("A$rowNumber", label, 2)
                inlineCell("B$rowNumber", value, 8)
            }
        }
        append("</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A1:B1\"/></mergeCells></worksheet>")
    }

    private fun drawing(photos: List<PositionedPhoto>): String = buildString {
        xmlDeclaration()
        append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\"")
        append(" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"")
        append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        photos.forEachIndexed { index, positioned ->
            val id = index + 1
            append("<xdr:oneCellAnchor><xdr:from>")
            append("<xdr:col>${positioned.column}</xdr:col><xdr:colOff>76200</xdr:colOff>")
            append("<xdr:row>${positioned.row - 1}</xdr:row><xdr:rowOff>57150</xdr:rowOff>")
            append("</xdr:from><xdr:ext cx=\"1047750\" cy=\"742950\"/><xdr:pic>")
            append("<xdr:nvPicPr><xdr:cNvPr id=\"$id\" name=\"${xml(positioned.photo.displayName)}\"/>")
            append("<xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
            append("<xdr:blipFill><a:blip r:embed=\"rId$id\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
            append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1047750\" cy=\"742950\"/></a:xfrm>")
            append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr>")
            append("</xdr:pic><xdr:clientData/></xdr:oneCellAnchor>")
        }
        append("</xdr:wsDr>")
    }

    private fun contentTypes(hasPhotos: Boolean): String = buildString {
        xmlDeclaration()
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        if (hasPhotos) {
            append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
            append("<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>")
            append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        }
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        append("<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        if (hasPhotos) append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
        append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
        append("</Types>")
    }

    private fun packageRelationships(): String = xmlDocument(
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>" +
            "</Relationships>",
    )

    private fun workbook(): String = xmlDocument(
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<bookViews><workbookView xWindow=\"0\" yWindow=\"0\" windowWidth=\"24000\" windowHeight=\"12000\"/></bookViews>" +
            "<sheets><sheet name=\"加班明细\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"规则说明\" sheetId=\"2\" r:id=\"rId2\"/></sheets>" +
            "<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>",
    )

    private fun workbookRelationships(): String = xmlDocument(
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>",
    )

    private fun sheetRelationships(): String = xmlDocument(
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/>" +
            "</Relationships>",
    )

    private fun drawingRelationships(photos: List<PositionedPhoto>): String = buildString {
        xmlDeclaration()
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        photos.forEachIndexed { index, positioned ->
            append("<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\"")
            append(" Target=\"../media/image${index + 1}.${positioned.photo.extension}\"/>")
        }
        append("</Relationships>")
    }

    private fun styles(): String = xmlDocument(
        """
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <numFmts count="3"><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/><numFmt numFmtId="165" formatCode="yyyy-mm-dd hh:mm"/><numFmt numFmtId="166" formatCode="0.00"/></numFmts>
          <fonts count="3">
            <font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
            <font><b/><sz val="18"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
          </fonts>
          <fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF176B5B"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8F2EF"/><bgColor indexed="64"/></patternFill></fill></fills>
          <borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFD6DEDB"/></left><right style="thin"><color rgb="FFD6DEDB"/></right><top style="thin"><color rgb="FFD6DEDB"/></top><bottom style="thin"><color rgb="FFD6DEDB"/></bottom><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="9">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
            <xf numFmtId="166" fontId="0" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
            <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
            <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="166" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
            <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
          </cellXfs>
          <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
        </styleSheet>
        """.trimIndent(),
    )

    private fun appProperties(): String = xmlDocument(
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">" +
            "<Application>加班记</Application><DocSecurity>0</DocSecurity><ScaleCrop>false</ScaleCrop>" +
            "<HeadingPairs><vt:vector size=\"2\" baseType=\"variant\"><vt:variant><vt:lpstr>工作表</vt:lpstr></vt:variant><vt:variant><vt:i4>2</vt:i4></vt:variant></vt:vector></HeadingPairs>" +
            "<TitlesOfParts><vt:vector size=\"2\" baseType=\"lpstr\"><vt:lpstr>加班明细</vt:lpstr><vt:lpstr>规则说明</vt:lpstr></vt:vector></TitlesOfParts>" +
            "</Properties>",
    )

    private fun coreProperties(generatedAt: LocalDateTime): String {
        val timestamp = generatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
        return xmlDocument(
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "<dc:title>加班统计导出</dc:title><dc:creator>加班记</dc:creator><cp:lastModifiedBy>加班记</cp:lastModifiedBy>" +
                "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$timestamp</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$timestamp</dcterms:modified>" +
                "</cp:coreProperties>",
        )
    }

    private inline fun StringBuilder.row(number: Int, height: Double? = null, cells: StringBuilder.() -> Unit) {
        append("<row r=\"$number\"")
        if (height != null) append(" ht=\"${decimal(height)}\" customHeight=\"1\"")
        append(">")
        cells()
        append("</row>")
    }

    private fun StringBuilder.column(min: Int, max: Int, width: Double) {
        append("<col min=\"$min\" max=\"$max\" width=\"${decimal(width)}\" customWidth=\"1\"/>")
    }

    private fun StringBuilder.inlineCell(reference: String, value: String, style: Int) {
        append("<c r=\"$reference\" s=\"$style\" t=\"inlineStr\"><is><t")
        if (value.startsWith(' ') || value.endsWith(' ')) append(" xml:space=\"preserve\"")
        append(">${xml(value)}</t></is></c>")
    }

    private fun StringBuilder.numberCell(reference: String, value: Double, style: Int) {
        append("<c r=\"$reference\" s=\"$style\"><v>${decimal(value)}</v></c>")
    }

    private fun StringBuilder.formulaCell(reference: String, formula: String, cached: Double, style: Int) {
        append("<c r=\"$reference\" s=\"$style\"><f>${xml(formula)}</f><v>${decimal(cached)}</v></c>")
    }

    private fun StringBuilder.xmlDeclaration() {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    }

    private fun xmlDocument(body: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>$body"

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun decimal(value: Double): String = String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')

    private fun excelSerial(value: LocalDateTime): Double =
        Duration.between(EXCEL_EPOCH.atStartOfDay(), value).toMillis() / 86_400_000.0 + EXCEL_TIME_EPSILON

    private fun cellRef(column: Int, row: Int): String = "${('A'.code + column - 1).toChar()}$row"

    private fun ZipOutputStream.text(path: String, value: String) = binary(path, value.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.binary(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private data class PositionedPhoto(
        val row: Int,
        val column: Int,
        val photo: ExcelPhoto,
    )

    private val ExcelPhoto.extension: String
        get() = if (mimeType.equals("image/png", ignoreCase = true)) "png" else "jpg"

    private val EntrySource.label: String
        get() = when (this) {
            EntrySource.AUTO_PUNCH -> "自动打卡"
            EntrySource.MANUAL -> "补卡"
            EntrySource.IMPORTED -> "已导入"
        }

    private val DayType.label: String
        get() = when (this) {
            DayType.WORKDAY -> "工作日"
            DayType.DAY_OFF -> "休息日"
            DayType.FULL_DAY_OVERTIME -> "全天加班"
        }

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private const val EXCEL_TIME_EPSILON = 1.0 / 86_400_000.0
}
