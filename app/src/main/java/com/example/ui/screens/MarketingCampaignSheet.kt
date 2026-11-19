package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.domain.CampaignAudienceFilter
import com.example.domain.CampaignBuilder
import com.example.domain.CampaignPreset
import com.example.ui.components.PrimaryButton
import com.example.ui.components.ProductPhoto
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppCampaignStudioSheet(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    initialSelectedProductIds: Set<Long> = emptySet(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig

    val variantsByProduct = remember(state.variants) {
        state.variants.groupBy { it.productId }
    }
    val activeProducts = remember(state.products) {
        state.products
            .filter { !it.isArchived }
            .sortedWith(
                compareByDescending<ProductEntity> { it.id in initialSelectedProductIds }
                    .thenByDescending { it.createdAt }
            )
    }

    var selectedProductIds by remember(initialSelectedProductIds) { mutableStateOf(initialSelectedProductIds) }
    var selectedPreset by remember { mutableStateOf(CampaignPreset.NEW_STOCK) }
    var hurryNote by remember { mutableStateOf(CampaignPreset.NEW_STOCK.defaultHurryNote) }
    var audienceFilter by remember(initialSelectedProductIds) {
        mutableStateOf(
            if (initialSelectedProductIds.isNotEmpty()) CampaignAudienceFilter.SMART_MATCH
            else CampaignAudienceFilter.ALL_OPTED_IN
        )
    }
    var userEditedMessage by remember { mutableStateOf(false) }

    val selectedProducts = remember(activeProducts, selectedProductIds) {
        activeProducts.filter { it.id in selectedProductIds }
    }

    val autoMessage = remember(selectedPreset, selectedProducts, variantsByProduct, currency, hurryNote) {
        CampaignBuilder.buildCampaignTemplate(
            preset = selectedPreset,
            selectedProducts = selectedProducts,
            variantsByProduct = variantsByProduct,
            currencySymbol = currency,
            hurryNote = hurryNote
        )
    }

    var messageDraft by remember { mutableStateOf(autoMessage) }
    LaunchedEffect(autoMessage) {
        if (!userEditedMessage) {
            messageDraft = autoMessage
        }
    }

    val recipients = remember(state.customers, state.customerSegments, audienceFilter, selectedProducts, variantsByProduct) {
        CampaignBuilder.filterAndRankRecipients(
            customers = state.customers,
            customerSegments = state.customerSegments,
            audienceFilter = audienceFilter,
            selectedProducts = selectedProducts,
            variantsByProduct = variantsByProduct
        )
    }

    val sentInSessionIds = remember { mutableStateListOf<Long>() }
    val recent24hCustomerIds = remember(state.campaignLogs) {
        val cutoff = System.currentTimeMillis() - 86_400_000L
        state.campaignLogs.filter { it.timestamp >= cutoff }.mapNotNull { it.customerId }.toSet()
    }
    val sentCountInList = recipients.count { it.customer.id in sentInSessionIds }
    val nextUnsent = recipients.firstOrNull { it.customer.id !in sentInSessionIds }
    var isPreparingPhotos by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .testTag("whatsapp_campaign_sheet"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = EmeraldSoft, shape = RoundedCornerShape(10.dp)) {
                        Icon(
                            Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.padding(8.dp).size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WhatsApp New Stock Studio", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
                        Text(
                            "Pick items to share in Broadcast/Status or message matched customers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryGrey
                        )
                    }
                }
            }

            // Step 1: Select Products
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "1. Pick Items to Promote (${selectedProducts.size}/5)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextCharcoal
                        )
                        if (selectedProductIds.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    selectedProductIds = emptySet()
                                    userEditedMessage = false
                                    if (audienceFilter == CampaignAudienceFilter.SMART_MATCH) {
                                        audienceFilter = CampaignAudienceFilter.ALL_OPTED_IN
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Clear", color = EmeraldPrimary)
                            }
                        }
                    }

                    if (activeProducts.isEmpty()) {
                        Text(
                            "No active items in Inventory yet — you can still send a general shop offer below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedGrey
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(activeProducts.take(25), key = { it.id }) { p ->
                                val isSelected = p.id in selectedProductIds
                                val stockQty = variantsByProduct[p.id].orEmpty().sumOf { it.stock }
                                Surface(
                                    color = if (isSelected) EmeraldSoft else SurfaceWhite,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) EmeraldPrimary else DividerGrey
                                    ),
                                    modifier = Modifier
                                        .width(164.dp)
                                        .testTag("campaign_article_${p.id}")
                                        .clickable {
                                            userEditedMessage = false
                                            selectedProductIds = if (isSelected) {
                                                selectedProductIds - p.id
                                            } else if (selectedProductIds.size < 5) {
                                                selectedProductIds + p.id
                                            } else {
                                                selectedProductIds
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ProductPhoto(
                                            photoUri = p.photoUri,
                                            contentDescription = p.name,
                                            modifier = Modifier.size(44.dp),
                                            cornerRadius = 8.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                p.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TextCharcoal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${Formatters.formatCurrency(p.sellingPrice, currency)} • $stockQty ${industry.unitPlural}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) EmeraldDark else TextSecondaryGrey,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: Campaign Preset
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Alert Style", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CampaignPreset.entries) { preset ->
                            val selected = selectedPreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedPreset = preset
                                    hurryNote = preset.defaultHurryNote
                                    userEditedMessage = false
                                },
                                label = { Text(preset.label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = PureWhite,
                                    containerColor = SurfaceWhite
                                )
                            )
                        }
                    }

                    val quickHooks = listOf(
                        "Hurry! Limited stock left — reply to reserve yours!",
                        "Special price for regular customers this week!",
                        "10% early-bird discount till Sunday!",
                        "Ask for quick delivery or store pickup!"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickHooks) { hook ->
                            AssistChip(
                                onClick = {
                                    hurryNote = hook
                                    userEditedMessage = false
                                },
                                label = { Text(hook.take(30) + if (hook.length > 30) "…" else "", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }
                }
            }

            // Step 3: Message Preview
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3. Message Preview", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        if (userEditedMessage) {
                            TextButton(
                                onClick = {
                                    userEditedMessage = false
                                    messageDraft = autoMessage
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Reset Auto-Text", color = EmeraldPrimary)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = {
                            userEditedMessage = true
                            messageDraft = it
                        },
                        label = { Text("WhatsApp Message ({name}, {shop}, {fav_category}, {fav_size})") },
                        minLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = DividerGrey
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("campaign_message_input")
                    )
                }
            }

            // Broadcast / Status Share
            item {
                Surface(
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = EmeraldPrimary)
                            Text("Fastest: WhatsApp Broadcast / Status", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        }
                        Text(
                            "Shares product photos + copies your message so you can drop it into a Broadcast List or Status in 1 tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryGrey
                        )
                        PrimaryButton(
                            text = if (isPreparingPhotos) "Preparing Photos..."
                            else if (selectedProducts.isNotEmpty()) "Share ${selectedProducts.size} Item Photo(s) + Alert"
                            else "Share Offer Text to WhatsApp",
                            onClick = {
                                if (isPreparingPhotos) return@PrimaryButton
                                isPreparingPhotos = true
                                scope.launch {
                                    val broadcastCaption = CampaignBuilder.personalizeMessage(
                                        template = messageDraft,
                                        customer = null,
                                        shopName = state.settings.shopName.ifBlank { "My Shop" }
                                    )
                                    val files = withContext(Dispatchers.IO) {
                                        ExportUtils.prepareShareableProductImages(context, selectedProducts)
                                    }
                                    isPreparingPhotos = false
                                    ExportUtils.shareCampaignWithPhotos(context, files, broadcastCaption)
                                }
                            },
                            enabled = !isPreparingPhotos && messageDraft.isNotBlank(),
                            icon = Icons.Rounded.Share,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "share_broadcast_photos_button"
                        )
                    }
                }
            }

            // 1-by-1 Personalized Queue
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Or Send 1-by-1 Personalized (${recipients.size} matched)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextCharcoal
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CampaignAudienceFilter.entries) { filter ->
                            val labelText = if (filter == CampaignAudienceFilter.SMART_MATCH) "Matched to Items" else filter.label
                            FilterChip(
                                selected = audienceFilter == filter,
                                onClick = { audienceFilter = filter },
                                label = { Text(labelText) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = PureWhite,
                                    containerColor = SurfaceWhite
                                )
                            )
                        }
                    }

                    if (recipients.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                            color = SurfaceSubtle,
                            border = BorderStroke(1.dp, DividerGrey)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Queue Progress: $sentCountInList / ${recipients.size} sent",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextCharcoal
                                    )
                                    Text(
                                        "${recipients.size - sentCountInList} remaining",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (sentCountInList == recipients.size) SemanticSuccess else TextMutedGrey
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (sentCountInList.toFloat() / recipients.size.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                    color = SemanticSuccess,
                                    trackColor = SurfaceWhite,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                                )
                                if (nextUnsent != null) {
                                    PrimaryButton(
                                        text = "Send Next: ${nextUnsent.customer.name.ifBlank { nextUnsent.customer.phone }}",
                                        onClick = {
                                            val c = nextUnsent.customer
                                            val personalized = CampaignBuilder.personalizeMessage(
                                                template = messageDraft,
                                                customer = c,
                                                shopName = state.settings.shopName.ifBlank { "My Shop" }
                                            )
                                            if (c.id !in sentInSessionIds) sentInSessionIds.add(c.id)
                                            viewModel.logCampaignSend(null, audienceFilter.key, c, personalized)
                                            ExportUtils.openWhatsApp(context, c.phone, personalized)
                                        },
                                        icon = Icons.Rounded.Send,
                                        modifier = Modifier.fillMaxWidth(),
                                        testTag = "send_next_customer_button"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (recipients.isEmpty()) {
                item {
                    Text(
                        if (audienceFilter == CampaignAudienceFilter.SMART_MATCH && selectedProducts.isNotEmpty()) {
                            "No saved customers match those exact categories/${industry.variantLabel.lowercase()} yet — switch to 'All Opted-In' or import contacts."
                        } else {
                            "No opted-in customers in this audience yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMutedGrey
                    )
                }
            } else {
                items(recipients, key = { it.customer.id }) { item ->
                    val c = item.customer
                    val sentSession = c.id in sentInSessionIds
                    val sentRecent = c.id in recent24hCustomerIds
                    val personalized = CampaignBuilder.personalizeMessage(
                        template = messageDraft,
                        customer = c,
                        shopName = state.settings.shopName.ifBlank { "My Shop" }
                    )

                    Surface(
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        c.name.ifBlank { c.phone },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextCharcoal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (sentSession || sentRecent) {
                                        Surface(color = SemanticSuccessSoft, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                if (sentSession) "Sent" else "Sent 24h",
                                                color = SemanticSuccess,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text("${c.phone} • ${c.visitCount} visits", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                if (item.matchReasons.isNotEmpty()) {
                                    Text(
                                        item.matchReasons.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (c.id !in sentInSessionIds) sentInSessionIds.add(c.id)
                                    viewModel.logCampaignSend(null, audienceFilter.key, c, personalized)
                                    ExportUtils.openWhatsApp(context, c.phone, personalized)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sentSession) SurfaceSubtle else EmeraldPrimary,
                                    contentColor = if (sentSession) TextCharcoal else PureWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (sentSession) "Resend" else "WhatsApp")
                            }
                        }
                    }
                }
            }
        }
    }
}
