package com.universalrp.cleansweep.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * 2.42: the main screen's spacing contract. Pure dp integers, so this runs on the JVM without
 * Compose — and it is a *layout* contract only. Whether nothing clips at a large font scale, on
 * a small screen or in split screen still needs the owner's phone.
 */
class HomeLayoutPolicyTest {

    /** The actual clipping bug: the card list had no top inset, so card one touched the header. */
    @Test fun theCardListHasATopGapSoTheFirstCardIsNotClipped() {
        val padding = HomeLayoutPolicy.listContentPadding()
        assertEquals(4, padding.size)
        assertTrue("top inset must be positive", padding[1] > 0)
        assertEquals(HomeLayoutPolicy.LIST_TOP_DP, padding[1])
        assertEquals(HomeLayoutPolicy.SCREEN_HORIZONTAL_DP, padding[0])
        assertEquals(HomeLayoutPolicy.SCREEN_HORIZONTAL_DP, padding[2])
        assertTrue(padding[3] > 0)
    }

    @Test fun everyScreenLevelGapIsOnOneScale() {
        assertTrue(HomeLayoutPolicy.onScale(HomeLayoutPolicy.BASE_DP))
        for (value in HomeLayoutPolicy.scale) {
            assertTrue("$value dp is off the ${HomeLayoutPolicy.BASE_DP} dp scale", HomeLayoutPolicy.onScale(value))
            assertEquals(value, HomeLayoutPolicy.scaled(value))
        }
        // The scale itself lists every value the home screen uses for edges, gaps and padding.
        assertTrue(HomeLayoutPolicy.scale.size >= 10)
        assertTrue(HomeLayoutPolicy.scale.containsAll(HomeLayoutPolicy.listContentPadding()))
    }

    /** Four hand-picked edges (14/16/14/16) were the "ugly, unprofessional" inconsistency. */
    @Test fun theHeaderTheStripsAndTheListShareOneScreenEdge() {
        assertEquals(16, HomeLayoutPolicy.SCREEN_HORIZONTAL_DP)
        assertFalse("the old 14 dp edge must not come back", HomeLayoutPolicy.scale.contains(14))
        assertFalse(HomeLayoutPolicy.onScale(14))
        // Card padding has exactly two sizes, both on the scale, instead of 12/14/16 mixed.
        assertTrue(HomeLayoutPolicy.onScale(HomeLayoutPolicy.CARD_PADDING_DP))
        assertTrue(HomeLayoutPolicy.onScale(HomeLayoutPolicy.COMPACT_CARD_PADDING_DP))
        assertTrue(HomeLayoutPolicy.CARD_PADDING_DP > HomeLayoutPolicy.COMPACT_CARD_PADDING_DP)
    }

    @Test fun gapsAreComfortableAndNeverTighterThanTheTopInset() {
        assertTrue(HomeLayoutPolicy.isCoherent())
        assertTrue(HomeLayoutPolicy.CARD_GAP_DP >= HomeLayoutPolicy.LIST_TOP_DP)
        assertTrue(HomeLayoutPolicy.LIST_BOTTOM_DP > HomeLayoutPolicy.CARD_GAP_DP)
        assertTrue(HomeLayoutPolicy.TEXT_GAP_DP > 0)
        // Android's recommended minimum tap target; nothing smaller may be called a control.
        assertTrue(HomeLayoutPolicy.MIN_CONTROL_DP >= 48)
    }

    @Test fun offScaleNumbersAreCorrectedInsteadOfShipped() {
        assertEquals(16, HomeLayoutPolicy.scaled(14))
        assertEquals(12, HomeLayoutPolicy.scaled(13))
        assertEquals(8, HomeLayoutPolicy.scaled(8))
        assertEquals(HomeLayoutPolicy.BASE_DP, HomeLayoutPolicy.scaled(0))
        assertEquals(HomeLayoutPolicy.BASE_DP, HomeLayoutPolicy.scaled(-6))
        assertFalse(HomeLayoutPolicy.onScale(0))
        assertFalse(HomeLayoutPolicy.onScale(-4))
        assertTrue(HomeLayoutPolicy.onScale(HomeLayoutPolicy.scaled(14)))
    }
}
