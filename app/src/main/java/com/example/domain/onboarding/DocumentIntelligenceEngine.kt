package com.example.domain.onboarding

import android.content.Context
import android.net.Uri
import com.example.data.local.ProductEntity
import java.io.File

sealed class DocumentSourceInput {
    data class ImageFile(val file: File, val label: String = file.name) : DocumentSourceInput()
    data class ContentUri(val uri: Uri, val mimeType: String, val label: String) : DocumentSourceInput()
    data class PlainTextNote(val text: String, val label: String = "Typed / Pasted Note") : DocumentSourceInput()
}

data class ExtractedDocumentLine(
    val rowNumber: Int,
    val sourceDocPage: String,
    val actionType: BusinessActionType,
    val productName: String = "",
    val sku: String = "",
    val barcode: String = "",
    val category: String = "",
    val quantity: Double = 1.0,
    val unitLabel: String = "pcs",
    val variantLabel: String = "Standard",
    val costPrice: Double? = null,
    val sellingPrice: Double? = null,
    val mrp: Double? = null,
    val amount: Double = 0.0,
    val partyName: String = "",
    val partyPhone: String = "",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val confidence: Float = 0.7f,
    val rawLineText: String = "",
    val notes: String = ""
)

data class DocumentExtractionResult(
    val engineId: String,
    val isCloudAiBacked: Boolean,
    val documentTypeLabel: String,
    val detectedLanguages: List<String>,
    val overallConfidence: Float,
    val extractedTextByPage: List<Pair<String, String>>,
    val lines: List<ExtractedDocumentLine>,
    val warnings: List<String>,
    val docHash: String
) {
    fun toStagedDrafts(): List<StagedRowDraft> = lines.map { line ->
        StagedRowDraft(
            rowNumber = line.rowNumber,
            sourceDocPage = line.sourceDocPage,
            actionType = line.actionType,
            sku = line.sku,
            barcode = line.barcode,
            productName = line.productName,
            category = line.category,
            unitLabel = line.unitLabel,
            variantLabel = line.variantLabel,
            quantity = line.quantity,
            costPrice = line.costPrice,
            sellingPrice = line.sellingPrice,
            mrp = line.mrp,
            amount = line.amount,
            partyName = line.partyName,
            partyPhone = line.partyPhone,
            batchNumber = line.batchNumber,
            expiryDate = line.expiryDate,
            confidence = line.confidence,
            rawPayloadJson = "{\"raw\":\"${line.rawLineText.replace("\"", "'")}\"}",
            notes = line.notes
        )
    }
}

interface DocumentIntelligenceEngine {
    val engineId: String
    val isCloudAiBacked: Boolean
    val engineDescription: String

    suspend fun extractDocument(
        context: Context,
        inputs: List<DocumentSourceInput>,
        catalogProducts: List<ProductEntity>
    ): DocumentExtractionResult
}
