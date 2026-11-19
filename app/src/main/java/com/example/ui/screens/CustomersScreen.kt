package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomerEntity
import com.example.domain.CampaignBuilder
import com.example.domain.CustomerSegmentType
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    preselectedCampaignProductId: Long? = null,
    onConsumedPreselectedProduct: () -> Unit = {},
    onOpenCustomerDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit = {}
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val bottomDockPadding = LocalGlassBottomPadding.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedSegment by remember { mutableStateOf(CustomerSegmentType.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showOfferSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var campaignPreselectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(preselectedCampaignProductId) {
        if (preselectedCampaignProductId != null) {
            campaignPreselectedIds = setOf(preselectedCampaignProductId)
            showOfferSheet = true
            onConsumedPreselectedProduct()
        }
    }

    val filteredCustomers = remember(state.customers, state.customerSegments, searchQuery, selectedSegment) {
        val q = searchQuery.trim().lowercase()
        state.customers.filter { c ->
            val segs = state.customerSegments[c.id].orEmpty()
            val matchesSeg = selectedSegment == CustomerSegmentType.ALL || selectedSegment in segs
            val matchesQuery = q.isEmpty() ||
                c.name.lowercase().contains(q) ||
                c.phone.contains(q) ||
                c.email.lowercase().contains(q)
            matchesSeg && matchesQuery
        }
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = bottomDockPadding)
                    .heightIn(min = 54.dp)
                    .testTag("add_customer_fab")
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Customer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            SoleBookTopBar(
                title = "Customers",
                subtitle = "${state.customers.size} saved • ${state.campaignLogs.size} offers sent",
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices,
                extraAction = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                campaignPreselectedIds = emptySet()
                                showOfferSheet = true
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("send_offer_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = EmeraldSoft,
                                contentColor = EmeraldDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Campaign", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = { showImportSheet = true },
                            modifier = Modifier.size(48.dp).testTag("import_contacts_button")
                        ) {
                            Icon(Icons.Rounded.PersonAddAlt1, contentDescription = "Import Contacts", tint = EmeraldPrimary)
                        }
                        IconButton(
                            onClick = {
                                viewModel.exportCustomersCsv { file ->
                                    ExportUtils.shareFile(context, file, "text/csv", "Export Customers CSV")
                                }
                            },
                            modifier = Modifier.size(48.dp).testTag("export_customers_csv")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Export CSV", tint = TextCharcoal)
                        }
                    }
                }
            )

            // WhatsApp Marketing Strip
            Surface(
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "New Stock & Festival Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextCharcoal
                        )
                        Text(
                            "${state.customers.count { it.whatsappConsent }} opted-in • Pick items & notify on WhatsApp",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryGrey
                        )
                    }
                    Button(
                        onClick = {
                            campaignPreselectedIds = emptySet()
                            showOfferSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Alert Now", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search name, phone, or email...",
                testTag = "customer_search_input",
                modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CustomerSegmentType.entries) { seg ->
                    val count = if (seg == CustomerSegmentType.ALL) state.customers.size else {
                        state.customerSegments.values.count { seg in it }
                    }
                    val selected = selectedSegment == seg
                    FilterChip(
                        selected = selected,
                        onClick = { selectedSegment = seg },
                        label = { Text("${seg.label} ($count)", style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceWhite,
                            labelColor = TextCharcoal
                        )
                    )
                }
            }

            if (filteredCustomers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                    EmptyState(
                        icon = Icons.Rounded.People,
                        title = if (state.customers.isEmpty()) "No customers saved yet" else "No customers in ${selectedSegment.label}",
                        subtitle = if (state.customers.isEmpty()) {
                            "Save customers during billing, pick from phone contacts, or import CSV/vCard lists."
                        } else {
                            "Clear search or switch segment filter to see all customers."
                        },
                        primaryLabel = if (state.customers.isEmpty()) "Add Customer" else "Show All",
                        onPrimaryClick = {
                            if (state.customers.isEmpty()) showAddDialog = true
                            else {
                                selectedSegment = CustomerSegmentType.ALL
                                searchQuery = ""
                            }
                        },
                        secondaryLabel = if (state.customers.isEmpty()) "Import Contacts" else null,
                        onSecondaryClick = if (state.customers.isEmpty()) { { showImportSheet = true } } else null
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = SoleBookSpacing.screenHorizontal,
                        end = SoleBookSpacing.screenHorizontal,
                        bottom = 88.dp + bottomDockPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        val segs = state.customerSegments[customer.id].orEmpty()
                        Surface(
                            onClick = { onOpenCustomerDetail(customer.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_card_${customer.phone}"),
                            shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, DividerGrey)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = EmeraldSoft,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val initials = customer.name.trim()
                                            .split(" ")
                                            .filter { it.isNotBlank() }
                                            .take(2)
                                            .joinToString("") { it.first().uppercase() }
                                            .ifBlank { "#" }
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldDark
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = customer.name.ifBlank { customer.phone },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextCharcoal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${customer.phone} • ${customer.visitCount} visits")
                                            if (customer.favouriteSize.isNotBlank()) append(" • ${industry.variantLabel}: ${customer.favouriteSize}")
                                            if (customer.email.isNotBlank()) append(" • ${customer.email}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (segs.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            segs.take(2).forEach { s ->
                                                val isCredit = s == CustomerSegmentType.CREDIT_PENDING
                                                Surface(
                                                    color = if (isCredit) SemanticErrorSoft else SurfaceSubtle,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        s.label,
                                                        color = if (isCredit) SemanticError else TextCharcoal,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        Formatters.formatCurrency(customer.totalSpent, currency),
                                        style = SoleBookTextStyles.FinancialInline,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCharcoal
                                    )
                                    if (customer.creditBalance > 0.5) {
                                        Text(
                                            "Due ${Formatters.formatCurrency(customer.creditBalance, currency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SemanticError,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            Formatters.formatShortDate(customer.lastVisitAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMutedGrey
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditCustomerDialog(
            initial = null,
            prefPlaceholder = industry.customerPrefLabel,
            onDismiss = { showAddDialog = false },
            onSave = { c ->
                viewModel.saveCustomer(c)
                showAddDialog = false
            }
        )
    }

    if (showOfferSheet) {
        WhatsAppCampaignStudioSheet(
            state = state,
            viewModel = viewModel,
            initialSelectedProductIds = campaignPreselectedIds,
            onDismiss = {
                showOfferSheet = false
                campaignPreselectedIds = emptySet()
            }
        )
    }

    if (showImportSheet) {
        ImportContactsSheet(
            onImportText = { raw, consent, done ->
                viewModel.importCustomersFromText(raw, consent) { done() }
            },
            onImportUri = { uri, consent, done ->
                viewModel.importCustomersFromUri(uri, consent) { done() }
            },
            onDismiss = { showImportSheet = false }
        )
    }

    if (showHelpDialog) {
        HelpTipDialog(
            title = "Customers & WhatsApp Marketing",
            tips = listOf(
                "Import your customer phone & email list via .vcf/.csv files or quick paste — duplicates merge automatically.",
                "Tap Campaign or Alert Now to pick newly arrived items, match interested buyers, and share alerts on WhatsApp.",
                "Customers are grouped into VIP, Regulars, New, Lapsed (60d+), and Credit Pending."
            ),
            onDismiss = { showHelpDialog = false }
        )
    }
}

@Composable
fun AddEditCustomerDialog(
    initial: CustomerEntity?,
    prefPlaceholder: String = "Preferred variant or fit",
    onDismiss: () -> Unit,
    onSave: (CustomerEntity) -> Unit
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var specialDate by remember { mutableStateOf(initial?.specialDate ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var whatsappConsent by remember { mutableStateOf(initial?.whatsappConsent ?: true) }
    var pickerHint by remember { mutableStateOf<String?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val dataUri = res.data?.data
            if (dataUri != null) {
                try {
                    context.contentResolver.query(
                        dataUri,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            if (numIdx >= 0) {
                                val norm = CampaignBuilder.normalizePhone(cursor.getString(numIdx).orEmpty())
                                if (norm.isNotBlank()) phone = norm.take(15)
                            }
                            if (nameIdx >= 0 && name.isBlank()) {
                                name = cursor.getString(nameIdx).orEmpty()
                            }
                        }
                    }
                    pickerHint = null
                } catch (_: SecurityException) {
                    pickerHint = "Use Import .vcf / Paste or type number manually"
                } catch (_: Exception) {
                    pickerHint = "Could not read contact — please enter manually"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = { Text(if (initial == null) "Add Customer" else "Edit Customer", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initial == null) {
                    SecondaryButton(
                        text = "Pick from Phone Contacts",
                        onClick = {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            runCatching { contactPickerLauncher.launch(pickIntent) }
                                .onFailure { pickerHint = "No Contacts app available — type number below" }
                        },
                        icon = Icons.Rounded.Contacts,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pickerHint.isNullOrBlank()) {
                        Text(pickerHint!!, style = MaterialTheme.typography.labelSmall, color = SemanticWarning)
                    }
                }
                FormField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() }.take(15) },
                    label = "Phone Number *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    testTag = "customer_phone_input"
                )
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name (optional)",
                    testTag = "customer_name_input"
                )
                FormField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email (optional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    testTag = "customer_email_input"
                )
                FormField(
                    value = specialDate,
                    onValueChange = { specialDate = it },
                    label = "Birthday / Anniversary (optional)"
                )
                FormField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes ($prefPlaceholder)"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = whatsappConsent, onCheckedChange = { whatsappConsent = it })
                    Text("WhatsApp offer consent", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        (initial ?: CustomerEntity(phone = phone, firstVisitAt = now, lastVisitAt = now)).copy(
                            phone = CampaignBuilder.normalizePhone(phone),
                            name = name.trim(),
                            email = email.trim(),
                            specialDate = specialDate.trim(),
                            notes = notes.trim(),
                            whatsappConsent = whatsappConsent
                        )
                    )
                },
                enabled = phone.trim().length >= 8,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_customer_button")
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}
