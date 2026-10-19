package com.rpideas.secretmessage.preview.ui

import com.rpideas.secretmessage.preview.chat.StrokePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingPadLayoutTest {
    @Test
    fun ownerPhoneSizesGetAPadAboutHalfTheScreenTall() {
        // About 360 x 682 dp usable: the pad is as wide as the deck allows.
        val narrowPhone = DrawingPadLayout.padSideDp(screenWidthDp = 360f, screenHeightDp = 682f)
        assertEquals(328f, narrowPhone, 0.01f)
        assertTrue(narrowPhone >= 0.45f * 682f)

        // About 393 x 812 dp usable.
        val tallPhone = DrawingPadLayout.padSideDp(screenWidthDp = 393f, screenHeightDp = 812f)
        assertEquals(361f, tallPhone, 0.01f)
        assertTrue(tallPhone >= 0.44f * 812f)
    }

    @Test
    fun pixel2SizedScreenKeepsRoomForTheControlsAndSomeChat() {
        val side = DrawingPadLayout.padSideDp(screenWidthDp = 411f, screenHeightDp = 659f)
        assertEquals(659f - DrawingPadLayout.CONTROLS_HEIGHT_DP - DrawingPadLayout.MIN_VISIBLE_CHAT_DP, side, 0.01f)
        // More than twice the 140 dp pad of 0.2.0-preview.
        assertTrue(side > 2 * LINE_WIDTH_REFERENCE_PAD_DP)
    }

    @Test
    fun portraitPadsStaySquareInsideTheDeckAndLeaveTheChatVisible() {
        for (width in 320..480 step 16) {
            for (height in 600..1_000 step 25) {
                val side = DrawingPadLayout.padSideDp(width.toFloat(), height.toFloat())
                assertTrue("$width x $height", side <= width - DrawingPadLayout.HORIZONTAL_INSETS_DP)
                assertTrue("$width x $height", side <= height * DrawingPadLayout.TARGET_HEIGHT_FRACTION)
                assertTrue(
                    "$width x $height",
                    side + DrawingPadLayout.CONTROLS_HEIGHT_DP + DrawingPadLayout.MIN_VISIBLE_CHAT_DP <= height,
                )
            }
        }
    }

    @Test
    fun shortScreensFallBackToTheCompactPad() {
        assertEquals(
            DrawingPadLayout.MIN_PAD_SIDE_DP,
            DrawingPadLayout.padSideDp(screenWidthDp = 731f, screenHeightDp = 339f),
            0.01f,
        )
    }

    @Test
    fun lineWidthIsMeasuredAgainstTheOriginalPadSoDrawingsKeepTheirProportions() {
        assertEquals(140f, LINE_WIDTH_REFERENCE_PAD_DP, 0.01f)
        assertEquals(5f / 140f, StrokePolicy.widthFraction(5f, LINE_WIDTH_REFERENCE_PAD_DP), 0.0001f)
        assertEquals(1f, PICTURE_ASPECT_RATIO, 0.0001f)
    }
}
