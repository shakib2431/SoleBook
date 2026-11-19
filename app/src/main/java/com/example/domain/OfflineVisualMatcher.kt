package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.data.local.ProductEntity
import com.example.data.local.ProductFingerprintEntity
import com.example.util.ImageUtils
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class VisualMatchResult(
    val product: ProductEntity,
    val similarityPercent: Int,
    val colorName: String,
    val isBestMatch: Boolean = false
)

data class VisualMatchSearchOutcome(
    val matches: List<VisualMatchResult>,
    val fallbackGuesses: List<VisualMatchResult>,
    val totalIndexedProducts: Int
)

object OfflineVisualMatcher {

    const val SIGNATURE_PREFIX = "v2:"
    const val VECTOR_LEN = 92
    const val DEFAULT_MIN_MATCH_PERCENT = 58
    private const val FALLBACK_MIN_PERCENT = 38
    private const val MAX_MATCHES = 5

    private const val SAMPLE_SIZE = 96

    // Channel A: 5x5 zero-mean structural luminance = 25 floats [0..24]
    private const val STRUCT_GRID = 5
    private const val STRUCT_LEN = STRUCT_GRID * STRUCT_GRID // 25

    // Channel B: 4x4 signed opponent color (RG, BY) = 32 floats [25..56]
    private const val COLOR_GRID = 4
    private const val SPATIAL_COLOR_LEN = COLOR_GRID * COLOR_GRID * 2 // 32

    // Channel C: 18-bin HSV (12 hues) + Achromatic (6 lightness) palette = 18 floats [57..74]
    private const val PALETTE_LEN = 18

    // Channel D: 4 quadrants x 4 edge orientations (0, 45, 90, 135) + 1 global edge density = 17 floats [75..91]
    private const val EDGE_LEN = 17

    private const val OFF_STRUCT = 0
    private const val OFF_SPATIAL_COLOR = OFF_STRUCT + STRUCT_LEN
    private const val OFF_PALETTE = OFF_SPATIAL_COLOR + SPATIAL_COLOR_LEN
    private const val OFF_EDGE = OFF_PALETTE + PALETTE_LEN

    fun decodeScaledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val raw = ImageUtils.decodeNormalizedBitmap(context, uri, maxDim = 640) ?: return null
            extractCenterSquareCrop(raw, 0.86f).also { if (it !== raw) raw.recycle() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a full upright query photo up to 640px without prematurely cropping edges,
     * so [extractQuerySignatures] can inspect multiple framing crops cleanly.
     */
    fun decodeQueryBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            ImageUtils.decodeNormalizedBitmap(context, uri, maxDim = 640)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeScaledBitmapFromProductPhoto(context: Context, photoUri: String): Bitmap? {
        if (photoUri.isBlank()) return null
        return try {
            val drawableRes = ImageUtils.resolveDrawableRes(photoUri)
            val raw = when {
                drawableRes != null -> decodeDrawableScaled(context, drawableRes, maxDim = 480)
                photoUri.startsWith("content://") || photoUri.startsWith("file://") -> {
                    ImageUtils.decodeNormalizedBitmap(context, Uri.parse(photoUri), maxDim = 640)
                }
                File(photoUri).exists() -> {
                    ImageUtils.decodeNormalizedBitmap(context, Uri.fromFile(File(photoUri)), maxDim = 640)
                }
                else -> null
            } ?: return null
            extractCenterSquareCrop(raw, 0.86f).also { if (it !== raw) raw.recycle() }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeDrawableScaled(context: Context, resId: Int, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(context.resources, resId, opts)
    }

    /**
     * Aspect-preserving center square crop scaled cleanly to [SAMPLE_SIZE]x[SAMPLE_SIZE].
     * Never stretches portrait 3:4 camera captures into distorted squares.
     */
    private fun extractCenterSquareCrop(src: Bitmap, cropFraction: Float): Bitmap {
        val baseSide = min(src.width, src.height)
        val side = (baseSide * cropFraction.coerceIn(0.3f, 1.0f)).roundToInt().coerceAtLeast(1)
        val startX = ((src.width - side) / 2).coerceAtLeast(0)
        val startY = ((src.height - side) / 2).coerceAtLeast(0)
        val cropped = if (side == src.width && side == src.height) {
            src
        } else {
            Bitmap.createBitmap(src, startX, startY, side, side)
        }
        val scaled = if (cropped.width == SAMPLE_SIZE && cropped.height == SAMPLE_SIZE) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, SAMPLE_SIZE, SAMPLE_SIZE, true)
        }
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }

    /**
     * Computes a 92-float normalized v2 descriptor + dominant color family name on an aspect-preserving
     * center square crop.
     */
    fun extractSignature(bitmap: Bitmap): Pair<FloatArray, String> {
        val square = if (bitmap.width == SAMPLE_SIZE && bitmap.height == SAMPLE_SIZE) {
            bitmap
        } else {
            extractCenterSquareCrop(bitmap, 0.86f)
        }
        val res = extractDescriptorFromSquare96(square)
        if (square !== bitmap) square.recycle()
        return res
    }

    /**
     * Extracts multiple center-framing crops (wide, medium, tight) plus horizontal mirrors
     * so products held slightly closer/farther or flipped left-vs-right still lock accurately.
     * Returned pairs carry (vector, orientationPenaltyFactor).
     */
    fun extractQuerySignatures(bitmap: Bitmap): Pair<List<Pair<FloatArray, Float>>, String> {
        val crops = listOf(0.88f, 0.70f, 0.54f)
        val views = mutableListOf<Pair<FloatArray, Float>>()
        var primaryColor = "Classic"

        crops.forEachIndexed { idx, frac ->
            val crop = extractCenterSquareCrop(bitmap, frac)
            val (vec, domColor) = extractDescriptorFromSquare96(crop)
            crop.recycle()
            if (idx == 1) primaryColor = domColor // medium center crop best avoids edge background for label
            else if (idx == 0 && primaryColor == "Classic") primaryColor = domColor

            views += vec to 1.0f
            views += mirrorDescriptorHorizontally(vec) to 0.97f
        }
        return views to primaryColor
    }

    private fun extractDescriptorFromSquare96(square: Bitmap): Pair<FloatArray, String> {
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        square.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)

        val lum = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)
        val rg = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)
        val by = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)
        val hsvArr = FloatArray(3)
        val hueArr = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)
        val satArr = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)
        val valArr = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            lum[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            rg[i] = (r - g) / 255f
            by[i] = (2 * b - r - g) / 510f
            Color.colorToHSV(px, hsvArr)
            hueArr[i] = hsvArr[0]
            satArr[i] = hsvArr[1]
            valArr[i] = hsvArr[2]
        }

        // Estimate outer border background color to softly suppress plain studio/table/wall margins
        var bgR = 0f
        var bgG = 0f
        var bgB = 0f
        var bgCount = 0
        for (y in 0 until SAMPLE_SIZE) {
            for (x in 0 until SAMPLE_SIZE) {
                if (x < 5 || x >= SAMPLE_SIZE - 5 || y < 5 || y >= SAMPLE_SIZE - 5) {
                    val px = pixels[y * SAMPLE_SIZE + x]
                    bgR += Color.red(px)
                    bgG += Color.green(px)
                    bgB += Color.blue(px)
                    bgCount++
                }
            }
        }
        bgR /= bgCount.coerceAtLeast(1)
        bgG /= bgCount.coerceAtLeast(1)
        bgB /= bgCount.coerceAtLeast(1)

        var borderVar = 0f
        for (y in 0 until SAMPLE_SIZE) {
            for (x in 0 until SAMPLE_SIZE) {
                if (x < 5 || x >= SAMPLE_SIZE - 5 || y < 5 || y >= SAMPLE_SIZE - 5) {
                    val px = pixels[y * SAMPLE_SIZE + x]
                    val dr = Color.red(px) - bgR
                    val dg = Color.green(px) - bgG
                    val db = Color.blue(px) - bgB
                    borderVar += sqrt(dr * dr + dg * dg + db * db)
                }
            }
        }
        val avgBorderDiff = borderVar / bgCount.coerceAtLeast(1)
        val hasUniformBorder = avgBorderDiff < 26f

        val fgWeight = FloatArray(SAMPLE_SIZE * SAMPLE_SIZE)
        for (y in 0 until SAMPLE_SIZE) {
            for (x in 0 until SAMPLE_SIZE) {
                val i = y * SAMPLE_SIZE + x
                val px = pixels[i]
                // Gentle Gaussian-like center priority (edges slightly down-weighted)
                val nx = (x - SAMPLE_SIZE / 2f) / (SAMPLE_SIZE / 2f)
                val ny = (y - SAMPLE_SIZE / 2f) / (SAMPLE_SIZE / 2f)
                val radial = (1.15f - 0.35f * (nx * nx + ny * ny)).coerceIn(0.45f, 1.15f)
                val bgSuppress = if (hasUniformBorder) {
                    val dr = Color.red(px) - bgR
                    val dg = Color.green(px) - bgG
                    val db = Color.blue(px) - bgB
                    val dist = sqrt(dr * dr + dg * dg + db * db)
                    (0.18f + 0.82f * (dist / 42f).coerceIn(0f, 1f))
                } else {
                    1f
                }
                fgWeight[i] = radial * bgSuppress
            }
        }

        val vec = FloatArray(VECTOR_LEN)

        // --- Channel A: 5x5 Zero-Mean Structural Luminance Layout (25 floats) ---
        val structRaw = FloatArray(STRUCT_LEN)
        var structMean = 0f
        for (gy in 0 until STRUCT_GRID) {
            val y0 = (gy * SAMPLE_SIZE) / STRUCT_GRID
            val y1 = ((gy + 1) * SAMPLE_SIZE) / STRUCT_GRID
            for (gx in 0 until STRUCT_GRID) {
                val x0 = (gx * SAMPLE_SIZE) / STRUCT_GRID
                val x1 = ((gx + 1) * SAMPLE_SIZE) / STRUCT_GRID
                var sumL = 0f
                var cnt = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        sumL += lum[y * SAMPLE_SIZE + x]
                        cnt++
                    }
                }
                val avg = sumL / cnt.coerceAtLeast(1)
                structRaw[gy * STRUCT_GRID + gx] = avg
                structMean += avg
            }
        }
        structMean /= STRUCT_LEN
        var structNormSq = 0f
        for (i in 0 until STRUCT_LEN) {
            val centered = structRaw[i] - structMean
            structRaw[i] = centered
            structNormSq += centered * centered
        }
        if (structNormSq > 1e-5f) {
            val invNorm = 1f / sqrt(structNormSq)
            for (i in 0 until STRUCT_LEN) {
                vec[OFF_STRUCT + i] = structRaw[i] * invNorm
            }
        }

        // --- Channel B: 4x4 Foreground-Weighted Opponent Color Layout (32 floats) ---
        val spatialRaw = FloatArray(SPATIAL_COLOR_LEN)
        var spNormSq = 0f
        for (gy in 0 until COLOR_GRID) {
            val y0 = (gy * SAMPLE_SIZE) / COLOR_GRID
            val y1 = ((gy + 1) * SAMPLE_SIZE) / COLOR_GRID
            for (gx in 0 until COLOR_GRID) {
                val x0 = (gx * SAMPLE_SIZE) / COLOR_GRID
                val x1 = ((gx + 1) * SAMPLE_SIZE) / COLOR_GRID
                var sumRg = 0f
                var sumBy = 0f
                var wSum = 0f
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val idx = y * SAMPLE_SIZE + x
                        val w = fgWeight[idx]
                        sumRg += rg[idx] * w
                        sumBy += by[idx] * w
                        wSum += w
                    }
                }
                val cellIdx = (gy * COLOR_GRID + gx) * 2
                val avgRg = sumRg / wSum.coerceAtLeast(1e-4f)
                val avgBy = sumBy / wSum.coerceAtLeast(1e-4f)
                spatialRaw[cellIdx] = avgRg
                spatialRaw[cellIdx + 1] = avgBy
                spNormSq += avgRg * avgRg + avgBy * avgBy
            }
        }
        // Only normalize spatial opponent color if actual chroma signal exists (otherwise keep 0 so achromatic items know they are achromatic)
        val spNorm = sqrt(spNormSq)
        if (spNorm > 0.04f) {
            for (i in 0 until SPATIAL_COLOR_LEN) {
                vec[OFF_SPATIAL_COLOR + i] = spatialRaw[i] / spNorm
            }
        }

        // --- Channel C: Foreground-Weighted 18-Bin HSV + Achromatic Palette (18 floats, L1) ---
        val palette = FloatArray(PALETTE_LEN)
        var paletteSum = 0f
        for (y in 4 until SAMPLE_SIZE - 4) {
            for (x in 4 until SAMPLE_SIZE - 4) {
                val i = y * SAMPLE_SIZE + x
                val h = hueArr[i]
                val s = satArr[i]
                val v = valArr[i]
                val w = fgWeight[i]
                val bin: Int
                val weight: Float
                if (s >= 0.17f && v >= 0.16f) {
                    bin = ((((h + 15f) % 360f) / 30f).toInt()).coerceIn(0, 11)
                    weight = w * (0.45f + 0.55f * s)
                } else {
                    bin = when {
                        v < 0.22f -> 12 // Black
                        v < 0.42f -> 13 // Charcoal / Dark Grey
                        v < 0.66f -> 14 // Mid Grey
                        v < 0.84f -> 15 // Light Grey
                        s >= 0.07f -> 16 // Cream / Off-white
                        else -> 17 // Pure White
                    }
                    weight = w * 0.75f
                }
                palette[bin] += weight
                paletteSum += weight
            }
        }
        val pNorm = paletteSum.coerceAtLeast(1e-4f)
        for (b in 0 until PALETTE_LEN) {
            vec[OFF_PALETTE + b] = palette[b] / pNorm
        }

        // --- Channel D: 4-Quadrant 4-Orientation Sobel Edge Histogram + Edge Density (17 floats) ---
        val edgeVec = FloatArray(EDGE_LEN)
        var totalGradMag = 0f
        var gradSamples = 0
        val half = SAMPLE_SIZE / 2
        for (y in 1 until SAMPLE_SIZE - 1) {
            val qy = if (y < half) 0 else 1
            for (x in 1 until SAMPLE_SIZE - 1) {
                val qx = if (x < half) 0 else 1
                val quad = qy * 2 + qx

                val gx = (
                    lum[(y - 1) * SAMPLE_SIZE + (x + 1)] + 2f * lum[y * SAMPLE_SIZE + (x + 1)] + lum[(y + 1) * SAMPLE_SIZE + (x + 1)]
                    ) - (
                    lum[(y - 1) * SAMPLE_SIZE + (x - 1)] + 2f * lum[y * SAMPLE_SIZE + (x - 1)] + lum[(y + 1) * SAMPLE_SIZE + (x - 1)]
                    )
                val gy = (
                    lum[(y + 1) * SAMPLE_SIZE + (x - 1)] + 2f * lum[(y + 1) * SAMPLE_SIZE + x] + lum[(y + 1) * SAMPLE_SIZE + (x + 1)]
                    ) - (
                    lum[(y - 1) * SAMPLE_SIZE + (x - 1)] + 2f * lum[(y - 1) * SAMPLE_SIZE + x] + lum[(y - 1) * SAMPLE_SIZE + (x + 1)]
                    )

                val mag = hypot(gx, gy) * fgWeight[y * SAMPLE_SIZE + x]
                totalGradMag += mag
                gradSamples++

                if (mag > 0.04f) {
                    var deg = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat()
                    if (deg < 0f) deg += 180f
                    if (deg >= 180f) deg -= 180f
                    val binPos = (deg / 45f) % 4f
                    val b0 = binPos.toInt().coerceIn(0, 3)
                    val b1 = (b0 + 1) % 4
                    val frac = binPos - b0
                    edgeVec[quad * 4 + b0] += mag * (1f - frac)
                    edgeVec[quad * 4 + b1] += mag * frac
                }
            }
        }
        val avgDensity = ((totalGradMag / gradSamples.coerceAtLeast(1)) * 2.2f).coerceIn(0f, 1f)
        var edgeNormSq = 0f
        for (i in 0 until 16) edgeNormSq += edgeVec[i] * edgeVec[i]
        val edgeNorm = sqrt(edgeNormSq)
        if (edgeNorm > 1e-4f) {
            for (i in 0 until 16) vec[OFF_EDGE + i] = edgeVec[i] / edgeNorm
        }
        vec[OFF_EDGE + 16] = avgDensity

        val dominantColor = inferDominantColorName(palette)
        return vec to dominantColor
    }

    private fun mirrorDescriptorHorizontally(src: FloatArray): FloatArray {
        if (src.size != VECTOR_LEN) return src
        val out = src.copyOf()

        // Mirror Channel A (5x5 structural grid)
        for (gy in 0 until STRUCT_GRID) {
            for (gx in 0 until STRUCT_GRID) {
                val mx = STRUCT_GRID - 1 - gx
                out[OFF_STRUCT + gy * STRUCT_GRID + mx] = src[OFF_STRUCT + gy * STRUCT_GRID + gx]
            }
        }

        // Mirror Channel B (4x4 spatial opponent color)
        for (gy in 0 until COLOR_GRID) {
            for (gx in 0 until COLOR_GRID) {
                val mx = COLOR_GRID - 1 - gx
                val srcIdx = OFF_SPATIAL_COLOR + (gy * COLOR_GRID + gx) * 2
                val dstIdx = OFF_SPATIAL_COLOR + (gy * COLOR_GRID + mx) * 2
                out[dstIdx] = src[srcIdx]
                out[dstIdx + 1] = src[srcIdx + 1]
            }
        }

        // Mirror Channel D (quadrants swap L <-> R, orientation bins 45° <-> 135° swap)
        for (qy in 0 until 2) {
            for (qx in 0 until 2) {
                val mqx = 1 - qx
                val srcQ = OFF_EDGE + (qy * 2 + qx) * 4
                val dstQ = OFF_EDGE + (qy * 2 + mqx) * 4
                out[dstQ + 0] = src[srcQ + 0] // 0° -> 0°
                out[dstQ + 1] = src[srcQ + 3] // 45° -> 135°
                out[dstQ + 2] = src[srcQ + 2] // 90° -> 90°
                out[dstQ + 3] = src[srcQ + 1] // 135° -> 45°
            }
        }

        return out
    }

    private fun inferDominantColorName(palette: FloatArray): String {
        val chromaticSum = (0 until 12).sumOf { palette[it].toDouble() }.toFloat()
        val whiteCream = palette[16] + palette[17]
        val greySum = palette[13] + palette[14] + palette[15]
        val blackSum = palette[12]

        // Pick dominant hue if meaningful colorful signal exists
        val topHueIdx = (0 until 12).maxByOrNull { palette[it] } ?: 1
        val topHueWeight = palette[topHueIdx]
        if (chromaticSum > 0.22f && topHueWeight >= blackSum * 0.55f) {
            return when (topHueIdx) {
                0, 11 -> "Red"
                1 -> "Tan"
                2 -> "Yellow"
                3, 4, 5 -> "Green"
                6 -> "Teal"
                7, 8 -> "Navy"
                9, 10 -> "Purple"
                else -> "Classic"
            }
        }

        return when {
            blackSum >= greySum && blackSum >= whiteCream -> "Black"
            whiteCream >= greySum && whiteCream >= blackSum -> "White"
            else -> "Grey"
        }
    }

    fun encodeVector(vec: FloatArray): String {
        val body = vec.joinToString(",") { "%.4f".format(Locale.US, it) }
        return "$SIGNATURE_PREFIX$body"
    }

    fun decodeVector(csv: String): FloatArray {
        if (!csv.startsWith(SIGNATURE_PREFIX)) return FloatArray(0)
        val raw = csv.removePrefix(SIGNATURE_PREFIX)
        if (raw.isBlank()) return FloatArray(0)
        val parts = raw.split(",")
        if (parts.size != VECTOR_LEN) return FloatArray(0)
        val out = FloatArray(VECTOR_LEN)
        for (i in 0 until VECTOR_LEN) {
            out[i] = parts[i].toFloatOrNull() ?: return FloatArray(0)
        }
        return out
    }

    /**
     * Calibrated visual similarity in [0f, 1f] combining palette Bhattacharyya overlap,
     * opponent spatial color, zero-mean spatial structure, and quadrant edge orientation.
     * Color and structural shape mutually gate each other so single-cue accidents do not score high.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != VECTOR_LEN || b.size != VECTOR_LEN) return 0f

        // 1. Channel C: 18-bin Palette Bhattacharyya overlap [0..1]
        var bhattacharyya = 0f
        for (i in 0 until PALETTE_LEN) {
            val pa = a[OFF_PALETTE + i].coerceAtLeast(0f)
            val pb = b[OFF_PALETTE + i].coerceAtLeast(0f)
            bhattacharyya += sqrt(pa * pb)
        }
        val paletteSim = bhattacharyya.coerceIn(0f, 1f)

        // 2. Channel B: 4x4 Signed Opponent Spatial Color similarity [0..1]
        var spNormA = 0f
        var spNormB = 0f
        var spDot = 0f
        for (i in 0 until SPATIAL_COLOR_LEN) {
            val va = a[OFF_SPATIAL_COLOR + i]
            val vb = b[OFF_SPATIAL_COLOR + i]
            spNormA += va * va
            spNormB += vb * vb
            spDot += va * vb
        }
        val bothAchromatic = spNormA < 0.10f && spNormB < 0.10f
        val oneAchromatic = (spNormA < 0.10f) xor (spNormB < 0.10f)
        val spatialColorSim = when {
            bothAchromatic -> paletteSim // Rely on exact neutral black/grey/white lightness match
            oneAchromatic -> 0.12f * paletteSim // Colorful vs neutral item strongly penalized
            else -> spDot.coerceIn(0f, 1f)
        }

        // 3. Channel A: 5x5 Zero-Mean Structural Luminance correlation [0..1]
        var stNormA = 0f
        var stNormB = 0f
        var stDot = 0f
        for (i in 0 until STRUCT_LEN) {
            val va = a[OFF_STRUCT + i]
            val vb = b[OFF_STRUCT + i]
            stNormA += va * va
            stNormB += vb * vb
            stDot += va * vb
        }
        val bothFlat = stNormA < 0.05f && stNormB < 0.05f
        val oneFlat = (stNormA < 0.05f) xor (stNormB < 0.05f)
        val structSim = when {
            bothFlat -> paletteSim
            oneFlat -> 0.20f
            else -> stDot.coerceIn(0f, 1f)
        }

        // 4. Channel D: 4-Quadrant Edge Orientation similarity [0..1]
        var edgeNormA = 0f
        var edgeNormB = 0f
        var edgeDot = 0f
        for (i in 0 until 16) {
            val va = a[OFF_EDGE + i]
            val vb = b[OFF_EDGE + i]
            edgeNormA += va * va
            edgeNormB += vb * vb
            edgeDot += va * vb
        }
        val densityDiff = abs(a[OFF_EDGE + 16] - b[OFF_EDGE + 16])
        val edgeSim = when {
            edgeNormA < 0.05f && edgeNormB < 0.05f -> (1f - densityDiff).coerceIn(0f, 1f)
            (edgeNormA < 0.05f) xor (edgeNormB < 0.05f) -> 0.25f
            else -> {
                // Contrast-stretch raw positive-quadrant cosine slightly so uniform distributions stay discriminative
                val rawEdge = ((edgeDot - 0.30f) / 0.70f).coerceIn(0f, 1f)
                (rawEdge * (1f - 0.35f * densityDiff)).coerceIn(0f, 1f)
            }
        }

        val colorGate = (0.58f * paletteSim + 0.42f * spatialColorSim).coerceIn(0f, 1f)
        val shapeGate = (0.62f * structSim + 0.38f * edgeSim).coerceIn(0f, 1f)

        val avgGate = (colorGate + shapeGate) * 0.5f
        val harmonicGate = if (colorGate + shapeGate > 1e-4f) {
            (2f * colorGate * shapeGate) / (colorGate + shapeGate)
        } else 0f

        return (0.50f * avgGate + 0.50f * harmonicGate).coerceIn(0f, 1f)
    }

    /**
     * Precision search comparing multi-crop query views against all indexed active products.
     * Unphotographed products are excluded, and low-confidence wrong items are strictly filtered out.
     */
    fun findBestMatches(
        queryViews: List<Pair<FloatArray, Float>>,
        queryDominantColor: String,
        products: List<ProductEntity>,
        fingerprints: Map<Long, ProductFingerprintEntity>,
        categoryFilter: String? = null,
        minMatchPercent: Int = DEFAULT_MIN_MATCH_PERCENT
    ): VisualMatchSearchOutcome {
        if (queryViews.isEmpty()) {
            return VisualMatchSearchOutcome(emptyList(), emptyList(), 0)
        }

        val activeProducts = products
            .filter { !it.isArchived }
            .filter { categoryFilter == null || categoryFilter == "All" || it.category == categoryFilter }

        val scored = mutableListOf<VisualMatchResult>()
        var indexedCount = 0

        for (product in activeProducts) {
            val fp = fingerprints[product.id] ?: continue
            val targetVec = decodeVector(fp.signatureCsv)
            if (targetVec.size != VECTOR_LEN) continue
            indexedCount++

            var bestSim = 0f
            for ((qVec, orientFactor) in queryViews) {
                val sim = cosineSimilarity(qVec, targetVec) * orientFactor
                if (sim > bestSim) bestSim = sim
            }

            val productColor = product.colorTag.ifBlank { fp.dominantColor }
            // Tiny tie-breaker only if baseline visual similarity already shows plausible overlap
            val colorNudge = if (bestSim >= 0.48f && productColor.equals(queryDominantColor, ignoreCase = true)) {
                0.03f
            } else 0f

            val percent = ((bestSim + colorNudge) * 100f).roundToInt().coerceIn(0, 99)
            scored += VisualMatchResult(
                product = product,
                similarityPercent = percent,
                colorName = productColor.ifBlank { "Classic" },
                isBestMatch = false
            )
        }

        val sorted = scored.sortedByDescending { it.similarityPercent }
        val topScore = sorted.firstOrNull()?.similarityPercent ?: 0
        val secondScore = sorted.getOrNull(1)?.similarityPercent ?: 0

        // Relative drop-off cutoff prevents distant trailing items from cluttering a crisp match
        val effectiveMin = if (topScore >= 74) {
            max(minMatchPercent, topScore - 18)
        } else {
            minMatchPercent
        }

        val filtered = sorted
            .filter { it.similarityPercent >= effectiveMin }
            .take(MAX_MATCHES)
            .mapIndexed { idx, res ->
                val markBest = idx == 0 && res.similarityPercent >= 66 &&
                    (sorted.size == 1 || res.similarityPercent - secondScore >= 5 || res.similarityPercent >= 78)
                res.copy(isBestMatch = markBest)
            }

        val fallbacks = if (filtered.isEmpty()) {
            sorted.filter { it.similarityPercent in FALLBACK_MIN_PERCENT until minMatchPercent }.take(2)
        } else emptyList()

        return VisualMatchSearchOutcome(
            matches = filtered,
            fallbackGuesses = fallbacks,
            totalIndexedProducts = indexedCount
        )
    }

    /**
     * Convenience wrapper returning strictly filtered matching products.
     */
    fun rankMatches(
        queryVec: FloatArray,
        queryDominantColor: String,
        products: List<ProductEntity>,
        fingerprints: Map<Long, ProductFingerprintEntity>,
        categoryFilter: String? = null,
        minMatchPercent: Int = DEFAULT_MIN_MATCH_PERCENT
    ): List<VisualMatchResult> {
        val views = listOf(
            queryVec to 1.0f,
            mirrorDescriptorHorizontally(queryVec) to 0.97f
        )
        return findBestMatches(
            queryViews = views,
            queryDominantColor = queryDominantColor,
            products = products,
            fingerprints = fingerprints,
            categoryFilter = categoryFilter,
            minMatchPercent = minMatchPercent
        ).matches
    }
}
