package com.universalrp.cleansweep.ui

/**
 * 2.42: **one** spacing scale for the main screen, so nothing is clipped under the header, no
 * two rows collide, and every gap is a whole multiple of the same base unit.
 *
 * Before this, the home screen carried four hand-picked edge values (14 / 16 / 14 / 16 dp), a
 * 6 dp header inset and — the actual clipping bug — a `LazyColumn` with **no top padding at
 * all**, so the first card was drawn straight against the header and the LED strip above it.
 *
 * The values are plain dp integers on purpose: a pure JVM test can assert the scale without a
 * Compose or Android dependency, and the UI applies `.dp` at the call site. This is a layout
 * contract, **not** a rendering certificate — actual clipping at large font scales, split
 * screens and small phone screens still needs a physical device.
 */
object HomeLayoutPolicy {

    /**
     * The screen-level gaps — edges, header, card-to-card and card padding — are whole
     * multiples of this. A card *body* keeps its own text rhythm, which is deliberately not
     * forced onto this scale; claiming one number for every pixel on the screen would be an
     * overclaim this file cannot back up.
     */
    const val BASE_DP = 4

    /** Screen edge to card edge. One value for the header, the LED strip, the news strip and the list. */
    const val SCREEN_HORIZONTAL_DP = 16

    /** Card to card, down the list. 2.42 raised this from 14 so rows no longer crowd. */
    const val CARD_GAP_DP = 16

    /**
     * Above the first card. This is the gap that was missing entirely: the header is drawn
     * above the list, so a zero top inset put the first card flush against it.
     */
    const val LIST_TOP_DP = 12

    /** Below the last card, clear of the navigation bar. */
    const val LIST_BOTTOM_DP = 28

    /** Inside a card: edge to its content. */
    const val CARD_PADDING_DP = 16

    /** A dense single-row card (the update row) keeps a slightly tighter inner padding. */
    const val COMPACT_CARD_PADDING_DP = 12

    /** Header row: vertical inset, and the gap between the mark and the app name. */
    const val HEADER_VERTICAL_DP = 8
    const val HEADER_ICON_GAP_DP = 12

    /** Header row trailing inset: the icon buttons carry their own touch padding. */
    const val HEADER_TRAILING_DP = 8

    /** Line to line inside a card body. */
    const val TEXT_GAP_DP = 8

    /** Tap targets keep Android's recommended minimum; nothing smaller is a control. */
    const val MIN_CONTROL_DP = 48

    /** Every value this screen uses, so a test can hold the whole scale to [BASE_DP]. */
    val scale: List<Int> = listOf(
        SCREEN_HORIZONTAL_DP, CARD_GAP_DP, LIST_TOP_DP, LIST_BOTTOM_DP, CARD_PADDING_DP,
        COMPACT_CARD_PADDING_DP, HEADER_VERTICAL_DP, HEADER_ICON_GAP_DP, HEADER_TRAILING_DP,
        TEXT_GAP_DP, MIN_CONTROL_DP,
    )

    /** True when a value belongs to the scale; false means somebody hand-picked a number. */
    fun onScale(dp: Int): Boolean = dp > 0 && dp % BASE_DP == 0

    /** The nearest value on the scale, so a one-off number can be corrected instead of shipped. */
    fun scaled(dp: Int): Int = if (dp <= 0) BASE_DP else ((dp + BASE_DP / 2) / BASE_DP) * BASE_DP

    /**
     * The list's own content padding as start, top, end, bottom. A non-zero top is the clipping
     * fix and is asserted by [HomeLayoutPolicyTest] so it cannot silently go back to zero.
     */
    fun listContentPadding(): List<Int> =
        listOf(SCREEN_HORIZONTAL_DP, LIST_TOP_DP, SCREEN_HORIZONTAL_DP, LIST_BOTTOM_DP)

    /** Card gap must never be smaller than the gap above the first card, or the list looks top-heavy. */
    fun isCoherent(): Boolean = CARD_GAP_DP >= LIST_TOP_DP && LIST_BOTTOM_DP > CARD_GAP_DP && onScale(BASE_DP)
}
