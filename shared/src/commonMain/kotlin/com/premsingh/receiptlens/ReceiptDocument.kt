package com.premsingh.receiptlens

/** Platform-neutral output from a recognizer. Native images and SDK objects stay outside commonMain. */
data class OcrDocument(
    val fullText: String,
    val lines: List<String>,
)

/** Values suggested by lightweight labels; they are intentionally not treated as verified facts. */
data class ReceiptCandidates(
    val merchant: String? = null,
    val invoiceNumber: String? = null,
    val date: String? = null,
    val total: String? = null,
)

fun normalizeOcrDocument(fullText: String, lines: List<String>): OcrDocument = OcrDocument(
    fullText = fullText.trim(),
    lines = lines.map { it.trim() }.filter { it.isNotEmpty() },
)

/**
 * Extracts conservative, editable candidates from common receipt labels.
 * This is not invoice understanding: callers must show the source and ask a person to verify.
 */
fun extractReceiptCandidates(lines: List<String>): ReceiptCandidates {
    val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
    val merchant = cleaned.firstOrNull { line ->
        val lower = line.lowercase()
        line.length in 2..48 && ':' !in line &&
            listOf("invoice", "receipt", "date", "total", "subtotal", "tax").none { lower.startsWith(it) }
    }

    return ReceiptCandidates(
        merchant = merchant,
        invoiceNumber = labelledValue(cleaned, listOf("invoice id", "invoice no", "invoice number", "receipt no")),
        date = labelledValue(cleaned, listOf("date", "invoice date")),
        total = labelledValue(cleaned, listOf("grand total", "amount due", "total")),
    )
}

private fun labelledValue(lines: List<String>, labels: List<String>): String? {
    for (index in lines.indices) {
        val normalized = lines[index].trim().trimEnd(':').trim()
        val matched = labels.firstOrNull { normalized.equals(it, ignoreCase = true) }
        if (matched != null) return lines.getOrNull(index + 1)?.trim()?.takeIf(String::isNotEmpty)

        val current = lines[index].trim()
        val label = labels.firstOrNull { current.startsWith(it, ignoreCase = true) }
        if (label != null) {
            val remainder = current.drop(label.length).trim().trimStart(':', '#', '-', ' ')
            if (remainder.isNotEmpty()) return remainder
        }
    }
    return null
}
