package com.programaorcamentos.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

fun parseMoneyToCents(input: String): Long {
    val normalized = input.trim().replace("€", "").replace(" ", "").replace(",", ".")
    if (normalized.isBlank()) return 0
    return runCatching {
        BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrDefault(0)
}

fun formatMoney(cents: Long, currencyCode: String = "EUR"): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    format.currency = Currency.getInstance(currencyCode.ifBlank { "EUR" })
    return format.format(cents / 100.0)
}

fun calculateLine(quantity: Double, unitPriceInputCents: Long, vatRate: Double, priceIncludesVat: Boolean): LineTotals {
    val rate = vatRate / 100.0
    val unitExVat = if (priceIncludesVat && rate > 0.0) {
        (unitPriceInputCents / (1.0 + rate)).roundToLong()
    } else {
        unitPriceInputCents
    }
    val subtotal = (quantity * unitExVat).roundToLong()
    val vat = (subtotal * rate).roundToLong()
    return LineTotals(unitExVat, subtotal, vat, subtotal + vat)
}

data class LineTotals(
    val unitPriceExcludingVatCents: Long,
    val subtotalExcludingVatCents: Long,
    val vatAmountCents: Long,
    val totalIncludingVatCents: Long
)
