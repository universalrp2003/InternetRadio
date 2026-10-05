package com.universalrp.appforge.model

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Stores projects as small JSON files in the app's private folder and writes
 * exports (single-file HTML app, zipped Android project) to the app's external
 * files folder — no storage permission is ever needed, and nothing leaves the
 * phone unless you press Share yourself.
 */
class ProjectRepo(private val context: Context) {

    private fun projectsDir(): File =
        File(context.filesDir, "projects").apply { mkdirs() }

    private fun exportsDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "exports").apply { mkdirs() }
    }

    // -------------------------------------------------------------- projects

    suspend fun list(): List<AppProject> = withContext(Dispatchers.IO) {
        val dir = projectsDir()
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { f -> runCatching { fromJson(f.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun save(project: AppProject) = withContext(Dispatchers.IO) {
        val stamped = project.copy(updatedAt = System.currentTimeMillis())
        File(projectsDir(), stamped.id + ".json").writeText(toJson(stamped))
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(projectsDir(), id + ".json").delete()
        Unit
    }

    // --------------------------------------------------------------- exports

    /** One self-contained HTML file — a real working mini app. */
    suspend fun exportHtml(project: AppProject): File = withContext(Dispatchers.IO) {
        val file = File(exportsDir(), slug(project) + ".html")
        file.writeText(HtmlRenderer.render(project))
        file
    }

    /** Full Android Studio project, zipped and ready to build. */
    suspend fun exportAndroidProject(project: AppProject): File = withContext(Dispatchers.IO) {
        val name = slug(project) + "-android"
        val zip = File(exportsDir(), name + ".zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            AndroidCodeGen.generate(project).forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        zip
    }

    /** Single file from a generated Android project, for the in-app code viewer. */
    fun previewCode(project: AppProject, path: String): String =
        AndroidCodeGen.generate(project)[path] ?: ""

    /** Writes the visible snippet to a text file so it can be shared anywhere. */
    suspend fun exportCodeText(project: AppProject, text: String): File = withContext(Dispatchers.IO) {
        val file = File(exportsDir(), slug(project) + "-code.txt")
        file.writeText(text)
        file
    }

    /** Short, human-readable location of the export folder. */
    fun exportPathLabel(): String {
        val dir = exportsDir()
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        val pretty = dir.absolutePath.removePrefix(root).trimStart('/')
        return "saved to $pretty — use Share to save it to Files, Drive or WhatsApp"
    }

    fun shareIntent(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(send, "Share " + file.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ------------------------------------------------------------ json codec

    private fun toJson(p: AppProject): String {
        val screens = JSONArray()
        p.screens.forEach { screen ->
            val so = JSONObject()
            so.put("id", screen.id)
            so.put("title", screen.title)
            val blocks = JSONArray()
            screen.blocks.forEach { b ->
                val bo = JSONObject()
                bo.put("id", b.id)
                bo.put("type", b.type.name)
                bo.put("text", b.text)
                bo.put("href", b.href)
                blocks.put(bo)
            }
            so.put("blocks", blocks)
            screens.put(so)
        }
        return JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("emoji", p.emoji)
            put("accent", p.accent)
            put("description", p.description)
            put("updatedAt", p.updatedAt)
            put("screens", screens)
        }.toString(2)
    }

    private fun fromJson(raw: String): AppProject {
        val o = JSONObject(raw)
        val screens = mutableListOf<AppScreen>()
        val screensJson = o.optJSONArray("screens") ?: JSONArray()
        for (i in 0 until screensJson.length()) {
            val so = screensJson.getJSONObject(i)
            val blocks = mutableListOf<Block>()
            val blocksJson = so.optJSONArray("blocks") ?: JSONArray()
            for (j in 0 until blocksJson.length()) {
                val bo = blocksJson.getJSONObject(j)
                val type = runCatching {
                    BlockType.valueOf(bo.optString("type", BlockType.PARAGRAPH.name))
                }.getOrDefault(BlockType.PARAGRAPH)
                blocks.add(
                    Block(
                        id = bo.optString("id", newId("blk")),
                        type = type,
                        text = bo.optString("text", ""),
                        href = bo.optString("href", ""),
                    )
                )
            }
            screens.add(
                AppScreen(
                    id = so.optString("id", newId("scr")),
                    title = so.optString("title", "Screen"),
                    blocks = blocks,
                )
            )
        }
        return AppProject(
            id = o.optString("id", newId("prj")),
            name = o.optString("name", "My app"),
            emoji = o.optString("emoji", "🚀"),
            accent = o.optString("accent", "#22D3EE"),
            description = o.optString("description", ""),
            screens = screens,
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    /** Filesystem-safe project name. */
    fun slug(project: AppProject): String {
        val s = project.name.lowercase()
            .map { c -> if (c.isLetterOrDigit()) c else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "app" }
        return s.take(40)
    }
}
