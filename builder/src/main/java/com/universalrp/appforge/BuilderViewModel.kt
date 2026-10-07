package com.universalrp.appforge

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalrp.appforge.model.ACCENT_COLORS
import com.universalrp.appforge.model.AppProject
import com.universalrp.appforge.model.AppScreen
import com.universalrp.appforge.model.Block
import com.universalrp.appforge.model.BlockType
import com.universalrp.appforge.model.HtmlRenderer
import com.universalrp.appforge.model.ProjectRepo
import com.universalrp.appforge.model.Templates
import com.universalrp.appforge.model.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BScreen { HOME, EDITOR, PREVIEW, EXPORT, ABOUT }

/** Shown on the home and about screens. */
const val APP_VERSION = "1.0"

/** Files offered in the generated-code viewer. */
val CODE_FILES = listOf(
    "app/src/main/java/MainActivity.kt",
    "app/src/main/AndroidManifest.xml",
    "app/build.gradle.kts",
    "app/src/main/assets/index.html",
    ".github/workflows/build-apk.yml",
    "README.md",
)

data class BuilderState(
    val screen: BScreen = BScreen.HOME,
    val projects: List<AppProject> = emptyList(),
    val project: AppProject? = null,
    val activeScreen: Int = 0,
    val showTemplates: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val exportInfo: String? = null,
    val codePath: String = CODE_FILES.first(),
    val codeText: String = "",
)

class BuilderViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val repo = ProjectRepo(ctx)

    private val _state = MutableStateFlow(BuilderState())
    val state: StateFlow<BuilderState> = _state

    init {
        refreshProjects()
    }

    private fun mutate(block: (BuilderState) -> BuilderState) {
        _state.update(block)
    }

    private fun refreshProjects() {
        viewModelScope.launch {
            val list = repo.list()
            mutate { it.copy(projects = list) }
        }
    }

    fun dismissMessage() = mutate { it.copy(message = null) }
    fun navigate(screen: BScreen) = mutate { it.copy(screen = screen) }
    fun showTemplates(show: Boolean) = mutate { it.copy(showTemplates = show) }

    // -------------------------------------------------------------- projects

    fun newProject(template: AppProject) {
        viewModelScope.launch {
            repo.save(template)
            mutate {
                it.copy(
                    project = template,
                    activeScreen = 0,
                    screen = BScreen.EDITOR,
                    showTemplates = false,
                    exportInfo = null,
                )
            }
            refreshProjects()
        }
    }

    fun openProject(project: AppProject) = mutate {
        it.copy(
            project = project,
            activeScreen = 0,
            screen = BScreen.EDITOR,
            exportInfo = null,
            codePath = CODE_FILES.first(),
            codeText = "",
        )
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            refreshProjects()
        }
    }

    /** Saves the open project and returns to the project list. */
    fun closeEditor() {
        val p = _state.value.project
        viewModelScope.launch {
            p?.let { repo.save(it) }
            refreshProjects()
        }
        mutate { it.copy(screen = BScreen.HOME, project = null, exportInfo = null) }
    }

    private fun updateProject(block: (AppProject) -> AppProject) {
        val current = _state.value.project ?: return
        val updated = block(current)
        mutate { it.copy(project = updated) }
        viewModelScope.launch { repo.save(updated) }
    }

    private fun mapActiveScreen(block: (AppScreen) -> AppScreen) {
        updateProject { p ->
            val index = _state.value.activeScreen.coerceIn(
                0,
                (p.screens.size - 1).coerceAtLeast(0),
            )
            p.copy(
                screens = p.screens.mapIndexed { i, s -> if (i == index) block(s) else s }
            )
        }
    }

    // ------------------------------------------------------------------ app

    fun setAppName(name: String) = updateProject { it.copy(name = name) }
    fun setEmoji(emoji: String) = updateProject { it.copy(emoji = emoji.take(3)) }
    fun setAccent(hex: String) = updateProject { it.copy(accent = hex) }
    fun setDescription(text: String) = updateProject { it.copy(description = text) }

    // --------------------------------------------------------------- screens

    fun selectScreen(index: Int) = mutate { it.copy(activeScreen = index) }

    fun addScreen() {
        updateProject { p ->
            p.copy(screens = p.screens + AppScreen(newId("scr"), "Screen ${p.screens.size + 1}"))
        }
        mutate { it.copy(activeScreen = (_state.value.project?.screens?.size ?: 1) - 1) }
    }

    fun deleteActiveScreen() {
        val p = _state.value.project ?: return
        if (p.screens.size <= 1) {
            mutate { it.copy(message = "An app needs at least one screen.") }
            return
        }
        val index = _state.value.activeScreen
        updateProject { project ->
            project.copy(screens = project.screens.filterIndexed { i, _ -> i != index })
        }
        mutate { it.copy(activeScreen = 0) }
    }

    fun renameActiveScreen(title: String) = mapActiveScreen { it.copy(title = title) }

    // ---------------------------------------------------------------- blocks

    fun addBlock(type: BlockType) = mapActiveScreen { screen ->
        screen.copy(blocks = screen.blocks + Block(id = newId("blk"), type = type))
    }

    fun updateBlock(id: String, text: String, href: String) = mapActiveScreen { screen ->
        screen.copy(
            blocks = screen.blocks.map { b ->
                if (b.id == id) b.copy(text = text, href = href) else b
            }
        )
    }

    fun deleteBlock(id: String) = mapActiveScreen { screen ->
        screen.copy(blocks = screen.blocks.filterNot { it.id == id })
    }

    fun moveBlock(id: String, direction: Int) = mapActiveScreen { screen ->
        val list = screen.blocks.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + direction
        if (index >= 0 && target in list.indices) {
            val item = list.removeAt(index)
            list.add(target, item)
        }
        screen.copy(blocks = list)
    }

    // --------------------------------------------------------------- preview

    fun previewHtml(): String =
        _state.value.project?.let { HtmlRenderer.render(it) } ?: ""

    // ---------------------------------------------------------------- export

    fun exportHtml(share: Boolean) {
        val project = _state.value.project ?: return
        mutate { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { repo.exportHtml(project) }
            val file = result.getOrNull()
            if (file == null) {
                mutate { it.copy(busy = false, message = "Export failed: ${result.exceptionOrNull()?.message ?: "unknown error"}") }
                return@launch
            }
            mutate {
                it.copy(
                    busy = false,
                    exportInfo = "HTML app saved: ${file.name} (${file.length() / 1024} KB) — ${repo.exportPathLabel()}",
                )
            }
            if (share) {
                runCatching { ctx.startActivity(repo.shareIntent(file, "text/html")) }
                    .onFailure { mutate { s -> s.copy(message = "No app available to share with.") } }
            }
        }
    }

    fun exportAndroidProject(share: Boolean) {
        val project = _state.value.project ?: return
        mutate { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { repo.exportAndroidProject(project) }
            val file = result.getOrNull()
            if (file == null) {
                mutate { it.copy(busy = false, message = "Export failed: ${result.exceptionOrNull()?.message ?: "unknown error"}") }
                return@launch
            }
            mutate {
                it.copy(
                    busy = false,
                    exportInfo = "Android project saved: ${file.name} (${file.length() / 1024} KB) — ${repo.exportPathLabel()}",
                )
            }
            if (share) {
                runCatching { ctx.startActivity(repo.shareIntent(file, "application/zip")) }
                    .onFailure { mutate { s -> s.copy(message = "No app available to share with.") } }
            }
        }
    }

    fun loadCode(path: String) {
        val project = _state.value.project ?: return
        val text = repo.previewCode(project, path)
        mutate { it.copy(codePath = path, codeText = text) }
    }

    fun copyCode() {
        val text = _state.value.codeText
        if (text.isBlank()) {
            mutate { it.copy(message = "Nothing to copy yet — pick a file first.") }
            return
        }
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AppForge code", text))
        mutate { it.copy(message = "Copied to clipboard.") }
    }

    fun shareCode() {
        val project = _state.value.project ?: return
        val text = _state.value.codeText
        if (text.isBlank()) {
            mutate { it.copy(message = "Pick a file first.") }
            return
        }
        viewModelScope.launch {
            val file = runCatching { repo.exportCodeText(project, text) }.getOrNull()
            if (file == null) {
                mutate { it.copy(message = "Could not prepare the file.") }
                return@launch
            }
            runCatching { ctx.startActivity(repo.shareIntent(file, "text/plain")) }
                .onFailure { mutate { s -> s.copy(message = "No app available to share with.") } }
        }
    }

    fun accentColors(): List<String> = ACCENT_COLORS

    fun templates(): List<AppProject> = Templates.all()
}
