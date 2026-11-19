package com.example.domain

import com.example.data.local.CustomerEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductVariantEntity
import com.example.util.Formatters

data class ParsedContact(
    val name: String,
    val phone: String,
    val email: String = ""
)

data class ContactImportResult(
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int
)

enum class CampaignPreset(
    val key: String,
    val label: String,
    val headline: String,
    val defaultHurryNote: String
) {
    NEW_STOCK(
        key = "NEW_STOCK",
        label = "New Stock Alert",
        headline = "FRESH NEW STOCK JUST ARRIVED!",
        defaultHurryNote = "Hurry! Fresh pairs just unpacked — reply with your size to reserve yours!"
    ),
    HURRY_LIMITED(
        key = "HURRY_LIMITED",
        label = "Hurry! Limited Pairs",
        headline = "HURRY — LIMITED PAIRS LEFT!",
        defaultHurryNote = "Selling fast! Message us now before your size goes out of stock."
    ),
    FESTIVE_DROP(
        key = "FESTIVE",
        label = "Festive / Weekend",
        headline = "SPECIAL FESTIVE COLLECTION LIVE!",
        defaultHurryNote = "Drop by this weekend to try the latest styles with family."
    ),
    VIP_OFFER(
        key = "VIP_OFFER",
        label = "VIP Offer",
        headline = "EXCLUSIVE VIP CUSTOMER OFFER!",
        defaultHurryNote = "Show this WhatsApp message at the counter for priority sizing & special deal."
    ),
    CLEARANCE(
        key = "CLEARANCE",
        label = "Clearance Deal",
        headline = "PRICE DROP / CLEARANCE DEAL!",
        defaultHurryNote = "Last few pairs at unbeatable shop prices — first come, first served!"
    )
}

enum class CampaignAudienceFilter(val key: String, val label: String) {
    SMART_MATCH("SMART_MATCH", "Matched to Shoes"),
    ALL_OPTED_IN("ALL", "All Opted-In"),
    VIP("VIP", "VIP"),
    REGULAR("REGULAR", "Regulars"),
    LAPSED("LAPSED", "Lapsed 60d+"),
    NEW("NEW", "New"),
    CREDIT_PENDING("CREDIT_PENDING", "Udhaar")
}

data class MatchedRecipient(
    val customer: CustomerEntity,
    val matchReasons: List<String>
)

object CampaignBuilder {

    private val emailRegex = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            else -> digits
        }
    }

    fun isValidPhone(normalized: String): Boolean {
        return normalized.length in 8..15
    }

    /**
     * Parses CSV lines, Google/Phone vCard (.vcf) blocks, or free-form pasted contact text
     * ("Name 9876543210", "9876543210, 9123456789", "Amit, 9811223344, amit@gmail.com").
     */
    fun parseContactsText(rawText: String): List<ParsedContact> {
        if (rawText.isBlank()) return emptyList()
        val trimmed = rawText.trim()
        val vcardBlockRegex = Regex("BEGIN:VCARD[\\s\\S]*?END:VCARD", RegexOption.IGNORE_CASE)
        val parsed = mutableListOf<ParsedContact>()
        var cursor = 0
        val matches = vcardBlockRegex.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            parsed += parseLinesOrCsv(trimmed)
        } else {
            for (m in matches) {
                if (m.range.first > cursor) {
                    val before = trimmed.substring(cursor, m.range.first)
                    parsed += parseLinesOrCsv(before)
                }
                parsed += parseVCardText(m.value)
                cursor = m.range.last + 1
            }
            if (cursor < trimmed.length) {
                parsed += parseLinesOrCsv(trimmed.substring(cursor))
            }
        }

        // Deduplicate by normalized phone while keeping richest name/email
        val byPhone = linkedMapOf<String, ParsedContact>()
        for (c in parsed) {
            val norm = normalizePhone(c.phone)
            if (!isValidPhone(norm)) continue
            val existing = byPhone[norm]
            if (existing == null) {
                byPhone[norm] = ParsedContact(
                    name = c.name.trim(),
                    phone = norm,
                    email = c.email.trim()
                )
            } else {
                byPhone[norm] = existing.copy(
                    name = existing.name.ifBlank { c.name.trim() },
                    email = existing.email.ifBlank { c.email.trim() }
                )
            }
        }
        return byPhone.values.toList()
    }

    private fun parseVCardText(text: String): List<ParsedContact> {
        val result = mutableListOf<ParsedContact>()
        val cards = text.split(Regex("END:VCARD", RegexOption.IGNORE_CASE))
        for (block in cards) {
            if (!block.contains("TEL", ignoreCase = true)) continue
            var name = ""
            var email = ""
            val phones = mutableListOf<String>()
            block.lines().forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.startsWith("FN", ignoreCase = true) && line.contains(":") -> {
                        name = line.substringAfter(":").trim()
                    }
                    line.startsWith("N:", ignoreCase = true) && name.isBlank() -> {
                        name = line.substringAfter(":").split(";")
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }
                    line.startsWith("TEL", ignoreCase = true) && line.contains(":") -> {
                        val rawNum = line.substringAfter(":").trim()
                        val norm = normalizePhone(rawNum)
                        if (isValidPhone(norm)) phones += norm
                    }
                    line.startsWith("EMAIL", ignoreCase = true) && line.contains(":") -> {
                        val em = line.substringAfter(":").trim()
                        if (email.isBlank() && emailRegex.matches(em)) email = em
                    }
                }
            }
            phones.distinct().forEach { p ->
                result += ParsedContact(name = name, phone = p, email = email)
            }
        }
        return result
    }

    private fun parseLinesOrCsv(text: String): List<ParsedContact> {
        val result = mutableListOf<ParsedContact>()
        val lines = text.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val lower = line.lowercase()
            if ((lower.contains("phone") && lower.contains("name")) || lower.startsWith("id,phone")) {
                continue // skip CSV header row
            }

            // If line contains multiple phone numbers separated by comma/semicolon without names
            val phoneMatches = Regex("(?:\\+?\\d[\\d\\s\\-()]{7,16}\\d)").findAll(line).toList()
            if (phoneMatches.size > 1 && !line.contains("@")) {
                phoneMatches.forEach { m ->
                    val norm = normalizePhone(m.value)
                    if (isValidPhone(norm)) {
                        result += ParsedContact(name = "", phone = norm)
                    }
                }
                continue
            }

            val email = emailRegex.find(line)?.value.orEmpty()
            val firstPhoneMatch = phoneMatches.firstOrNull() ?: continue
            val normPhone = normalizePhone(firstPhoneMatch.value)
            if (!isValidPhone(normPhone)) continue

            // Extract name by removing phone, email, punctuation separators
            var nameCandidate = line
                .replace(firstPhoneMatch.value, " ")
                .let { if (email.isNotBlank()) it.replace(email, " ") else it }
                .replace("\"", " ")
                .replace(",", " ")
                .replace(";", " ")
                .replace("\t", " ")
                .replace(" - ", " ")
                .trim()
                .replace(Regex("\\s+"), " ")
                .trim('-', '|', ':', '•')
                .trim()

            // Discard pure digits/booleans from CSV exports if user pasted CSV rows
            if (nameCandidate.all { it.isDigit() || it == '.' }) {
                nameCandidate = ""
            }
            result += ParsedContact(name = nameCandidate, phone = normPhone, email = email)
        }
        return result
    }

    fun buildCampaignTemplate(
        preset: CampaignPreset,
        selectedProducts: List<ProductEntity>,
        variantsByProduct: Map<Long, List<ProductVariantEntity>>,
        currencySymbol: String,
        hurryNote: String = preset.defaultHurryNote
    ): String {
        return buildString {
            appendLine("Namaste {name}! 👋")
            appendLine("*${preset.headline}* at *{shop}*")
            appendLine()
            if (selectedProducts.isNotEmpty()) {
                selectedProducts.forEachIndexed { idx, p ->
                    val availableSizes = variantsByProduct[p.id]
                        .orEmpty()
                        .filter { it.stock > 0 }
                        .sortedBy { it.size.toIntOrNull() ?: 99 }
                        .map { it.size }
                    val sizeLine = if (availableSizes.isNotEmpty()) {
                        " | Sizes: ${availableSizes.joinToString(", ")}"
                    } else ""
                    val priceText = Formatters.formatCurrency(p.sellingPrice, currencySymbol)
                    val tag = if (p.brand.isNotBlank()) "${p.brand} " else ""
                    appendLine("${idx + 1}. *${tag}${p.name}* (${p.skuCode}) — *$priceText*$sizeLine")
                }
                appendLine()
            } else {
                appendLine("Fresh {fav_category} & trending footwear in Size {fav_size} are ready at the store!")
                appendLine()
            }
            if (hurryNote.isNotBlank()) {
                appendLine("⏳ ${hurryNote.trim()}")
            }
        }.trim()
    }

    fun personalizeMessage(
        template: String,
        customer: CustomerEntity?,
        shopName: String
    ): String {
        val displayName = customer?.name?.trim()?.takeIf { it.isNotBlank() } ?: "Friend"
        val favCat = customer?.favouriteCategory?.trim()?.takeIf { it.isNotBlank() } ?: "footwear"
        val favSize = customer?.favouriteSize?.trim()?.takeIf { it.isNotBlank() } ?: "your size"
        return template
            .replace("{name}", displayName)
            .replace("{shop}", shopName)
            .replace("{fav_category}", favCat)
            .replace("{fav_size}", favSize)
    }

    fun filterAndRankRecipients(
        customers: List<CustomerEntity>,
        customerSegments: Map<Long, Set<CustomerSegmentType>>,
        audienceFilter: CampaignAudienceFilter,
        selectedProducts: List<ProductEntity>,
        variantsByProduct: Map<Long, List<ProductVariantEntity>>
    ): List<MatchedRecipient> {
        val optedIn = customers.filter { it.whatsappConsent && it.phone.isNotBlank() }

        val targetCategories = selectedProducts.map { it.category.lowercase() }.toSet()
        val targetSizes = selectedProducts.flatMap { p ->
            variantsByProduct[p.id].orEmpty().filter { it.stock > 0 }.map { it.size.lowercase() }
        }.toSet()

        fun matchReasonsFor(c: CustomerEntity): List<String> {
            val reasons = mutableListOf<String>()
            if (c.favouriteCategory.isNotBlank() && c.favouriteCategory.lowercase() in targetCategories) {
                reasons += "Buys ${c.favouriteCategory}"
            }
            if (c.favouriteSize.isNotBlank() && c.favouriteSize.lowercase() in targetSizes) {
                reasons += "Size ${c.favouriteSize} in stock"
            }
            return reasons
        }

        val mapped = optedIn.mapNotNull { c ->
            val segs = customerSegments[c.id].orEmpty()
            val reasons = matchReasonsFor(c)
            val include = when (audienceFilter) {
                CampaignAudienceFilter.ALL_OPTED_IN -> true
                CampaignAudienceFilter.SMART_MATCH -> {
                    if (selectedProducts.isEmpty()) true
                    else reasons.isNotEmpty()
                }
                CampaignAudienceFilter.VIP -> CustomerSegmentType.VIP in segs
                CampaignAudienceFilter.REGULAR -> CustomerSegmentType.REGULAR in segs
                CampaignAudienceFilter.LAPSED -> CustomerSegmentType.LAPSED in segs
                CampaignAudienceFilter.NEW -> CustomerSegmentType.NEW in segs
                CampaignAudienceFilter.CREDIT_PENDING -> CustomerSegmentType.CREDIT_PENDING in segs
            }
            if (include) MatchedRecipient(c, reasons) else null
        }

        return mapped.sortedWith(
            compareByDescending<MatchedRecipient> { it.matchReasons.size }
                .thenByDescending { it.customer.totalSpent }
                .thenByDescending { it.customer.lastVisitAt }
        )
    }
}
