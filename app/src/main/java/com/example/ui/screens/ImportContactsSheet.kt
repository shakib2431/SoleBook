package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.CampaignBuilder
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportContactsSheet(
    onImportText: (String, Boolean, () -> Unit) -> Unit,
    onImportUri: (Uri, Boolean, () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var pastedText by remember { mutableStateOf("") }
    var whatsappConsent by remember { mutableStateOf(true) }

    val parsedPreview = remember(pastedText) {
        CampaignBuilder.parseContactsText(pastedText)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportUri(uri, whatsappConsent) {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("import_contacts_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Import Customer Contacts", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                "Build your WhatsApp broadcast & customer list fast — duplicates are automatically merged by phone number.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey
            )

            Surface(
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Option 1: Import from File (.vcf or .csv)", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Text(
                        "Pick a .vcf contacts file exported from Phone/Google Contacts, or any CSV spreadsheet with phone & email columns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryGrey
                    )
                    SecondaryButton(
                        text = "Choose .vcf or .csv File",
                        onClick = {
                            documentPicker.launch(
                                arrayOf(
                                    "text/vcard",
                                    "text/x-vcard",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        icon = Icons.Rounded.UploadFile,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "pick_contacts_file_button"
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Option 2: Paste Numbers / Names / Emails", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        label = { Text("One per line or comma-separated") },
                        placeholder = {
                            Text("Raju 9876543210\nAnita Sharma, 9811223344, anita@gmail.com\n9988776655")
                        },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = DividerGrey
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("bulk_paste_contacts_input")
                    )

                    Surface(
                        color = if (parsedPreview.isNotEmpty()) SemanticSuccessSoft else SurfaceSubtle,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (parsedPreview.isNotEmpty()) "Found ${parsedPreview.size} valid contact(s)"
                            else "Paste contacts to preview",
                            color = if (parsedPreview.isNotEmpty()) SemanticSuccess else TextMutedGrey,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = whatsappConsent, onCheckedChange = { whatsappConsent = it })
                        Text("Opted-in for WhatsApp New Stock Alerts", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                    }

                    PrimaryButton(
                        text = "Import ${parsedPreview.size} Contact(s)",
                        onClick = {
                            onImportText(pastedText, whatsappConsent) {
                                onDismiss()
                            }
                        },
                        enabled = parsedPreview.isNotEmpty(),
                        icon = Icons.Rounded.PersonAddAlt1,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "confirm_import_contacts_button"
                    )
                }
            }
        }
    }
}
