package com.example.domain.onboarding

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SpreadsheetPreview(
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val estimatedTotalRows: Int,
    val suggestedMapping: Map<Int, TargetField>,
    val fileFormat: String // CSV or XLSX
)

/**
 * Pure-Kotlin streaming CSV & XLSX reader/writer designed for 10,000+ rows on Android
 * with tiny heap overhead (no Apache POI bloat).
 */
object SpreadsheetCodec {

    fun sha256OfFile(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            var read = input.read(buf)
            while (read > 0) {
                md.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun detectFormatBySignatureOrName(file: File, fileName: String = file.name): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "xlsx") return "XLSX"
        if (ext == "csv" || ext == "tsv" || ext == "txt") return "CSV"
        // Peek magic bytes for ZIP/XLSX ("PK")
        val header = ByteArray(2)
        val n = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
        return if (n == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) "XLSX" else "CSV"
    }

    fun inspectPreview(file: File, fileName: String = file.name, maxSampleRows: Int = 5): SpreadsheetPreview {
        val fmt = detectFormatBySignatureOrName(file, fileName)
        var headers = emptyList<String>()
        val samples = mutableListOf<List<String>>()
        var rowCount = 0

        streamRows(file, fmt) { idx, row ->
            if (idx == 0) {
                headers = row
            } else {
                rowCount++
                if (samples.size < maxSampleRows) samples += row
            }
        }
        return SpreadsheetPreview(
            headers = headers,
            sampleRows = samples,
            estimatedTotalRows = rowCount,
            suggestedMapping = ColumnMapper.autoDetectMappings(headers),
            fileFormat = fmt
        )
    }

    fun streamRows(
        file: File,
        format: String = detectFormatBySignatureOrName(file),
        onRow: (rowIndex: Int, cells: List<String>) -> Unit
    ) {
        if (format == "XLSX") {
            streamXlsx(file, onRow)
        } else {
            file.inputStream().use { streamCsv(it, onRow) }
        }
    }

    fun streamCsv(
        inputStream: InputStream,
        onRow: (rowIndex: Int, cells: List<String>) -> Unit
    ) {
        val pushback = PushbackInputStream(inputStream, 4)
        val bom = ByteArray(3)
        val readBom = pushback.read(bom)
        if (readBom == 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
            // Stripped UTF-8 BOM
        } else if (readBom > 0) {
            pushback.unread(bom, 0, readBom)
        }

        val reader = BufferedReader(InputStreamReader(pushback, StandardCharsets.UTF_8), 32 * 1024)
        var delimiter: Char? = null
        var rowIndex = 0
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false

        fun detectDelimiter(firstLineHint: String): Char {
            val commaCount = firstLineHint.count { it == ',' }
            val semiCount = firstLineHint.count { it == ';' }
            val tabCount = firstLineHint.count { it == '\t' }
            return when {
                tabCount > commaCount && tabCount >= semiCount -> '\t'
                semiCount > commaCount && semiCount > tabCount -> ';'
                else -> ','
            }
        }

        // Peek first 512 chars to auto-detect separator
        reader.mark(2048)
        val peekChars = CharArray(512)
        val peekLen = reader.read(peekChars)
        reader.reset()
        delimiter = if (peekLen > 0) detectDelimiter(String(peekChars, 0, peekLen)) else ','

        var prevWasCr = false
        while (true) {
            val cInt = reader.read()
            if (cInt == -1) {
                if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
                    currentRow += cell.toString().trim()
                    if (currentRow.any { it.isNotEmpty() }) {
                        onRow(rowIndex++, currentRow.toList())
                    }
                }
                break
            }
            val ch = cInt.toChar()
            if (prevWasCr && ch == '\n') {
                prevWasCr = false
                continue
            }
            prevWasCr = false

            when {
                inQuotes -> {
                    if (ch == '"') {
                        reader.mark(1)
                        val next = reader.read()
                        if (next == '"'.code) {
                            cell.append('"')
                        } else {
                            if (next != -1) reader.reset()
                            inQuotes = false
                        }
                    } else {
                        cell.append(ch)
                    }
                }
                ch == '"' -> {
                    if (cell.isEmpty()) inQuotes = true else cell.append(ch)
                }
                ch == delimiter -> {
                    currentRow += cell.toString().trim()
                    cell.setLength(0)
                }
                ch == '\r' || ch == '\n' -> {
                    if (ch == '\r') prevWasCr = true
                    currentRow += cell.toString().trim()
                    cell.setLength(0)
                    if (currentRow.any { it.isNotEmpty() }) {
                        onRow(rowIndex++, currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
        }
    }

    fun streamXlsx(
        file: File,
        onRow: (rowIndex: Int, cells: List<String>) -> Unit
    ) {
        ZipFile(file).use { zip ->
            val sharedStrings = readXlsxSharedStrings(zip)
            val sheetEntryName = resolveFirstSheetEntryName(zip)
            val sheetEntry = zip.getEntry(sheetEntryName)
                ?: throw IllegalArgumentException("No worksheet found inside XLSX file")

            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            zip.getInputStream(sheetEntry).buffered(64 * 1024).use { sheetStream ->
                parser.setInput(sheetStream, "UTF-8")

                var event = parser.eventType
                var currentRowCells = mutableListOf<String>()
                var currentColIdx = -1
                var currentCellType = ""
                var inValue = false
                var inInlineStr = false
                val textBuf = StringBuilder()
                var emittedRowIdx = 0

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "row" -> {
                                    currentRowCells = mutableListOf()
                                    currentColIdx = -1
                                }
                                "c" -> {
                                    val ref = parser.getAttributeValue(null, "r")
                                    currentColIdx = if (!ref.isNullOrBlank()) {
                                        columnRefToIndex(ref)
                                    } else {
                                        currentColIdx + 1
                                    }
                                    currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                    textBuf.setLength(0)
                                }
                                "v" -> {
                                    inValue = true
                                    textBuf.setLength(0)
                                }
                                "is" -> {
                                    inInlineStr = true
                                    textBuf.setLength(0)
                                }
                                "t" -> {
                                    if (inInlineStr) {
                                        // Accumulate across multiple <t> runs inside <is>
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inValue || inInlineStr) {
                                textBuf.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "v" -> inValue = false
                                "is" -> inInlineStr = false
                                "c" -> {
                                    val rawVal = textBuf.toString().trim()
                                    val decoded = when (currentCellType) {
                                        "s" -> rawVal.toIntOrNull()?.let { idx ->
                                            sharedStrings.getOrNull(idx) ?: ""
                                        } ?: ""
                                        "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                                        else -> rawVal
                                    }
                                    if (currentColIdx >= 0) {
                                        while (currentRowCells.size <= currentColIdx) {
                                            currentRowCells += ""
                                        }
                                        currentRowCells[currentColIdx] = decoded
                                    }
                                    textBuf.setLength(0)
                                }
                                "row" -> {
                                    if (currentRowCells.any { it.isNotEmpty() }) {
                                        onRow(emittedRowIdx++, currentRowCells.toList())
                                    }
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
    }

    private fun readXlsxSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val result = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        zip.getInputStream(entry).buffered(64 * 1024).use { stream ->
            parser.setInput(stream, "UTF-8")
            var event = parser.eventType
            var inSi = false
            var inT = false
            val buf = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            inSi = true
                            buf.setLength(0)
                        } else if (parser.name == "t" && inSi) {
                            inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inSi && inT) buf.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") inT = false
                        else if (parser.name == "si") {
                            inSi = false
                            result += buf.toString()
                        }
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun resolveFirstSheetEntryName(zip: ZipFile): String {
        val defaultPath = "xl/worksheets/sheet1.xml"
        val workbookEntry = zip.getEntry("xl/workbook.xml") ?: return defaultPath
        val relsEntry = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return defaultPath
        val relTargets = mutableMapOf<String, String>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }

        zip.getInputStream(relsEntry).use { s ->
            val p = factory.newPullParser().apply { setInput(s, "UTF-8") }
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val target = p.getAttributeValue(null, "Target")
                    if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                        relTargets[id] = target.trimStart('/')
                    }
                }
                e = p.next()
            }
        }

        zip.getInputStream(workbookEntry).use { s ->
            val p = factory.newPullParser().apply { setInput(s, "UTF-8") }
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && p.name == "sheet") {
                    val rid = p.getAttributeValue(null, "r:id")
                    val target = rid?.let { relTargets[it] }
                    if (!target.isNullOrBlank()) {
                        return if (target.startsWith("xl/")) target else "xl/$target"
                    }
                }
                e = p.next()
            }
        }
        return defaultPath
    }

    private fun columnRefToIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    fun buildImportTemplateRows(): List<List<String>> {
        val headers = listOf(
            "SKU", "Barcode", "Product Name", "Category", "Brand", "Unit",
            "Variant Type", "Variant", "Color", "Cost Price", "Selling Price", "MRP",
            "Opening Stock", "Reorder Level", "Location", "Supplier", "Batch No", "Expiry Date", "Serials", "Image Ref"
        )
        val row1 = listOf(
            "GR-001", "8901234567890", "Basmati Rice", "Staples & Grains", "Daawat", "kg",
            "Pack Size", "5kg", "", "480", "560", "600",
            "25", "5", "RACK-A1", "Shree Traders", "B-2409", "Dec 2026", "", "GR-001.jpg"
        )
        val row2 = listOf(
            "CL-002", "8909876543210", "Cotton Polo Tee", "Shirts & T-Shirts", "Classic", "pcs",
            "Size", "L", "Navy Blue", "290", "599", "699",
            "12", "4", "SHELF-B2", "Surat Garments", "", "", "", "CL-002_Navy.jpg"
        )
        val row3 = listOf(
            "EL-003", "8905556667778", "USB-C Fast Charger 25W", "Chargers & Cables", "Anker", "pcs",
            "Model", "25W PD", "White", "520", "899", "999",
            "8", "3", "COUNTER-1", "Metro Electronics", "", "12 Months", "SN101,SN102", ""
        )
        return listOf(headers, row1, row2, row3)
    }

    fun buildImportTemplateCsv(): String {
        return buildCsvFromRows(buildImportTemplateRows())
    }

    fun buildCsvFromRows(rows: List<List<String>>): String {
        val sb = StringBuilder()
        rows.forEach { row ->
            sb.appendLine(row.joinToString(",") { cell -> escapeCsvCell(cell) })
        }
        return sb.toString()
    }

    fun escapeCsvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun writeImportTemplateXlsx(outFile: File): File {
        writeXlsxFromRows(outFile, buildImportTemplateRows())
        return outFile
    }

    fun writeXlsxFromRows(outFile: File, rows: List<List<String>>) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            writeXlsxToStream(fos, rows)
        }
    }

    fun writeXlsxToStream(outputStream: OutputStream, rows: List<List<String>>) {
        ZipOutputStream(outputStream.buffered()).use { zos ->
            fun putTextEntry(path: String, xml: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(xml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            putTextEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )

            putTextEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            putTextEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="SoleBook_Import" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
            )

            putTextEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
            )

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val writer = zos.bufferedWriter(StandardCharsets.UTF_8)
            writer.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            writer.write("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rIdx, row ->
                val rNum = rIdx + 1
                writer.write("""<row r="$rNum">""")
                row.forEachIndexed { cIdx, rawCell ->
                    val colRef = "${indexToColumnRef(cIdx)}$rNum"
                    val asNum = if (rIdx > 0 && rawCell.isNotEmpty() && !rawCell.startsWith("0") && rawCell.toDoubleOrNull() != null && rawCell.length <= 11) {
                        rawCell
                    } else null
                    if (asNum != null) {
                        writer.write("""<c r="$colRef"><v>$asNum</v></c>""")
                    } else {
                        writer.write("""<c r="$colRef" t="inlineStr"><is><t>${escapeXml(rawCell)}</t></is></c>""")
                    }
                }
                writer.write("</row>")
            }
            writer.write("</sheetData></worksheet>")
            writer.flush()
            zos.closeEntry()
        }
    }

    private fun indexToColumnRef(index: Int): String {
        var i = index + 1
        val sb = StringBuilder()
        while (i > 0) {
            val rem = (i - 1) % 26
            sb.append(('A'.code + rem).toChar())
            i = (i - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch >= ' ' || ch == '\t' || ch == '\n') sb.append(ch)
            }
        }
        return sb.toString()
    }
}
