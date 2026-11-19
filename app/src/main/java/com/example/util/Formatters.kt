package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Formatters {

    /**
     * Formats numbers in Indian numbering system: 1,25,000
     */
    fun formatIndianNumber(value: Long): String {
        val negative = value < 0
        val digits = abs(value).toString()
        if (digits.length <= 3) {
            return if (negative) "-$digits" else digits
        }
        val lastThree = digits.takeLast(3)
        val remaining = digits.dropLast(3)
        val grouped = remaining.reversed().chunked(2).joinToString(",").reversed()
        val result = "$grouped,$lastThree"
        return if (negative) "-$result" else result
    }

    fun formatCurrency(amount: Double, symbol: String = "₹", showDecimals: Boolean = false): String {
        val negative = amount < -0.005
        val absVal = abs(amount)
        return if (!showDecimals) {
            val rounded = absVal.roundToLong()
            val formatted = formatIndianNumber(rounded)
            if (negative) "-$symbol$formatted" else "$symbol$formatted"
        } else {
            val whole = absVal.toLong()
            val paise = ((absVal - whole) * 100).roundToLong().coerceIn(0, 99)
            val formattedWhole = formatIndianNumber(whole)
            val paiseStr = paise.toString().padStart(2, '0')
            if (negative) "-$symbol$formattedWhole.$paiseStr" else "$symbol$formattedWhole.$paiseStr"
        }
    }

    fun formatSignedCurrency(amount: Double, symbol: String = "₹"): String {
        return when {
            amount > 0.5 -> "+${formatCurrency(amount, symbol)}"
            amount < -0.5 -> formatCurrency(amount, symbol)
            else -> "${symbol}0"
        }
    }

    fun formatShortDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDayHeader(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun startOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun startOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(timestamp)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return cal.timeInMillis
    }

    fun startOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(timestamp)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    fun dayOfWeekIndex(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun .. 7 = Sat
    }

    fun generateNextSku(existingSkus: List<String>, prefix: String = "SB"): String {
        val cleanPrefix = prefix.trim().ifBlank { "SB" }.uppercase()
        val maxNum = existingSkus.mapNotNull { sku ->
            val dashIdx = sku.indexOf('-')
            if (dashIdx >= 0 && dashIdx < sku.length - 1) {
                sku.substring(dashIdx + 1).toIntOrNull()
            } else null
        }.maxOrNull() ?: 0
        val next = maxNum + 1
        return "$cleanPrefix-${next.toString().padStart(3, '0')}"
    }
}
