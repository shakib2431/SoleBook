package com.example.domain.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.ProductEntity
import com.example.util.ImageUtils
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Offline Paper-to-Action Document Extractor backed by:
 *  - Android PdfRenderer for multi-page PDF rasterization
 *  - Grayscale contrast preprocessing for faint thermal bills & low-light scans
 *  - On-device ML Kit Text Recognition (Latin + Devanagari) with spatial Y-band row reconstruction
 *  - MultilingualDocumentParser (English / Hindi / Bengali normalization & mixed-intent classification)
 */
class OnDeviceDocumentExtractor : DocumentIntelligenceEngine {

    override val engineId: String = "ON_DEVICE_MLKIT_OCR"
    override val isCloudAiBacked: Boolean = false
    override val engineDescription: String =
        "On-Device ML Kit OCR (Latin & Devanagari printed text) + Multilingual Rule Classifier"

    override suspend fun extractDocument(
        context: Context,
        inputs: List<DocumentSourceInput>,
        catalogProducts: List<ProductEntity>
    ): DocumentExtractionResult = withContext(Dispatchers.Default) {
        val pages = mutableListOf<Pair<String, String>>()
        val warnings = mutableListOf(
            "Running offline On-Device OCR & Rule Parser (Cloud Gemini AI via Firebase was declined). Verify low-confidence rows, handwriting, or Bengali script manually before approving."
        )

        for (input in inputs) {
            when (input) {
                is DocumentSourceInput.PlainTextNote -> {
                    pages += input.label to input.text
                }
                is DocumentSourceInput.ImageFile -> {
                    val bmp = ImageUtils.decodeNormalizedBitmap(context, Uri.fromFile(input.file), maxDim = 1800)
                    if (bmp == null) {
                        warnings += "Could not decode image: ${input.label}"
                    } else {
                        val processed = enhanceContrastForOcr(bmp)
                        val ocrText = runDualScriptOcr(processed)
                        if (processed !== bmp) processed.recycle()
                        bmp.recycle()
                        pages += input.label to ocrText
                    }
                }
                is DocumentSourceInput.ContentUri -> {
                    val isPdf = input.mimeType.contains("pdf", ignoreCase = true) ||
                        input.label.endsWith(".pdf", ignoreCase = true)
                    if (isPdf) {
                        val pdfPages = renderPdfPagesAndOcr(context, input.uri, input.label)
                        if (pdfPages.isEmpty()) {
                            warnings += "PDF had no readable pages: ${input.label}"
                        } else {
                            pages += pdfPages
                        }
                    } else {
                        val bmp = ImageUtils.decodeNormalizedBitmap(context, input.uri, maxDim = 1800)
                        if (bmp == null) {
                            warnings += "Could not decode image URI: ${input.label}"
                        } else {
                            val processed = enhanceContrastForOcr(bmp)
                            val ocrText = runDualScriptOcr(processed)
                            if (processed !== bmp) processed.recycle()
                            bmp.recycle()
                            pages += input.label to ocrText
                        }
                    }
                }
            }
        }

        MultilingualDocumentParser.parsePages(
            pages = pages,
            catalogProducts = catalogProducts,
            engineId = engineId,
            isCloudAiBacked = false,
            extraWarnings = warnings
        )
    }

    private suspend fun renderPdfPagesAndOcr(
        context: Context,
        uri: Uri,
        docLabel: String
    ): List<Pair<String, String>> {
        val tmpPdf = File(context.cacheDir, "doc_tmp_${System.currentTimeMillis()}.pdf")
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    FileOutputStream(tmpPdf).use { out -> inp.copyTo(out) }
                }
                tmpPdf.exists() && tmpPdf.length() > 0L
            }.getOrDefault(false)
        }
        if (!copied) return emptyList()

        val results = mutableListOf<Pair<String, String>>()
        try {
            val pfd = ParcelFileDescriptor.open(tmpPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val maxPages = renderer.pageCount.coerceAtMost(12)
                    for (idx in 0 until maxPages) {
                        renderer.openPage(idx).use { page ->
                            val scale = 2.0f
                            val w = (page.width * scale).toInt().coerceIn(400, 2200)
                            val h = (page.height * scale).toInt().coerceIn(400, 3000)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val ocrText = runDualScriptOcr(bmp)
                            bmp.recycle()
                            results += "$docLabel • Page ${idx + 1}" to ocrText
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Gracefully return any pages decoded so far
        } finally {
            runCatching { tmpPdf.delete() }
        }
        return results
    }

    private suspend fun runDualScriptOcr(bitmap: Bitmap): String {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val latinText = runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                recognizer.process(inputImage).awaitMlKit()
            } finally {
                recognizer.close()
            }
        }.getOrNull()

        val reconstructedLatin = latinText?.let { reconstructSpatialRows(it) }.orEmpty()

        // If Latin confidence / Devanagari characters may be present or Latin result sparse, also try Devanagari recognizer
        val devText = if (reconstructedLatin.length < 24 || looksLikeNonLatinOrNoisy(reconstructedLatin)) {
            runCatching {
                val devRec = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                try {
                    devRec.process(inputImage).awaitMlKit()
                } finally {
                    devRec.close()
                }
            }.getOrNull()?.let { reconstructSpatialRows(it) }.orEmpty()
        } else ""

        return if (devText.any { it in '\u0900'..'\u097F' }) devText else reconstructedLatin.ifBlank { devText }
    }

    private fun looksLikeNonLatinOrNoisy(s: String): Boolean {
        val letters = s.count { it.isLetter() }
        return letters < 10
    }

    /**
     * Reconstructs table rows from ML Kit bounding boxes by clustering lines with similar Y-coordinates
     * and sorting left-to-right with '|' delimiters when columns have horizontal separation.
     */
    private fun reconstructSpatialRows(result: Text): String {
        val allLines = result.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return result.text

        val sorted = allLines.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in sorted) {
            val top = line.boundingBox?.top ?: 0
            val height = line.boundingBox?.height()?.coerceAtLeast(16) ?: 24
            val tolerance = (height * 0.55f).toInt().coerceAtLeast(12)
            val targetRow = rows.lastOrNull()?.let { last ->
                val lastTop = last.first().boundingBox?.top ?: 0
                if (abs(top - lastTop) <= tolerance) last else null
            }
            if (targetRow != null) {
                targetRow += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row ->
            val ordered = row.sortedBy { it.boundingBox?.left ?: 0 }
            if (ordered.size > 1) ordered.joinToString(" | ") { it.text.trim() }
            else ordered.first().text.trim()
        }
    }

    fun enhanceContrastForOcr(src: Bitmap): Bitmap {
        return try {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val contrast = 1.35f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            grayMatrix.postConcat(contrastMatrix)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(grayMatrix) }
            canvas.drawBitmap(src, 0f, 0f, paint)
            out
        } catch (_: Throwable) {
            src
        }
    }
}

suspend fun <T> Task<T>.awaitMlKit(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
    addOnCanceledListener {
        cont.cancel()
    }
}
