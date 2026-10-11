package com.universalrp.cleansweep.ai

import java.text.Normalizer
import java.util.Locale

/** Exact model/generation evidence. A related encyclopedia result is never a current seller price. */
object ProductEvidencePolicy {
    data class Product(val brand: String, val series: String, val number: String, val radio: String?) {
        val label: String get() = (listOf(brand.replaceFirstChar { it.uppercase() }, series, number) + listOfNotNull(radio?.uppercase(Locale.ROOT)))
            .filter { it.isNotBlank() }.joinToString(" ")
    }
    private fun normal(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace("ரெட்மி", "redmi").replace("போகோ", "poco")
        .replace(Regex("(?<=[a-z])(?=[0-9])"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
    private val model = Regex("\\b(redmi|poco|iphone|oneplus|pixel|galaxy|realme)\\s+(?:(note|nord|a|s|m|f)\\s*)?(\\d{1,3}[a-z]{0,2})\\b(?:\\s+(5g|4g))?")
    fun requested(query: String): Product? = model.find(normal(query))?.let {
        Product(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4].ifBlank { null })
    }
    fun priceQuestion(query: String): Boolean = Regex("\\b(price|prices|cost|costs|pricing)\\b").containsMatchIn(normal(query)) || query.contains("விலை")
    fun exactModel(text: String, query: String): Boolean {
        val product = requested(query) ?: return true
        return model.findAll(normal(text)).any {
            it.groupValues[1] == product.brand && it.groupValues[2] == product.series && it.groupValues[3] == product.number &&
                (product.radio == null || it.groupValues[4] == product.radio)
        }
    }
    fun sellerPriceEvidence(source: String, text: String, query: String): Boolean = exactModel(text, query) &&
        !source.contains("wikipedia", true) && !text.contains("wikipedia.org", true) &&
        !Regex("\\b(vs|versus|comparison|compare)\\b").containsMatchIn(normal(text)) &&
        Regex("(?i)(₹\\s*[\\d,]+|\\b(?:rs\\.?|inr|usd)\\s*[\\d,]+|\\$\\s*[\\d,]+)").containsMatchIn(text)

    fun promptRule(query: String): String {
        val product = requested(query) ?: return ""
        return "The exact requested product is ${product.label}. Do not substitute another generation, radio variant, or similarly named model. " +
            if (priceQuestion(query)) "Only quote prices explicitly present in exact-product seller evidence or actual cited web-search results. " +
                "Fetch time is not price verification. Distinguish launch/list price from a current listing; mention storage variant, seller, region and offers when supported. " +
                "If no exact-product price evidence exists, say the current price could not be verified. Never fill in a price from memory. " else ""
    }
}
