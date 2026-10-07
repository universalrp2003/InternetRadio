package com.universalrp.appforge.model

/**
 * A single building block of a screen.
 *
 * [text] is the main content (heading text, paragraph, button label, list items…)
 * and [href] is used by LINK / BUTTON / IMAGE. Buttons pointing at "#screen:<id>"
 * switch to another screen inside the built app; anything else opens a URL.
 */
data class Block(
    val id: String,
    val type: BlockType,
    val text: String = "",
    val href: String = "",
)

enum class BlockType(
    val label: String,
    val hint: String,
    val hasText: Boolean = true,
    val hasHref: Boolean = false,
    val multiline: Boolean = false,
) {
    HEADING("Heading", "Big title text"),
    PARAGRAPH("Paragraph", "Body text", multiline = true),
    NOTE("Tip / note", "Highlighted note", multiline = true),
    LIST("Bullet list", "One item per line", multiline = true),
    LINK("Link", "Link text", hasHref = true),
    BUTTON("Button", "Button label", hasHref = true),
    IMAGE("Image", "Caption (optional)", hasHref = true),
    FIELD("Text field", "Field label — what the user types is saved on the phone"),
    SAVE("Save button", "Saves every text field on this screen"),
    CHECKLIST("Checklist", "One task per line — ticks are remembered", multiline = true),
    DIVIDER("Divider", "A thin separator line", hasText = false),
}

/** One screen of the app being built. */
data class AppScreen(
    val id: String,
    val title: String,
    val blocks: List<Block> = emptyList(),
)

/** A complete app project. This is what gets saved as JSON and exported. */
data class AppProject(
    val id: String,
    val name: String,
    val emoji: String = "🚀",
    val accent: String = "#22D3EE",
    val description: String = "",
    val screens: List<AppScreen> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** The six accent colours offered by the editor. */
val ACCENT_COLORS = listOf(
    "#22D3EE", // cyan
    "#8B5CF6", // violet
    "#34D399", // green
    "#FBBF24", // amber
    "#F87171", // red
    "#F472B6", // pink
)

/** Simple id generator — no UUID dependency needed. */
fun newId(prefix: String): String =
    prefix + "-" + System.currentTimeMillis().toString(36) + "-" + (0..9999).random()
