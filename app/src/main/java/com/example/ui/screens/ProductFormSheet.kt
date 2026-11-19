package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.data.local.VendorEntity
import com.example.domain.BusinessCategory
import com.example.domain.IndustryConfig
import com.example.domain.IndustryProfile
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.CameraCaptureDialog
import com.example.ui.components.FormField
import com.example.ui.components.ProductPhoto
import com.example.ui.components.SkuBoxTag
import com.example.ui.theme.*
import com.example.util.Formatters
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditProductSheet(
    initialProduct: ProductEntity?,
    initialSizes: Map<String, Int>,
    autoSku: String,
    currency: String,
    industryConfig: IndustryConfig = IndustryProfile.resolve("GENERAL"),
    vendors: List<VendorEntity> = emptyList(),
    initialVendorId: Long? = initialProduct?.vendorId,
    initialCategory: String? = null,
    initialGender: String? = null,
    showLotPaymentFields: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ProductEntity, Map<String, Int>) -> Unit,
    onSaveWithPayment: ((ProductEntity, Map<String, Int>, Double?, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val categories = remember(industryConfig) { industryConfig.suggestedCategories }
    val audienceOptions = remember(industryConfig) { industryConfig.audienceSegmentOptions }
    val colorOptions = listOf("Black", "White", "Brown", "Tan", "Blue", "Grey", "Red", "Green")
    val styleOptions = listOf("Classic", "Sport", "Casual", "Premium", "Formal", "Pack")

    val defaultCat = categories.firstOrNull() ?: "General"
    val startCategory = initialProduct?.category ?: initialCategory ?: defaultCat
    val startVendor = vendors.firstOrNull { it.id == (initialVendorId ?: initialProduct?.vendorId) }

    var skuCode by remember { mutableStateOf(initialProduct?.skuCode ?: autoSku) }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(startCategory) }
    var customCategory by remember {
        mutableStateOf(if (startCategory !in categories) startCategory else "")
    }
    var brand by remember { mutableStateOf(initialProduct?.brand ?: "") }
    var gender by remember {
        mutableStateOf(initialProduct?.gender ?: initialGender ?: audienceOptions.firstOrNull() ?: "Standard")
    }
    var colorTag by remember { mutableStateOf(initialProduct?.colorTag ?: "") }
    var styleTag by remember { mutableStateOf(initialProduct?.styleTag ?: "") }
    var rackLocation by remember { mutableStateOf(initialProduct?.rackLocation ?: "") }
    var photoUri by remember {
        mutableStateOf(
            initialProduct?.photoUri
                ?: if (industryConfig.category == BusinessCategory.FOOTWEAR) {
                    ImageUtils.defaultDrawableForCategory(startCategory)
                } else ""
        )
    }
    var costText by remember { mutableStateOf(initialProduct?.costPrice?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var sellText by remember { mutableStateOf(initialProduct?.sellingPrice?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var mrpText by remember { mutableStateOf(initialProduct?.mrp?.toInt()?.toString() ?: "") }
    var supplier by remember { mutableStateOf(initialProduct?.supplier ?: startVendor?.name ?: "") }
    var selectedVendorId by remember { mutableStateOf(initialVendorId ?: initialProduct?.vendorId) }
    var lotCode by remember {
        mutableStateOf(initialProduct?.lotCode ?: if (initialProduct == null) "LOT-${autoSku.filter { it.isDigit() }}" else "")
    }
    var unitLabel by remember {
        mutableStateOf(initialProduct?.unitLabel ?: industryConfig.unitSingular)
    }
    var batchOrSerial by remember { mutableStateOf(initialProduct?.batchOrSerial ?: "") }
    var expiryOrWarranty by remember { mutableStateOf(initialProduct?.expiryOrWarranty ?: "") }
    var isRepeatable by remember {
        mutableStateOf(
            initialProduct?.isRepeatable ?: (industryConfig.category != BusinessCategory.FOOTWEAR)
        )
    }
    var reorderText by remember { mutableStateOf((initialProduct?.reorderLevel ?: 5).toString()) }

    // Initial lot payment options
    var paymentModeChoice by remember { mutableStateOf("PAID") }
    var partialPaidText by remember { mutableStateOf("") }
    var lotBillNotes by remember { mutableStateOf("") }
    var showAdvancedDetails by remember {
        mutableStateOf(
            initialProduct != null ||
                initialVendorId != null ||
                industryConfig.showBatchOrSerialField ||
                industryConfig.showExpiryOrWarrantyField
        )
    }

    val resolvedInitialVariants = remember(initialSizes, industryConfig) {
        if (initialSizes.isNotEmpty()) initialSizes
        else mapOf((industryConfig.defaultVariants.firstOrNull() ?: "Standard") to 10)
    }
    val sizeMap = remember {
        mutableStateMapOf<String, Int>().apply { putAll(resolvedInitialVariants) }
    }
    var customSizeInput by remember { mutableStateOf("") }

    var showInAppCamera by remember { mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()

    fun savePickedUriAsync(uri: Uri) {
        sheetScope.launch {
            val saved = withContext(Dispatchers.IO) {
                ImageUtils.compressAndSavePhoto(context, uri, maxDim = 1200)
            }
            if (saved != null) {
                photoUri = saved
            } else {
                Toast.makeText(context, "Could not read selected image file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { savePickedUriAsync(it) }
    }
    val filesPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { savePickedUriAsync(it) }
    }

    val costVal = costText.toDoubleOrNull() ?: 0.0
    val sellVal = sellText.toDoubleOrNull() ?: 0.0
    val totalUnitsCount = sizeMap.values.sum()
    val totalLotBill = totalUnitsCount * costVal
    val effectiveCategory = customCategory.trim().ifBlank { category }
    val canSave = name.isNotBlank() && costVal > 0 && sellVal > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (initialProduct == null) "Add Product" else "Edit Product",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextCharcoal
                    )
                    Text(
                        text = "${industryConfig.displayCategoryName} • Code $skuCode",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondaryGrey
                    )
                }
                SkuBoxTag(skuCode = skuCode, large = false)
            }

            // Photo row
            Surface(
                color = SurfaceSubtle,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProductPhoto(
                        photoUri = photoUri,
                        contentDescription = "Product Photo",
                        modifier = Modifier.size(76.dp),
                        cornerRadius = 12.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showInAppCamera = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("stock_camera_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DividerGrey),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Camera", style = MaterialTheme.typography.labelMedium, color = TextCharcoal)
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("stock_gallery_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DividerGrey),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Photos", style = MaterialTheme.typography.labelMedium, color = TextCharcoal)
                            }
                        }
                        TextButton(
                            onClick = { filesPickerLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.heightIn(min = 38.dp).testTag("stock_files_button"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondaryGrey)
                            Spacer(Modifier.width(6.dp))
                            Text("Browse Files", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                        }
                    }
                }
            }

            if (industryConfig.category == BusinessCategory.FOOTWEAR) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageUtils.bundledDrawables.entries.toList()) { (key, _) ->
                        val selected = photoUri == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EmeraldPrimary else DividerGrey),
                            modifier = Modifier.size(50.dp).clickable { photoUri = key }
                        ) {
                            ProductPhoto(photoUri = key, contentDescription = key, cornerRadius = 8.dp)
                        }
                    }
                }
            }

            if (showInAppCamera) {
                CameraCaptureDialog(
                    title = "Capture Product Photo",
                    subtitle = "4:3 sharp SDR • Tap product to focus",
                    allowGalleryFallback = true,
                    onDismiss = { showInAppCamera = false },
                    onPhotoReady = { file ->
                        showInAppCamera = false
                        sheetScope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                ImageUtils.compressAndSaveFile(context, file, maxDim = 1200)
                            }
                            if (saved != null) {
                                photoUri = saved
                            } else {
                                Toast.makeText(context, "Could not save captured photo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onGalleryPicked = { uri ->
                        showInAppCamera = false
                        savePickedUriAsync(uri)
                    }
                )
            }

            FormField(
                value = name,
                onValueChange = { name = it },
                label = "Product Name *",
                placeholder = "e.g. Classic Item",
                testTag = "product_name_input"
            )

            Text("Category", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat && customCategory.isBlank(),
                        onClick = {
                            category = cat
                            customCategory = ""
                            if (photoUri.startsWith("drawable:") && initialProduct == null && industryConfig.category == BusinessCategory.FOOTWEAR) {
                                photoUri = ImageUtils.defaultDrawableForCategory(cat)
                            }
                        },
                        label = { Text(cat) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    value = costText,
                    onValueChange = { s -> costText = s.filter { it.isDigit() || it == '.' } },
                    label = "Cost ($currency) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "product_cost_input",
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    value = sellText,
                    onValueChange = { s -> sellText = s.filter { it.isDigit() || it == '.' } },
                    label = "Selling ($currency) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "product_sell_input",
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    value = mrpText,
                    onValueChange = { s -> mrpText = s.filter { it.isDigit() || it == '.' } },
                    label = "MRP",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.85f)
                )
            }

            // Stock & Variants
            Text(industryConfig.variantPluralLabel, style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sizeMap.keys.sortedBy { it.toIntOrNull() ?: 99 }.forEach { sz ->
                    val qty = sizeMap[sz] ?: 0
                    Surface(
                        color = SurfaceSubtle,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${industryConfig.variantLabel}: $sz",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextCharcoal,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(
                                onClick = { sizeMap[sz] = (qty - 1).coerceAtLeast(0) },
                                modifier = Modifier.size(48.dp)
                            ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease quantity") }
                            Text(
                                text = "$qty",
                                style = SoleBookTextStyles.FinancialCompact,
                                color = TextCharcoal,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { sizeMap[sz] = qty + 1 },
                                modifier = Modifier.size(48.dp)
                            ) { Icon(Icons.Rounded.Add, contentDescription = "Increase quantity") }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FormField(
                    value = customSizeInput,
                    onValueChange = { customSizeInput = it.take(12) },
                    label = "Add ${industryConfig.variantLabel}",
                    placeholder = industryConfig.defaultVariants.lastOrNull() ?: "Standard",
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        val s = customSizeInput.trim()
                        if (s.isNotEmpty()) {
                            sizeMap.putIfAbsent(s, 2)
                            customSizeInput = ""
                        }
                    },
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text("+ Add")
                }
            }

            // Collapsible Progressive Disclosure for optional business fields
            TextButton(
                onClick = { showAdvancedDetails = !showAdvancedDetails },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (showAdvancedDetails) Icons.Rounded.ExpandLess else Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (showAdvancedDetails) "Hide extra details" else "More details (brand, SKU, supplier, rack, batch)",
                    color = EmeraldPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            AnimatedVisibility(visible = showAdvancedDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        label = "Custom Category (optional)"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = "Brand",
                            modifier = Modifier.weight(1f)
                        )
                        FormField(
                            value = skuCode,
                            onValueChange = { skuCode = it.uppercase().take(12) },
                            label = "Item Code / SKU",
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FormField(
                            value = barcode,
                            onValueChange = { barcode = it.trim().take(32) },
                            label = "Barcode (EAN/UPC/QR, optional)",
                            testTag = "product_barcode_input",
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            onClick = { showBarcodeScanner = true },
                            modifier = Modifier.size(48.dp).testTag("product_scan_barcode_btn")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan product barcode", tint = EmeraldPrimary)
                        }
                    }

                    if (industryConfig.showAudienceSegmentField) {
                        Text(industryConfig.audienceSegmentLabel, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            audienceOptions.forEach { g ->
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    if (industryConfig.showUnitField) {
                        Text("Unit of Measurement", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(industryConfig.unitSuggestions) { u ->
                                FilterChip(
                                    selected = unitLabel.equals(u, ignoreCase = true),
                                    onClick = { unitLabel = u },
                                    label = { Text(u) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    if (industryConfig.showBatchOrSerialField) {
                        FormField(
                            value = batchOrSerial,
                            onValueChange = { batchOrSerial = it.take(32) },
                            label = industryConfig.batchOrSerialLabel
                        )
                    }

                    if (industryConfig.showExpiryOrWarrantyField) {
                        FormField(
                            value = expiryOrWarranty,
                            onValueChange = { expiryOrWarranty = it.take(32) },
                            label = industryConfig.expiryOrWarrantyLabel
                        )
                    }

                    if (industryConfig.showColorTag) {
                        Text(industryConfig.colorTagLabel, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            colorOptions.forEach { col ->
                                FilterChip(
                                    selected = colorTag.equals(col, ignoreCase = true),
                                    onClick = { colorTag = if (colorTag.equals(col, ignoreCase = true)) "" else col },
                                    label = { Text(col) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    if (industryConfig.showStyleTag) {
                        Text(industryConfig.styleTagLabel, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            styleOptions.forEach { st ->
                                FilterChip(
                                    selected = styleTag.equals(st, ignoreCase = true),
                                    onClick = { styleTag = if (styleTag.equals(st, ignoreCase = true)) "" else st },
                                    label = { Text(st) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormField(
                            value = rackLocation,
                            onValueChange = { rackLocation = it.take(20) },
                            label = "Shelf / Rack",
                            leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                            testTag = "product_rack_input",
                            modifier = Modifier.weight(1.2f)
                        )
                        FormField(
                            value = reorderText,
                            onValueChange = { s -> reorderText = s.filter { it.isDigit() } },
                            label = "Low Alert At",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    if (vendors.isNotEmpty()) {
                        Text("Supplier", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedVendorId == null,
                                    onClick = { selectedVendorId = null },
                                    label = { Text("Direct / Other") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                            items(vendors, key = { it.id }) { v ->
                                FilterChip(
                                    selected = selectedVendorId == v.id,
                                    onClick = {
                                        selectedVendorId = v.id
                                        supplier = v.name
                                    },
                                    label = { Text(v.name) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormField(
                            value = supplier,
                            onValueChange = { supplier = it },
                            label = "Supplier Name",
                            modifier = Modifier.weight(1.2f)
                        )
                        FormField(
                            value = lotCode,
                            onValueChange = { lotCode = it.uppercase().take(14) },
                            label = "Lot Code",
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Text("Restock Type", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isRepeatable,
                            onClick = { isRepeatable = true },
                            label = { Text("Regular Restock") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = !isRepeatable,
                            onClick = { isRepeatable = false },
                            label = { Text("One-Time Lot") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (showLotPaymentFields && onSaveWithPayment != null && initialProduct == null && totalLotBill > 0) {
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Purchase Payment ($totalUnitsCount ${industryConfig.unitPlural} = ${Formatters.formatCurrency(totalLotBill, currency)})",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextCharcoal
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paymentModeChoice == "PAID",
                                onClick = { paymentModeChoice = "PAID" },
                                label = { Text("Paid Full") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = paymentModeChoice == "PARTIAL",
                                onClick = { paymentModeChoice = "PARTIAL" },
                                label = { Text("Partial") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = paymentModeChoice == "CREDIT",
                                onClick = { paymentModeChoice = "CREDIT" },
                                label = { Text("On Credit") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                        if (paymentModeChoice == "PARTIAL") {
                            FormField(
                                value = partialPaidText,
                                onValueChange = { s -> partialPaidText = s.filter { it.isDigit() || it == '.' } },
                                label = "Amount Paid Now ($currency)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        FormField(
                            value = lotBillNotes,
                            onValueChange = { lotBillNotes = it },
                            label = "Bill Note (optional)"
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val entity = ProductEntity(
                        id = initialProduct?.id ?: 0L,
                        skuCode = skuCode.ifBlank { autoSku },
                        barcode = barcode.trim(),
                        name = name.trim(),
                        category = effectiveCategory,
                        brand = brand.trim(),
                        gender = gender,
                        photoUri = photoUri,
                        costPrice = costVal,
                        sellingPrice = sellVal,
                        mrp = mrpText.toDoubleOrNull(),
                        supplier = supplier.trim(),
                        reorderLevel = reorderText.toIntOrNull() ?: 5,
                        colorTag = colorTag.trim(),
                        styleTag = styleTag.trim(),
                        rackLocation = rackLocation.trim(),
                        isRepeatable = isRepeatable,
                        vendorId = selectedVendorId,
                        lotCode = lotCode.trim(),
                        unitLabel = unitLabel.trim(),
                        batchOrSerial = batchOrSerial.trim(),
                        expiryOrWarranty = expiryOrWarranty.trim(),
                        isArchived = initialProduct?.isArchived ?: false,
                        createdAt = initialProduct?.createdAt ?: System.currentTimeMillis()
                    )
                    if (showLotPaymentFields && onSaveWithPayment != null && initialProduct == null) {
                        val paid = when (paymentModeChoice) {
                            "CREDIT" -> 0.0
                            "PARTIAL" -> (partialPaidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, totalLotBill)
                            else -> totalLotBill
                        }
                        onSaveWithPayment(entity, sizeMap.toMap(), paid, lotBillNotes.trim())
                    } else {
                        onSave(entity, sizeMap.toMap())
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("save_product_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = PureWhite
                )
            ) {
                Text(
                    text = if (initialProduct == null) "Save Product to Stock" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            title = "Scan Product Barcode",
            onDismiss = { showBarcodeScanner = false },
            onBarcodeDetected = { code ->
                barcode = code
                showBarcodeScanner = false
            }
        )
    }
}
