package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

/** Source-only prepared cases; must run in both authorized build variants before publication. */
class ArenaRatingPolicyTest {
    @Test fun exactOnly() { val row = ArenaRatingPolicy.Score("test-model-1", 1000.0, null, null); assertEquals(row, ArenaRatingPolicy.exact("test-model-1", listOf(row))) }
    @Test fun nearbyNameNotSubstituted() { val row = ArenaRatingPolicy.Score("test-model-1", 1000.0, null, null); assertNull(ArenaRatingPolicy.exact("test-model-2", listOf(row))) }
    @Test fun providerAliasNotStripped() { val row = ArenaRatingPolicy.Score("test-model-1", 1000.0, null, null); assertNull(ArenaRatingPolicy.exact("provider/test-model-1:free", listOf(row))) }
    @Test fun ambiguousDuplicatesUnavailable() { val row = ArenaRatingPolicy.Score("test-model-1", 1000.0, null, null); assertNull(ArenaRatingPolicy.exact("test-model-1", listOf(row, row))) }
    @Test fun invalidValuesRejected() { assertFalse(ArenaRatingPolicy.valid(Double.NaN)); assertFalse(ArenaRatingPolicy.valid(Double.POSITIVE_INFINITY)); assertFalse(ArenaRatingPolicy.valid(-1.0)) }
    @Test fun recentWindowOnly() { assertTrue(ArenaRatingPolicy.current(1000, 1000 + ArenaRatingPolicy.CURRENT_WINDOW_MS)); assertFalse(ArenaRatingPolicy.current(1000, 1001 + ArenaRatingPolicy.CURRENT_WINDOW_MS)) }
    @Test fun futureNotCurrent() { assertFalse(ArenaRatingPolicy.current(1000, 999)) }
    @Test fun unknownDateNotCurrent() { assertFalse(ArenaRatingPolicy.current(0, 1000)) }
}
