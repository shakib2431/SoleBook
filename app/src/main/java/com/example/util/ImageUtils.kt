package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    val bundledDrawables = mapOf(
        "drawable:img_chappal_leather" to R.drawable.img_chappal_leather,
        "drawable:img_sandal_sport" to R.drawable.img_sandal_sport,
        "drawable:img_shoe_running" to R.drawable.img_shoe_running,
        "drawable:img_shoe_formal" to R.drawable.img_shoe_formal
    )

    fun resolveDrawableRes(photoUri: String): Int? {
        return bundledDrawables[photoUri]
    }

    fun defaultDrawableForCategory(category: String): String {
        return when (category.lowercase()) {
            "chappal", "slippers" -> "drawable:img_chappal_leather"
            "sandal", "kids" -> "drawable:img_sandal_sport"
            "formal", "loafers" -> "drawable:img_shoe_formal"
            else -> "drawable:img_shoe_running"
        }
    }

    fun createTempCameraUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun createTempCameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    fun applyExifTransform(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
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
        return try {
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed !== bitmap) bitmap.recycle()
            transformed
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Decodes any image URI (Gallery, Files/Documents, Camera) cleanly in ARGB_8888 / sRGB,
     * applies EXIF orientation so captures are never sideways, and scales down to [maxDim].
     */
    fun decodeNormalizedBitmap(context: Context, sourceUri: Uri, maxDim: Int = 1200): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedViaImageDecoder = try {
                val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > maxDim && w > 0 && h > 0) {
                        val scale = maxDim.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Throwable) {
                null
            }
            if (decodedViaImageDecoder != null) return decodedViaImageDecoder
        }

        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (origW / sampleSize > maxDim * 2 || origH / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                }
            }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val orientation = readExifOrientation(context, sourceUri)
            val upright = applyExifTransform(decoded, orientation)

            val longest = max(upright.width, upright.height)
            if (longest > maxDim) {
                val scale = maxDim.toFloat() / longest.toFloat()
                val newW = (upright.width * scale).roundToInt().coerceAtLeast(1)
                val newH = (upright.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(upright, newW, newH, true).also {
                    if (it !== upright) upright.recycle()
                }
            } else {
                upright
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalizes a freshly captured camera JPEG file (bakes EXIF rotation + sRGB color profile)
     * into a clean JPEG so downstream previews and Coil render exact colors and upright framing.
     */
    fun normalizeCapturedFile(context: Context, sourceFile: File, maxDim: Int = 1200): File? {
        if (!sourceFile.exists()) return null
        val bmp = decodeNormalizedBitmap(context, Uri.fromFile(sourceFile), maxDim = maxDim) ?: return null
        return try {
            val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
            val normalizedFile = File(dir, "norm_${System.currentTimeMillis()}.jpg")
            FileOutputStream(normalizedFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bmp.recycle()
            normalizedFile
        } catch (_: Exception) {
            bmp.recycle()
            null
        }
    }

    fun readImageDimensions(file: File): Pair<Int, Int>? {
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compresses any picked or captured image to max [maxDim]px on longest side (EXIF upright + sRGB)
     * at high JPEG 90% quality so footwear photos stay crisp offline.
     */
    fun compressAndSavePhoto(context: Context, sourceUri: Uri, maxDim: Int = 1200): String? {
        return try {
            val scaled = decodeNormalizedBitmap(context, sourceUri, maxDim = maxDim) ?: return null
            val photoDir = File(context.filesDir, "product_photos").apply { mkdirs() }
            val outFile = File(photoDir, "shoe_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scaled.recycle()
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun compressAndSaveFile(context: Context, sourceFile: File, maxDim: Int = 1200): String? {
        return compressAndSavePhoto(context, Uri.fromFile(sourceFile), maxDim)
    }

    /**
     * Saves the shop logo as a lossless PNG in internal app storage so transparent backgrounds
     * and crisp typography remain sharp on frosted glass surfaces.
     */
    fun compressAndSaveLogo(context: Context, sourceUri: Uri, maxDim: Int = 512): String? {
        return try {
            val scaled = decodeNormalizedBitmap(context, sourceUri, maxDim = maxDim) ?: return null
            val logoDir = File(context.filesDir, "shop_branding").apply { mkdirs() }
            val outFile = File(logoDir, "logo_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            scaled.recycle()
            // Clean up older logos inside shop_branding so storage stays tidy
            logoDir.listFiles()?.forEach { f ->
                if (f.name != outFile.name && f.name.startsWith("logo_")) {
                    runCatching { f.delete() }
                }
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteLogoFile(path: String) {
        if (path.isBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }
}
