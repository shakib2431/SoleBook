package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.*
import com.example.data.repository.SoleBookRepository
import com.example.domain.onboarding.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class SpreadsheetInspectState(
    val cachedFile: File,
    val fileName: String,
    val fileHash: String,
    val preview: SpreadsheetPreview,
    val activeMapping: Map<Int, TargetField>,
    val duplicateBatch: ImportBatchEntity? = null
)

data class DataHubUiState(
    val importBatches: List<ImportBatchEntity> = emptyList(),
    val purchaseOrders: List<PurchaseOrderEntity> = emptyList(),
    val stockCountSessions: List<StockCountSessionEntity> = emptyList(),
    val locations: List<InventoryLocationEntity> = emptyList(),
    val recentMovements: List<InventoryMovementEntity> = emptyList(),
    val productImages: List<ProductImageEntity> = emptyList(),
    val spreadsheetInspect: SpreadsheetInspectState? = null,
    val imageImportReport: ImageImportReport? = null,
    val barcodeLookup: BarcodeLookupResult? = null,
    val duplicateConflict: DuplicateBarcodeConflict? = null,
    val docExtraction: DocumentExtractionResult? = null,
    val isBusy: Boolean = false,
    val busyMessage: String = ""
)

/**
 * Modular ViewModel controller encapsulating all 6 Universal Inventory Onboarding,
 * Staging/Approval, Barcode, Stock Count, and On-Device Document Intelligence workflows.
 */
class OnboardingHubController(
    private val application: Application,
    private val db: SoleBookDatabase,
    private val repository: SoleBookRepository,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit
) {
    private val dao = db.soleBookDao()
    private val onboardingDao = db.onboardingDao()
    private val stagingEngine = StagingAndCommitEngine(db, repository)
    private val stockCountService = StockCountService(db, repository)
    private val documentEngine: DocumentIntelligenceEngine = OnDeviceDocumentExtractor()

    private val _spreadsheetInspect = MutableStateFlow<SpreadsheetInspectState?>(null)
    private val _imageImportReport = MutableStateFlow<ImageImportReport?>(null)
    private val _barcodeLookup = MutableStateFlow<BarcodeLookupResult?>(null)
    private val _duplicateConflict = MutableStateFlow<DuplicateBarcodeConflict?>(null)
    private val _docExtraction = MutableStateFlow<DocumentExtractionResult?>(null)
    private val _busyState = MutableStateFlow(false to "")

    private val dbStreamsFlow = combine(
        repository.importBatchesFlow,
        repository.purchaseOrdersFlow,
        repository.stockCountSessionsFlow,
        repository.locationsFlow,
        repository.movementsFlow,
        repository.productImagesFlow
    ) { arr: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        DbBundle(
            batches = arr[0] as List<ImportBatchEntity>,
            purchaseOrders = arr[1] as List<PurchaseOrderEntity>,
            stockCounts = arr[2] as List<StockCountSessionEntity>,
            locations = arr[3] as List<InventoryLocationEntity>,
            movements = (arr[4] as List<InventoryMovementEntity>).take(120),
            images = arr[5] as List<ProductImageEntity>
        )
    }

    private val workflowStreamsFlow = combine(
        _spreadsheetInspect,
        _imageImportReport,
        _barcodeLookup,
        _duplicateConflict,
        _docExtraction,
        _busyState
    ) { arr: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        WorkflowBundle(
            spreadsheet = arr[0] as SpreadsheetInspectState?,
            imageReport = arr[1] as ImageImportReport?,
            barcode = arr[2] as BarcodeLookupResult?,
            dupConflict = arr[3] as DuplicateBarcodeConflict?,
            docExtraction = arr[4] as DocumentExtractionResult?,
            busy = arr[5] as Pair<Boolean, String>
        )
    }

    val state: StateFlow<DataHubUiState> = combine(dbStreamsFlow, workflowStreamsFlow) { d, w ->
        DataHubUiState(
            importBatches = d.batches,
            purchaseOrders = d.purchaseOrders,
            stockCountSessions = d.stockCounts,
            locations = d.locations,
            recentMovements = d.movements,
            productImages = d.images,
            spreadsheetInspect = w.spreadsheet,
            imageImportReport = w.imageReport,
            barcodeLookup = w.barcode,
            duplicateConflict = w.dupConflict,
            docExtraction = w.docExtraction,
            isBusy = w.busy.first,
            busyMessage = w.busy.second
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), DataHubUiState())

    private data class DbBundle(
        val batches: List<ImportBatchEntity>,
        val purchaseOrders: List<PurchaseOrderEntity>,
        val stockCounts: List<StockCountSessionEntity>,
        val locations: List<InventoryLocationEntity>,
        val movements: List<InventoryMovementEntity>,
        val images: List<ProductImageEntity>
    )

    private data class WorkflowBundle(
        val spreadsheet: SpreadsheetInspectState?,
        val imageReport: ImageImportReport?,
        val barcode: BarcodeLookupResult?,
        val dupConflict: DuplicateBarcodeConflict?,
        val docExtraction: DocumentExtractionResult?,
        val busy: Pair<Boolean, String>
    )

    fun observeBatch(batchId: Long): Flow<ImportBatchEntity?> =
        repository.importBatchesFlow.map { list -> list.firstOrNull { it.id == batchId } }

    fun observeBatchItems(batchId: Long): Flow<List<StagedImportItemEntity>> =
        onboardingDao.observeStagedItemsForBatch(batchId)

    fun observeStockCountLines(sessionId: Long): Flow<List<StockCountLineEntity>> =
        onboardingDao.observeStockCountLines(sessionId)

    // --- Part B: CSV / XLSX Templates & Streaming Import ---

    fun generateSpreadsheetTemplate(format: String, onReady: (File) -> Unit) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(application.cacheDir, "exports").apply { mkdirs() }
                if (format.equals("XLSX", ignoreCase = true)) {
                    val out = File(dir, "solebook_inventory_template.xlsx")
                    SpreadsheetCodec.writeImportTemplateXlsx(out)
                    out
                } else {
                    val out = File(dir, "solebook_inventory_template.csv")
                    out.writeText(SpreadsheetCodec.buildImportTemplateCsv(), Charsets.UTF_8)
                    out
                }
            }
            onReady(file)
        }
    }

    fun inspectSpreadsheetUri(uri: Uri) {
        scope.launch {
            _busyState.value = true to "Inspecting spreadsheet columns..."
            try {
                val inspect = withContext(Dispatchers.IO) {
                    val name = resolveDisplayName(uri) ?: "import_sheet.csv"
                    val dir = File(application.cacheDir, "imports").apply { mkdirs() }
                    val cached = File(dir, "sheet_${System.currentTimeMillis()}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                    application.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cached).use { out -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Cannot open selected spreadsheet")

                    val hash = SpreadsheetCodec.sha256OfFile(cached)
                    val preview = SpreadsheetCodec.inspectPreview(cached, name, maxSampleRows = 5)
                    val dupBatch = stagingEngine.checkExistingFileBatch(hash)
                    SpreadsheetInspectState(
                        cachedFile = cached,
                        fileName = name,
                        fileHash = hash,
                        preview = preview,
                        activeMapping = preview.suggestedMapping,
                        duplicateBatch = dupBatch
                    )
                }
                _spreadsheetInspect.value = inspect
            } catch (e: Exception) {
                onToast("Could not read spreadsheet: ${e.message ?: "unsupported format"}")
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun updateSpreadsheetMapping(columnIndex: Int, targetField: TargetField) {
        val current = _spreadsheetInspect.value ?: return
        val nextMap = current.activeMapping.toMutableMap()
        if (targetField == TargetField.IGNORE) {
            nextMap.remove(columnIndex)
        } else {
            nextMap[columnIndex] = targetField
        }
        _spreadsheetInspect.value = current.copy(activeMapping = nextMap)
    }

    fun clearSpreadsheetInspect() {
        _spreadsheetInspect.value = null
    }

    fun stageInspectedSpreadsheet(
        defaultCategory: String,
        defaultUnit: String,
        forceReimport: Boolean,
        onStaged: (batchId: Long) -> Unit
    ) {
        val inspect = _spreadsheetInspect.value ?: return
        scope.launch {
            _busyState.value = true to "Staging ${inspect.preview.estimatedTotalRows} rows for review..."
            try {
                val outcome = withContext(Dispatchers.IO) {
                    stagingEngine.stageSpreadsheet(
                        file = inspect.cachedFile,
                        fileName = inspect.fileName,
                        mapping = inspect.activeMapping,
                        defaultCategory = defaultCategory.ifBlank { "General" },
                        defaultUnit = defaultUnit.ifBlank { "pcs" },
                        forceReimport = forceReimport
                    )
                }
                if (outcome.isDuplicateUpload) {
                    onToast("This exact file was already uploaded (Batch #${outcome.existingBatchId}). Opening existing batch.")
                    _spreadsheetInspect.value = null
                    onStaged(outcome.batch.id)
                } else {
                    _spreadsheetInspect.value = null
                    onToast("Staged ${outcome.batch.validItems} valid, ${outcome.batch.invalidItems} flagged rows")
                    onStaged(outcome.batch.id)
                }
            } catch (e: Exception) {
                onToast("Spreadsheet staging failed: ${e.message}")
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    // --- Part C: Bulk Image ZIP + URL ---

    fun importImagesFromZipUri(zipUri: Uri, mappingCsvUri: Uri? = null) {
        scope.launch {
            _busyState.value = true to "Streaming & optimizing product images..."
            try {
                val report = withContext(Dispatchers.IO) {
                    val externalMaps = mutableListOf<ImageMappingSpec>()
                    if (mappingCsvUri != null) {
                        application.contentResolver.openInputStream(mappingCsvUri)?.use { csvIn ->
                            SpreadsheetCodec.streamCsv(csvIn) { idx, row ->
                                if (idx > 0 && row.size >= 2) {
                                    val sku = row.getOrNull(0).orEmpty().trim()
                                    val fname = row.getOrNull(1).orEmpty().trim()
                                    if (sku.isNotBlank() && fname.isNotBlank()) {
                                        externalMaps += ImageMappingSpec(
                                            imageFile = fname,
                                            skuOrBarcode = sku,
                                            variantKey = row.getOrNull(2).orEmpty().trim(),
                                            colorTag = row.getOrNull(3).orEmpty().trim(),
                                            isPrimary = row.getOrNull(4).orEmpty().equals("true", true)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    application.contentResolver.openInputStream(zipUri)?.use { zipIn ->
                        ProductImageManager.importImagesFromZipStream(
                            context = application,
                            zipInputStream = zipIn,
                            soleBookDao = dao,
                            onboardingDao = onboardingDao,
                            externalMappings = externalMaps
                        )
                    } ?: throw IllegalStateException("Cannot read selected ZIP archive")
                }
                _imageImportReport.value = report
                withContext(Dispatchers.Default) {
                    repository.indexFingerprintsForProducts(application)
                }
                onToast("Images: ${report.matchedCount} matched, ${report.duplicateSkippedCount} duplicates skipped, ${report.unmatchedFiles.size} unmatched")
            } catch (e: Exception) {
                onToast("Image ZIP import failed: ${e.message}")
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun clearImageImportReport() {
        _imageImportReport.value = null
    }

    fun attachImageUrlToProduct(productId: Long, url: String, variantKey: String = "", isPrimary: Boolean = true) {
        scope.launch {
            _busyState.value = true to "Downloading & optimizing image..."
            try {
                val saved = withContext(Dispatchers.IO) {
                    val prod = dao.getProductById(productId) ?: return@withContext null
                    ProductImageManager.downloadImageUrlForProduct(
                        context = application,
                        imageUrl = url,
                        product = prod,
                        variantKey = variantKey,
                        soleBookDao = dao,
                        onboardingDao = onboardingDao
                    )
                }
                if (saved != null) {
                    withContext(Dispatchers.Default) {
                        repository.indexFingerprintsForProducts(application)
                    }
                    onToast("Image saved locally and attached to product")
                } else {
                    onToast("Could not download valid image from URL")
                }
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    // --- Part D: Barcode Onboarding ---

    fun lookupBarcode(code: String, allowExternalLookup: Boolean) {
        scope.launch {
            _busyState.value = true to "Looking up barcode $code..."
            try {
                val res = BarcodeOnboardingService.lookupBarcode(
                    barcodeRaw = code,
                    allowExternalLookup = allowExternalLookup,
                    soleBookDao = dao,
                    onboardingDao = onboardingDao
                )
                _barcodeLookup.value = res
                val dup = BarcodeOnboardingService.checkDuplicateBarcode(code, null, dao, onboardingDao)
                _duplicateConflict.value = dup
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun clearBarcodeLookup() {
        _barcodeLookup.value = null
        _duplicateConflict.value = null
    }

    fun saveBarcodeProduct(
        product: ProductEntity,
        variantLabel: String,
        openingQty: Int,
        onSaved: (Long) -> Unit = {}
    ) {
        scope.launch {
            val dup = BarcodeOnboardingService.checkDuplicateBarcode(
                barcodeRaw = product.barcode,
                excludeProductId = product.id.takeIf { it > 0 },
                soleBookDao = dao,
                onboardingDao = onboardingDao
            )
            if (dup != null) {
                _duplicateConflict.value = dup
                onToast("Duplicate barcode! Already assigned to ${dup.existingSku} (${dup.existingProductName})")
                return@launch
            }
            val cleanVar = variantLabel.trim().ifBlank { "Standard" }
            val savedId = repository.saveProductWithVariants(product, mapOf(cleanVar to openingQty.coerceAtLeast(0)))
            if (openingQty > 0) {
                onboardingDao.insertMovementIgnore(
                    InventoryMovementEntity(
                        movementKey = "barcode:create:$savedId:${System.currentTimeMillis()}",
                        productId = savedId,
                        sizeLabel = cleanVar,
                        movementType = "OPENING",
                        qtyDelta = openingQty,
                        beforeQty = 0,
                        afterQty = openingQty,
                        unitCost = product.costPrice,
                        location = product.rackLocation,
                        sourceRoute = "BARCODE",
                        timestamp = System.currentTimeMillis(),
                        notes = "Added via Barcode Onboarding (${product.barcode})"
                    )
                )
            }
            onToast("${product.name} saved (${openingQty} stocked)")
            _barcodeLookup.value = null
            _duplicateConflict.value = null
            onSaved(savedId)
        }
    }

    fun quickRestockByBarcode(productId: Long, variantLabel: String, qtyAdded: Int, newUnitCost: Double) {
        scope.launch {
            val cleanVar = variantLabel.trim().ifBlank { "Standard" }
            repository.restockProduct(
                productId = productId,
                addedPerSize = mapOf(cleanVar to qtyAdded.coerceAtLeast(1)),
                newUnitCost = newUnitCost,
                supplier = "",
                notes = "Barcode quick restock"
            )
            onboardingDao.insertMovementIgnore(
                InventoryMovementEntity(
                    movementKey = "barcode:restock:$productId:${System.currentTimeMillis()}",
                    productId = productId,
                    sizeLabel = cleanVar,
                    movementType = "PURCHASE_RECEIPT",
                    qtyDelta = qtyAdded,
                    beforeQty = 0,
                    afterQty = qtyAdded,
                    unitCost = newUnitCost,
                    sourceRoute = "BARCODE",
                    timestamp = System.currentTimeMillis(),
                    notes = "Barcode quick restock"
                )
            )
            onToast("Added $qtyAdded units to stock")
            _barcodeLookup.value = null
        }
    }

    // --- Part E: Bulk Manual Entry & Physical Stock Count ---

    fun stageBulkManualDrafts(drafts: List<StagedRowDraft>, label: String, onStaged: (Long) -> Unit) {
        if (drafts.isEmpty()) return
        scope.launch {
            _busyState.value = true to "Staging ${drafts.size} manual entries..."
            try {
                val batch = withContext(Dispatchers.IO) {
                    stagingEngine.stageDrafts(
                        sourceRoute = "MANUAL_BULK",
                        fileName = label.ifBlank { "Bulk Manual Entry (${drafts.size} rows)" },
                        drafts = drafts
                    )
                }
                onToast("Staged ${batch.totalItems} rows (${batch.invalidItems} need attention)")
                onStaged(batch.id)
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun createStockCountSession(
        title: String,
        location: String,
        categoryFilter: String,
        prefillExpected: Boolean,
        onCreated: (Long) -> Unit
    ) {
        scope.launch {
            val sessionId = withContext(Dispatchers.IO) {
                stockCountService.createSession(title, location, categoryFilter, prefillExpected)
            }
            onToast("Stock count session created")
            onCreated(sessionId)
        }
    }

    fun scanBarcodeInStockCount(sessionId: Long, barcode: String, delta: Int = 1) {
        scope.launch {
            val matched = withContext(Dispatchers.IO) {
                stockCountService.incrementCountByBarcode(sessionId, barcode, delta)
            }
            if (matched != null) {
                onToast("Counted ${matched.productName} (${matched.variantLabel}) = ${matched.countedQty}")
            } else {
                onToast("Barcode '$barcode' not in this stock count session")
            }
        }
    }

    fun updateStockCountLineQty(line: StockCountLineEntity, newCount: Int) {
        scope.launch(Dispatchers.IO) {
            stockCountService.updateLineCount(line, newCount)
        }
    }

    fun toggleStockCountLineApproved(line: StockCountLineEntity, approved: Boolean) {
        scope.launch(Dispatchers.IO) {
            stockCountService.toggleLineApproved(line, approved)
        }
    }

    fun commitStockCountSession(sessionId: Long, onDone: (Int) -> Unit = {}) {
        scope.launch {
            _busyState.value = true to "Reconciling physical stock count..."
            try {
                val count = withContext(Dispatchers.IO) {
                    stockCountService.commitSession(sessionId)
                }
                onToast("Reconciled $count stock lines with audit ledger")
                onDone(count)
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    // --- Part F & G: Multilingual Document Intelligence ---

    fun extractDocuments(inputs: List<DocumentSourceInput>, catalogProducts: List<ProductEntity>) {
        if (inputs.isEmpty()) return
        scope.launch {
            _busyState.value = true to "Scanning pages & classifying retail actions..."
            try {
                val res = documentEngine.extractDocument(application, inputs, catalogProducts)
                _docExtraction.value = res
                if (res.lines.isEmpty()) {
                    onToast("No structured line items detected — inspect text or enter manually")
                } else {
                    onToast("Extracted ${res.lines.size} items (${res.documentTypeLabel})")
                }
            } catch (e: Exception) {
                onToast("Document extraction error: ${e.message}")
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun clearDocExtraction() {
        _docExtraction.value = null
    }

    fun stageDocExtraction(onStaged: (Long) -> Unit) {
        val doc = _docExtraction.value ?: return
        scope.launch {
            _busyState.value = true to "Preparing document items for review..."
            try {
                val existingDup = stagingEngine.checkExistingFileBatch(doc.docHash)
                if (existingDup != null && existingDup.status != "CANCELLED") {
                    _docExtraction.value = null
                    onToast("Document already staged (Batch #${existingDup.id})")
                    onStaged(existingDup.id)
                    return@launch
                }
                val batch = withContext(Dispatchers.IO) {
                    stagingEngine.stageDrafts(
                        sourceRoute = "AI_DOCUMENT",
                        fileName = "${doc.documentTypeLabel} (${doc.extractedTextByPage.size}p)",
                        fileHash = doc.docHash,
                        drafts = doc.toStagedDrafts()
                    )
                }
                _docExtraction.value = null
                onToast("Staged ${batch.totalItems} document lines for approval")
                onStaged(batch.id)
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    // --- Part H: Shared Staging Review, Approval, Commit, Error CSV, PO GRN & Locations ---

    fun updateStagedItem(item: StagedImportItemEntity) {
        scope.launch(Dispatchers.IO) {
            // Re-run lightweight validation on edited row
            val action = BusinessActionType.fromKey(item.actionType)
            val errs = mutableListOf<String>()
            if (item.productName.isBlank() && action !in listOf(
                    BusinessActionType.EXPENSE,
                    BusinessActionType.VENDOR_PAYMENT,
                    BusinessActionType.CUSTOMER_CREDIT
                )
            ) {
                errs += "Product name required"
            }
            if ((item.sellingPrice ?: 0.0) < 0 || (item.costPrice ?: 0.0) < 0) {
                errs += "Prices cannot be negative"
            }
            if (item.quantity < 0) {
                errs += "Quantity cannot be negative"
            }
            if (action in listOf(BusinessActionType.EXPENSE, BusinessActionType.CUSTOMER_CREDIT, BusinessActionType.VENDOR_PAYMENT) && item.amount <= 0) {
                errs += "Amount must be positive"
            }
            val cleaned = item.copy(validationErrors = errs.joinToString("; "))
            onboardingDao.updateStagedItem(cleaned)
        }
    }

    fun approveAllValidInBatch(batchId: Long) {
        scope.launch(Dispatchers.IO) {
            onboardingDao.approveAllValidInBatch(batchId, "APPROVED")
            onToast("Approved all valid rows in Batch #$batchId")
        }
    }

    fun commitBatch(batchId: Long, autoApproveValid: Boolean = false, onDone: (BatchCommitResult) -> Unit = {}) {
        scope.launch {
            _busyState.value = true to "Committing approved rows safely..."
            try {
                val res = withContext(Dispatchers.IO) {
                    stagingEngine.commitBatchChunked(
                        batchId = batchId,
                        autoApproveValid = autoApproveValid,
                        onProgress = { done, total ->
                            _busyState.value = true to "Committed $done / $total rows..."
                        }
                    )
                }
                if (res.createdProductIds.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        val prods = dao.getAllProductsSync().filter { it.id in res.createdProductIds }
                        repository.indexFingerprintsForProducts(application, prods)
                    }
                }
                onToast("Batch #$batchId: ${res.committedCount} committed, ${res.skippedCount} skipped, ${res.failedCount} failed")
                onDone(res)
            } catch (e: Exception) {
                onToast("Batch commit interrupted safely: ${e.message}")
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun exportBatchErrorCsv(batchId: Long, onReady: (File) -> Unit) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                stagingEngine.exportBatchErrorReport(application, batchId)
            }
            onReady(file)
        }
    }

    fun confirmPurchaseOrderReceived(poId: Long, paidAmount: Double? = null) {
        scope.launch {
            _busyState.value = true to "Receiving goods & updating weighted-average stock..."
            try {
                val ok = withContext(Dispatchers.IO) {
                    stagingEngine.confirmPurchaseOrderReceived(poId, paidAmount)
                }
                if (ok) {
                    onToast("Goods Received! Inventory & costs updated.")
                } else {
                    onToast("Purchase Order not found or already received")
                }
            } finally {
                _busyState.value = false to ""
            }
        }
    }

    fun addInventoryLocation(code: String, name: String, type: String) {
        scope.launch(Dispatchers.IO) {
            val cleanCode = code.trim().uppercase().ifBlank { "LOC-${System.currentTimeMillis().toString().takeLast(4)}" }
            onboardingDao.insertLocation(
                InventoryLocationEntity(
                    code = cleanCode,
                    name = name.trim().ifBlank { cleanCode },
                    locationType = type
                )
            )
            onToast("Location '$name' added")
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        runCatching {
            application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }
}
