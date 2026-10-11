package com.universalrp.cleansweep.notifications

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** No AI request or online translation fallback. Model downloads are a separate, explicit UI action. */
object LocalMessageTranslator {
    private suspend fun <T> await(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { if (continuation.isActive) continuation.resumeWith(Result.success(it)) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) }
            .addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
    suspend fun ready(): Boolean = withTimeoutOrNull(3_000) {
        val manager = RemoteModelManager.getInstance()
        await(manager.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.TAMIL).build()))
    } ?: false
    /** Caller shows model-download consent/data notice. Defaults to Wi-Fi only, no messages are involved. */
    suspend fun download(wifiOnly: Boolean = true): Boolean = withTimeoutOrNull(120_000) {
        val options = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.TAMIL).build()
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            await(translator.downloadModelIfNeeded(conditions)); true
        } finally { translator.close() }
    } ?: false
    suspend fun translate(text: String, target: String): String? {
        if (text.none { it.isLetter() }) return text
        val identifier = LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.7f).build())
        val source = try { withTimeoutOrNull(3_000) { await(identifier.identifyLanguage(text)) } } finally { identifier.close() }
        if (source !in setOf("en", "ta")) return null // Unknown/other languages do not get a guessed English reading.
        if (source == target) return text
        if (!ready()) return null // Never silently download a model when a private notification arrives.
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source!!).setTargetLanguage(target).build())
        return try { withTimeoutOrNull(5_000) { await(translator.translate(text.take(NotificationReadingPolicy.MAX_BODY))).trim().takeIf { it.isNotBlank() } } }
        finally { translator.close() }
    }
}
