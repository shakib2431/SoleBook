package com.example.domain.onboarding

import com.example.data.local.OnboardingDao
import com.example.data.local.ProductEntity
import com.example.data.local.ProductVariantEntity
import com.example.data.local.SoleBookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class BarcodeLookupResult {
    data class MatchedCatalog(
        val product: ProductEntity,
        val matchedVariant: ProductVariantEntity?,
        val allVariants: List<ProductVariantEntity>
    ) : BarcodeLookupResult()

    data class ExternalCatalogHit(
        val barcode: String,
        val suggestedName: String,
        val suggestedBrand: String,
        val suggestedCategory: String,
        val suggestedPackOrUnit: String,
        val imageUrl: String,
        val sourceAttribution: String
    ) : BarcodeLookupResult()

    data class UnknownBarcode(
        val barcode: String,
        val reason: String
    ) : BarcodeLookupResult()
}

data class DuplicateBarcodeConflict(
    val barcode: String,
    val existingProductId: Long,
    val existingSku: String,
    val existingProductName: String,
    val existingVariantLabel: String?
)

/**
 * Offline-first barcode lookup + duplicate guard + optional transparent external product metadata lookup.
 * Strictly never fabricates product names or prices when a barcode is unknown.
 */
object BarcodeOnboardingService {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lookupLocalCatalog(
        barcodeRaw: String,
        soleBookDao: SoleBookDao,
        onboardingDao: OnboardingDao
    ): BarcodeLookupResult.MatchedCatalog? {
        val code = barcodeRaw.trim()
        if (code.isEmpty()) return null

        onboardingDao.getVariantByBarcode(code)?.let { v ->
            val p = soleBookDao.getProductById(v.productId) ?: return@let
            val vars = soleBookDao.getVariantsForProduct(p.id)
            return BarcodeLookupResult.MatchedCatalog(p, v, vars)
        }
        onboardingDao.getProductByBarcode(code)?.let { p ->
            val vars = soleBookDao.getVariantsForProduct(p.id)
            return BarcodeLookupResult.MatchedCatalog(p, vars.firstOrNull(), vars)
        }
        onboardingDao.getVariantBySku(code)?.let { v ->
            val p = soleBookDao.getProductById(v.productId) ?: return@let
            val vars = soleBookDao.getVariantsForProduct(p.id)
            return BarcodeLookupResult.MatchedCatalog(p, v, vars)
        }
        onboardingDao.getProductBySku(code)?.let { p ->
            val vars = soleBookDao.getVariantsForProduct(p.id)
            return BarcodeLookupResult.MatchedCatalog(p, vars.firstOrNull(), vars)
        }
        return null
    }

    suspend fun checkDuplicateBarcode(
        barcodeRaw: String,
        excludeProductId: Long?,
        soleBookDao: SoleBookDao,
        onboardingDao: OnboardingDao
    ): DuplicateBarcodeConflict? {
        val code = barcodeRaw.trim()
        if (code.isEmpty()) return null

        onboardingDao.getProductByBarcode(code)?.let { p ->
            if (p.id != excludeProductId) {
                return DuplicateBarcodeConflict(
                    barcode = code,
                    existingProductId = p.id,
                    existingSku = p.skuCode,
                    existingProductName = p.name,
                    existingVariantLabel = null
                )
            }
        }
        onboardingDao.getVariantByBarcode(code)?.let { v ->
            if (v.productId != excludeProductId) {
                val p = soleBookDao.getProductById(v.productId)
                return DuplicateBarcodeConflict(
                    barcode = code,
                    existingProductId = v.productId,
                    existingSku = p?.skuCode ?: "",
                    existingProductName = p?.name ?: "Product #${v.productId}",
                    existingVariantLabel = v.size
                )
            }
        }
        return null
    }

    /**
     * Optional online lookup via Open Food Facts public v2 API.
     * Returns only verified metadata if status == 1 and product_name is non-empty; NEVER fabricates prices or items.
     */
    suspend fun lookupBarcode(
        barcodeRaw: String,
        allowExternalLookup: Boolean,
        soleBookDao: SoleBookDao,
        onboardingDao: OnboardingDao
    ): BarcodeLookupResult = withContext(Dispatchers.IO) {
        val code = barcodeRaw.trim()
        if (code.isEmpty()) {
            return@withContext BarcodeLookupResult.UnknownBarcode(code, "Empty barcode")
        }
        lookupLocalCatalog(code, soleBookDao, onboardingDao)?.let { return@withContext it }

        if (!allowExternalLookup) {
            return@withContext BarcodeLookupResult.UnknownBarcode(code, "Not found in local catalog")
        }

        val url = "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,categories,quantity,image_front_small_url"
        val jsonStr = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SoleBookRetail/1.0 (Android)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext BarcodeLookupResult.UnknownBarcode(
            code,
            "Not in local catalog and online lookup unavailable (offline or unreachable)"
        )

        val root = runCatching { JSONObject(jsonStr) }.getOrNull()
            ?: return@withContext BarcodeLookupResult.UnknownBarcode(code, "Unrecognized external response")
        if (root.optInt("status", 0) != 1) {
            return@withContext BarcodeLookupResult.UnknownBarcode(code, "Unknown barcode (not found in external catalog)")
        }
        val prodObj = root.optJSONObject("product")
            ?: return@withContext BarcodeLookupResult.UnknownBarcode(code, "No product record returned")
        val name = prodObj.optString("product_name", "").trim()
        if (name.isEmpty()) {
            return@withContext BarcodeLookupResult.UnknownBarcode(code, "Incomplete external record; enter details manually")
        }
        val brand = prodObj.optString("brands", "").split(',').firstOrNull()?.trim().orEmpty()
        val category = prodObj.optString("categories", "").split(',').firstOrNull()?.trim().orEmpty()
        val qty = prodObj.optString("quantity", "").trim()
        val imgUrl = prodObj.optString("image_front_small_url", "").trim()

        BarcodeLookupResult.ExternalCatalogHit(
            barcode = code,
            suggestedName = name,
            suggestedBrand = brand,
            suggestedCategory = category,
            suggestedPackOrUnit = qty,
            imageUrl = imgUrl,
            sourceAttribution = "Open Food Facts (verified online; confirm prices manually)"
        )
    }
}
