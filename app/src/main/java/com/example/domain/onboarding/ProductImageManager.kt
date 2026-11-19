package com.example.domain.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.OnboardingDao
import com.example.data.local.ProductEntity
import com.example.data.local.ProductImageEntity
import com.example.data.local.SoleBookDao
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ImageMappingSpec(
    val imageFile: String,
    val skuOrBarcode: String,
    val variantKey: String = "",
    val colorTag: String = "",
    val isPrimary: Boolean = false
)

data class ImageImportReport(
    val totalProcessed: Int,
    val matchedCount: Int,
    val duplicateSkippedCount: Int,
    val unmatchedFiles: List<String>,
    val productsMissingImagesCount: Int,
    val savedImages: List<ProductImageEntity>,
    val reportCsvPath: String
)

object ProductImageManager {

    private val supportedImageExts = setOf("jpg", "jpeg", "png", "webp", "bmp")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Streams a ZIP archive entry-by-entry without buffering the archive in memory.
     * Supports optional explicit mapping sheet inside or alongside ZIP plus SKU/barcode/name matching.
     */
    suspend fun importImagesFromZipStream(
        context: Context,
        zipInputStream: InputStream,
        soleBookDao: SoleBookDao,
        onboardingDao: OnboardingDao,
        externalMappings: List<ImageMappingSpec> = emptyList()
    ): ImageImportReport {
        val products = soleBookDao.getAllProductsSync()
        val bySku = products.associateBy { it.skuCode.trim().lowercase() }
        val byBarcode = products.filter { it.barcode.isNotBlank() }.associateBy { it.barcode.trim().lowercase() }
        val byName = products.associateBy { normalizeNameKey(it.name) }

        val existingHashes = onboardingDao.getAllImagesSync()
            .mapNotNull { it.sha256Hash.takeIf { h -> h.isNotBlank() } }
            .toMutableSet()

        val mappingByFilename = mutableMapOf<String, ImageMappingSpec>()
        externalMappings.forEach { m ->
            mappingByFilename[m.imageFile.substringAfterLast('/').trim().lowercase()] = m
        }

        val pendingEntries = mutableListOf<Triple<String, ByteArray, String>>() // entryName, bytes, sha256
        val discoveredMappings = mutableListOf<ImageMappingSpec>()

        ZipInputStream(zipInputStream.buffered(32 * 1024)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val baseName = entry.name.substringAfterLast('/').trim()
                    val ext = baseName.substringAfterLast('.', "").lowercase()
                    if (!baseName.startsWith(".") && !entry.name.contains("__MACOSX")) {
                        if (ext == "csv") {
                            val csvBytes = readBoundedBytes(zis, maxBytes = 4 * 1024 * 1024)
                            if (csvBytes != null) {
                                SpreadsheetCodec.streamCsv(csvBytes.inputStream()) { idx, row ->
                                    if (idx > 0 && row.size >= 2) {
                                        val skuOrBar = row.getOrNull(0).orEmpty().trim()
                                        val fileCell = row.getOrNull(1).orEmpty().trim()
                                        if (skuOrBar.isNotBlank() && fileCell.isNotBlank()) {
                                            val spec = ImageMappingSpec(
                                                imageFile = fileCell,
                                                skuOrBarcode = skuOrBar,
                                                variantKey = row.getOrNull(2).orEmpty().trim(),
                                                colorTag = row.getOrNull(3).orEmpty().trim(),
                                                isPrimary = row.getOrNull(4).orEmpty().equals("true", ignoreCase = true) ||
                                                    row.getOrNull(4).orEmpty() == "1"
                                            )
                                            discoveredMappings += spec
                                            mappingByFilename[fileCell.substringAfterLast('/').lowercase()] = spec
                                        }
                                    }
                                }
                            }
                        } else if (ext in supportedImageExts) {
                            val imgBytes = readBoundedBytes(zis, maxBytes = 12 * 1024 * 1024)
                            if (imgBytes != null && imgBytes.isNotEmpty()) {
                                pendingEntries += Triple(baseName, imgBytes, sha256(imgBytes))
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val savedImages = mutableListOf<ProductImageEntity>()
        val unmatched = mutableListOf<String>()
        var duplicateSkipped = 0
        val matchedProductIds = mutableSetOf<Long>()

        for ((fileName, bytes, hash) in pendingEntries) {
            if (!existingHashes.add(hash)) {
                duplicateSkipped++
                continue
            }
            val lowerName = fileName.lowercase()
            val explicitSpec = mappingByFilename[lowerName]
            val targetProduct = resolveProductForImage(
                fileName = fileName,
                spec = explicitSpec,
                bySku = bySku,
                byBarcode = byBarcode,
                byName = byName
            )
            if (targetProduct == null) {
                unmatched += fileName
                continue
            }

            val savedPair = saveCompressedAndThumbnail(context, bytes, targetProduct.skuCode)
            if (savedPair == null) {
                unmatched += fileName
                continue
            }
            val (fullPath, thumbPath) = savedPair
            val inferredColor = explicitSpec?.colorTag?.ifBlank { extractColorHint(fileName) } ?: extractColorHint(fileName)
            val isPrimary = explicitSpec?.isPrimary == true || targetProduct.photoUri.isBlank()

            val imgEntity = ProductImageEntity(
                productId = targetProduct.id,
                variantKey = explicitSpec?.variantKey ?: "",
                colorTag = inferredColor,
                localPath = fullPath,
                thumbPath = thumbPath,
                sha256Hash = hash,
                isPrimary = isPrimary
            )
            val imgId = onboardingDao.insertProductImage(imgEntity)
            savedImages += imgEntity.copy(id = imgId)
            matchedProductIds += targetProduct.id

            if (isPrimary) {
                soleBookDao.updateProduct(
                    targetProduct.copy(
                        photoUri = fullPath,
                        colorTag = targetProduct.colorTag.ifBlank { inferredColor },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val productsMissingImages = products.filter { p ->
            p.photoUri.isBlank() && p.id !in matchedProductIds
        }

        val reportFile = writeImageReportCsv(context, unmatched, productsMissingImages, duplicateSkipped, savedImages.size)

        return ImageImportReport(
            totalProcessed = pendingEntries.size,
            matchedCount = savedImages.size,
            duplicateSkippedCount = duplicateSkipped,
            unmatchedFiles = unmatched,
            productsMissingImagesCount = productsMissingImages.size,
            savedImages = savedImages,
            reportCsvPath = reportFile.absolutePath
        )
    }

    suspend fun importSingleImageUriForProduct(
        context: Context,
        uri: Uri,
        product: ProductEntity,
        variantKey: String = "",
        colorTag: String = "",
        isPrimary: Boolean = true,
        soleBookDao: SoleBookDao,
        onboardingDao: OnboardingDao
    ): ProductImageEntity? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBoundedBytes(it, 12 * 1024 * 1024) }
            ?: return null
        val hash = sha256(bytes)
        val existing = onboardingDao.findImageByHash(hash)
        if (existing != null && existing.productId == product.id) return existing

        val (fullPath, thumbPath) = saveCompressedAndThumbnail(context, bytes, product.skuCode) ?: return null
        val entity = ProductImageEntity(
            productId = product.id,
            variantKey = variantKey,
            colorTag = colorTag,
            localPath = fullPath,
            thumbPath = thumbPath,
            sha256Hash = hash,
            isPrimary = isPrimary
        )
        val id = onboardingDao.insertProductImage(entity)
        if (isPrimary || product.photoUri.isBlank()) {
            soleBookDao.updateProduct(product.copy(photoUri = fullPath, updatedAt = System.currentTimeMillis()))
        }
        return entity.copy(id = id)
    }

    suspend fun downloadImageUrlForProduct(
        context: Context,
        imageUrl: String,
        product: ProductEntity,
        variantKey: String = "",
        soleBookDao: SoleBookDao,
        onboardingDao: OnboardingDao
    ): ProductImageEntity? {
        val cleanUrl = imageUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) return null
        val bytes = runCatching {
            val req = Request.Builder().url(cleanUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                if (contentType.isNotEmpty() && !contentType.startsWith("image/")) return@use null
                resp.body?.byteStream()?.use { readBoundedBytes(it, 8 * 1024 * 1024) }
            }
        }.getOrNull() ?: return null

        val hash = sha256(bytes)
        val dup = onboardingDao.findImageByHash(hash)
        if (dup != null && dup.productId == product.id) return dup

        val (fullPath, thumbPath) = saveCompressedAndThumbnail(context, bytes, product.skuCode) ?: return null
        val isPrimary = product.photoUri.isBlank()
        val entity = ProductImageEntity(
            productId = product.id,
            variantKey = variantKey,
            colorTag = "",
            localPath = fullPath,
            thumbPath = thumbPath,
            sourceUrl = cleanUrl,
            sha256Hash = hash,
            isPrimary = isPrimary
        )
        val id = onboardingDao.insertProductImage(entity)
        if (isPrimary) {
            soleBookDao.updateProduct(product.copy(photoUri = fullPath, updatedAt = System.currentTimeMillis()))
        }
        return entity.copy(id = id)
    }

    private fun resolveProductForImage(
        fileName: String,
        spec: ImageMappingSpec?,
        bySku: Map<String, ProductEntity>,
        byBarcode: Map<String, ProductEntity>,
        byName: Map<String, ProductEntity>
    ): ProductEntity? {
        if (spec != null) {
            val key = spec.skuOrBarcode.trim().lowercase()
            bySku[key]?.let { return it }
            byBarcode[key]?.let { return it }
            byName[normalizeNameKey(spec.skuOrBarcode)]?.let { return it }
        }
        val stem = fileName.substringBeforeLast('.').trim()
        val stemLower = stem.lowercase()
        bySku[stemLower]?.let { return it }
        byBarcode[stemLower]?.let { return it }

        // Split on '_' or ' ' to match prefix SKU e.g. "SL-001_Black_front.jpg" -> "sl-001"
        val tokens = stemLower.split('_', ' ').filter { it.isNotBlank() }
        for (tok in tokens) {
            bySku[tok]?.let { return it }
            byBarcode[tok]?.let { return it }
        }
        // Fallback: dash-preserving prefix match e.g. "SL-001-red.jpg" -> "SL-001"
        val hyphenParts = stemLower.split('-')
        if (hyphenParts.size >= 2) {
            val firstTwo = "${hyphenParts[0]}-${hyphenParts[1]}"
            bySku[firstTwo]?.let { return it }
        }
        byName[normalizeNameKey(stem)]?.let { return it }
        return null
    }

    private fun extractColorHint(fileName: String): String {
        val knownColors = listOf("Black", "White", "Brown", "Tan", "Blue", "Navy", "Grey", "Red", "Green", "Pink", "Yellow", "Beige")
        val stem = fileName.substringBeforeLast('.')
        return knownColors.firstOrNull { stem.contains(it, ignoreCase = true) } ?: ""
    }

    private fun normalizeNameKey(raw: String): String {
        return raw.lowercase().replace(Regex("[^a-z0-9]+"), "")
    }

    fun saveCompressedAndThumbnail(
        context: Context,
        bytes: ByteArray,
        skuHint: String,
        maxFullDim: Int = 1200,
        maxThumbDim: Int = 240
    ): Pair<String, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sample = 1
        while (origW / sample > maxFullDim * 2 || origH / sample > maxFullDim * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        val fullBmp = scaleDown(decoded, maxFullDim)
        if (fullBmp !== decoded) decoded.recycle()

        val thumbBmp = scaleDown(fullBmp, maxThumbDim)

        val safeSku = skuHint.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val stamp = "${System.nanoTime()}"
        val imgDir = File(context.filesDir, "product_images").apply { mkdirs() }
        val thumbDir = File(context.filesDir, "product_thumbs").apply { mkdirs() }
        val fullFile = File(imgDir, "${safeSku}_$stamp.jpg")
        val thumbFile = File(thumbDir, "${safeSku}_${stamp}_thumb.jpg")

        return try {
            FileOutputStream(fullFile).use { fullBmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            FileOutputStream(thumbFile).use { thumbBmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            if (thumbBmp !== fullBmp) thumbBmp.recycle()
            fullBmp.recycle()
            fullFile.absolutePath to thumbFile.absolutePath
        } catch (_: Exception) {
            if (thumbBmp !== fullBmp) thumbBmp.recycle()
            fullBmp.recycle()
            null
        }
    }

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= maxDim) return bmp
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (bmp.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bmp.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun writeImageReportCsv(
        context: Context,
        unmatched: List<String>,
        missingProducts: List<ProductEntity>,
        duplicateCount: Int,
        matchedCount: Int
    ): File {
        val rows = mutableListOf(
            listOf("ReportType", "Identifier", "Details")
        )
        rows += listOf("SUMMARY", "Matched", matchedCount.toString())
        rows += listOf("SUMMARY", "DuplicatesSkipped", duplicateCount.toString())
        unmatched.forEach { file ->
            rows += listOf("UNMATCHED_FILE", file, "No matching SKU, barcode, or product name")
        }
        missingProducts.forEach { p ->
            rows += listOf("PRODUCT_MISSING_IMAGE", p.skuCode, p.name)
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "image_import_report_${System.currentTimeMillis()}.csv")
        file.writeText(SpreadsheetCodec.buildCsvFromRows(rows), Charsets.UTF_8)
        return file
    }
}
