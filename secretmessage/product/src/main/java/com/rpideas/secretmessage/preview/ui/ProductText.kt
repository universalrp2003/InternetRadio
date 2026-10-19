package com.rpideas.secretmessage.preview.ui

/**
 * Interface words of the product screens. The offline preview shows them in English; the
 * published app sets [translate] to its Tamil/English choice. The app keeps that choice in Compose
 * state, so screens that call [t] while composing follow a language change at once.
 */
internal object ProductText {
    @Volatile
    var translate: (String) -> String = { it }
}

/** [english] in the app's language; `{0}`, `{1}`… in it are replaced by [args] after translating. */
internal fun t(english: String, vararg args: Any): String {
    var text = ProductText.translate(english)
    args.forEachIndexed { index, value -> text = text.replace("{$index}", value.toString()) }
    return text
}
