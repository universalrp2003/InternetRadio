package com.rpideas.secretmessage.preview.ui

/**
 * Sizes the square drawing pad. The owner asked for a pad about half the screen tall after testing
 * the 140 dp pad of 0.2.0-preview on two phones, so the pad aims for half of the chat screen's
 * height. It is limited by the deck width, because it stays square so a drawn letter keeps its
 * shape inline, and by the room that the header, composer, pad controls and a strip of the chat
 * still need. Compose also lets the pad shrink if the controls turn out taller than estimated
 * here (for example with a large font size), so they are never pushed off-screen.
 */
internal object DrawingPadLayout {
    /** The pad aims for this fraction of the chat screen's height. */
    const val TARGET_HEIGHT_FRACTION = 0.5f

    /** Header, composer, the two pad control rows, the tool row and paddings at the default font size. */
    const val CONTROLS_HEIGHT_DP = 274f

    /** At least this much of the chat stays visible above the deck while drawing. */
    const val MIN_VISIBLE_CHAT_DP = 80f

    /** Short screens such as landscape keep the earlier compact pad size. */
    const val MIN_PAD_SIDE_DP = 96f

    /** Screen and deck padding on both sides of the pad. */
    const val HORIZONTAL_INSETS_DP = 32f

    /** Side of the square pad, in dp, for a chat screen of the given size in dp. */
    fun padSideDp(screenWidthDp: Float, screenHeightDp: Float): Float {
        val byWidth = screenWidthDp - HORIZONTAL_INSETS_DP
        val byTarget = screenHeightDp * TARGET_HEIGHT_FRACTION
        val byRoom = screenHeightDp - CONTROLS_HEIGHT_DP - MIN_VISIBLE_CHAT_DP
        return minOf(byWidth, byTarget, byRoom).coerceAtLeast(MIN_PAD_SIDE_DP)
    }
}
