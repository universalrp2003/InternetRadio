package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

class ProductEvidencePolicyTest {
    private val query = "Redmi13 5G current price in India"
    @Test fun recognizesJoinedBrandAndModel() { assertEquals("Redmi 13 5G", ProductEvidencePolicy.requested(query)?.label) }
    @Test fun spacedAndJoinedNamesReferToSameProduct() { assertTrue(ProductEvidencePolicy.exactModel("Xiaomi Redmi 13 5G listing", query)) }
    @Test fun differentGenerationCannotMatch() { assertFalse(ProductEvidencePolicy.exactModel("Redmi 12 5G: ₹11,999", query)) }
    @Test fun differentRadioCannotMatch() { assertFalse(ProductEvidencePolicy.exactModel("Redmi 13 4G: ₹11,999", query)) }
    @Test fun missingRadioIsNotEvidenceOfFiveG() { assertFalse(ProductEvidencePolicy.exactModel("Redmi 13 specifications", query)) }
    @Test fun differentLetterVariantCannotMatch() { assertFalse(ProductEvidencePolicy.exactModel("Redmi 13C 5G ₹9,999", query)) }
    @Test fun tamilQueryKeepsExactEntity() { assertEquals("Redmi 13 5G", ProductEvidencePolicy.requested("ரெட்மி13 5G விலை என்ன")?.label) }
    @Test fun wikipediaIsNotSellerPriceEvidence() { assertFalse(ProductEvidencePolicy.sellerPriceEvidence("Wikipedia", "Redmi 13 5G launch price ₹13,999", query)) }
    @Test fun comparisonPriceIsNotAnExactSellerPrice() { assertFalse(ProductEvidencePolicy.sellerPriceEvidence("Search excerpt", "Redmi 13 5G vs Redmi 12 5G ₹9,999", query)) }
    @Test fun aProductMentionWithoutPriceIsInsufficient() { assertFalse(ProductEvidencePolicy.sellerPriceEvidence("Store", "Redmi 13 5G details", query)) }
    @Test fun exactListingExcerptCanBeShownAsUnverifiedEvidence() { assertTrue(ProductEvidencePolicy.sellerPriceEvidence("Seller", "Redmi 13 5G 6 GB: INR 13,999", query)) }
    @Test fun unrelatedLexicalOverlapCannotRestoreWrongModel() { assertFalse(AssistantEvidencePolicy.relevant("Redmi 12 5G price today", query)); assertTrue(ProductEvidencePolicy.promptRule(query).contains("Never fill in a price from memory")) }
}
