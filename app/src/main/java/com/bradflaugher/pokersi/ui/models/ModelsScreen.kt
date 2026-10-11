package com.bradflaugher.pokersi.ui.models

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bradflaugher.pokersi.llm.DownloadProgress
import com.bradflaugher.pokersi.llm.EngineState
import com.bradflaugher.pokersi.llm.LlamaEngine
import com.bradflaugher.pokersi.llm.LocalModel
import com.bradflaugher.pokersi.llm.ModelLocation
import com.bradflaugher.pokersi.llm.ModelStore
import com.bradflaugher.pokersi.ui.pixel.CrtOverlay
import com.bradflaugher.pokersi.ui.screens.ConsoleSlot
import com.bradflaugher.pokersi.ui.pixel.LocalPixelPalette
import com.bradflaugher.pokersi.ui.pixel.PanelStyle
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.FeltBackground
import com.bradflaugher.pokersi.ui.pixel.FeltColors
import kotlinx.coroutines.launch
import java.util.Locale

/** Everything [ModelsContent] needs, so screenshots can script it without a device. */
data class ModelsUiState(
    val engine: EngineState,
    val models: List<LocalModel>,
    val folders: List<Uri>,
    /** Single files the user imported; only these can be forgotten one by one (folder models go with their folder). */
    val importedFiles: List<Uri> = emptyList(),
    /** Keeps the header's top right free for the debug console's "~" (SETTINGS > DEBUG CONSOLE): load errors land there. */
    val consoleSlot: Boolean = false,
)

/** What the model bay's buttons do. */
interface ModelsActions {
    fun download(model: LocalModel)
    fun cancel(model: LocalModel)
    fun load(model: LocalModel)
    fun unload()
    fun delete(model: LocalModel)
    fun addFolder()
    fun addFile()
    fun removeFolder(uri: Uri)
    fun back()
}

/** The model bay, wired to [LlamaEngine] and [ModelStore]. */
@Composable
fun ModelsScreen(onBack: () -> Unit, consoleSlot: Boolean = false) {
    val context = LocalContext.current
    val engine = remember(context) { LlamaEngine.get(context) }
    val store = remember(context) { ModelStore.get(context) }
    val scope = rememberCoroutineScope()
    val engineState by engine.state.collectAsState()
    val models by store.models.collectAsState()
    val folders by store.folders.collectAsState()
    val importedFiles by store.files.collectAsState()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) store.addFolder(uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) store.importFile(uri)
    }
    // Progress shows in a notification; the download runs whether or not it's allowed.
    var pendingDownload by remember { mutableStateOf<LocalModel?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.catalog?.let(store::download)
        pendingDownload = null
    }

    BackHandler(onBack = onBack)
    val actions = object : ModelsActions {
        override fun download(model: LocalModel) {
            val catalog = model.catalog ?: return
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                store.download(catalog)
            } else {
                pendingDownload = model
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        override fun cancel(model: LocalModel) = store.cancelDownload(model.id)
        override fun load(model: LocalModel) {
            scope.launch { engine.load(model) }
        }

        override fun unload() {
            scope.launch { engine.unload() }
        }

        override fun delete(model: LocalModel) {
            scope.launch {
                if ((engine.state.value as? EngineState.Ready)?.modelId == model.id) engine.unload()
                store.delete(model)
            }
        }

        override fun addFolder() = pickFolder.launch(null)
        override fun addFile() = pickFile.launch(arrayOf("application/octet-stream", "*/*"))
        override fun removeFolder(uri: Uri) = store.removeFolder(uri)
        override fun back() = onBack()
    }
    ModelsContent(ModelsUiState(engineState, models, folders, importedFiles, consoleSlot = consoleSlot), actions)
}

/** The model bay itself: what's loaded, the catalog, and the player's own GGUFs. */
@Composable
fun ModelsContent(state: ModelsUiState, actions: ModelsActions, modifier: Modifier = Modifier) {
    val p = LocalPixelPalette.current
    // The starter model first, then what's already here, then smallest to largest.
    val catalog = state.models.filter { !it.isCustom }
        .sortedWith(compareByDescending<LocalModel> { it.catalog?.recommended == true }.thenByDescending { it.isAvailable }.thenBy { it.sizeBytes })
    val custom = state.models.filter { it.isCustom }
    FeltBackground(modifier.fillMaxSize(), colors = FeltColors.Lobby) {
        CrtOverlay(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("BACK", actions::back, variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
                            Spacer(Modifier.padding(6.dp))
                            // The titles take what's left, so the console button always keeps its width.
                            Column(Modifier.weight(1f)) {
                                PixelText("MODEL BAY", style = PixelType.Title, color = p.yellow, maxLines = 1)
                                PixelText(
                                    "Pick a brain for your opponents", style = PixelType.BodySmall, color = p.cabinetGlow, shadowUnits = 0,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (state.consoleSlot) ConsoleSlot()
                        }
                    }
                    item { EnginePanel(state.engine, actions) }
                    item { SectionHeader("DOWNLOAD A BRAIN") }
                    items(catalog, key = { it.id }) { model -> ModelRow(model, state.engine, actions, removable(model, state)) }
                    item { SectionHeader("YOUR OWN GGUF FILES") }
                    item {
                        PixelPanel(Modifier.fillMaxWidth(), style = PanelStyle.Dark) {
                            PixelText(
                                "Got GGUF models already? Point PokerSI at a folder or a file. " +
                                    "Nothing is copied: the model loads right where it is.",
                                style = PixelType.BodySmall, shadowUnits = 0,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("ADD FOLDER", actions::addFolder, variant = PixelButtonVariant.Hold, textStyle = PixelType.Small)
                                PixelButton("ADD FILE", actions::addFile, variant = PixelButtonVariant.Hold, textStyle = PixelType.Small)
                            }
                            state.folders.forEach { uri ->
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PixelText(
                                        "📁 " + folderName(uri), Modifier.weight(1f),
                                        style = PixelType.BodySmall, color = p.cabinetGlow, shadowUnits = 0, maxLines = 1,
                                    )
                                    PixelButton("REMOVE", { actions.removeFolder(uri) }, variant = PixelButtonVariant.Gray, textStyle = PixelType.Small, sound = null)
                                }
                            }
                        }
                    }
                    if (custom.isEmpty()) {
                        item {
                            PixelText(
                                "No custom models yet.", Modifier.fillMaxWidth(),
                                style = PixelType.BodySmall, color = p.gray, textAlign = TextAlign.Center, shadowUnits = 0,
                            )
                        }
                    }
                    items(custom, key = { it.id }) { model -> ModelRow(model, state.engine, actions, removable(model, state)) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    PixelText(text, Modifier.padding(top = 8.dp), style = PixelType.Heading, color = LocalPixelPalette.current.hotPink)
}

@Composable
private fun EnginePanel(engine: EngineState, actions: ModelsActions) {
    val p = LocalPixelPalette.current
    val (headline, detail, color) = when (engine) {
        EngineState.Unavailable -> Triple("HOUSE BOT ONLY", "This build has no AI engine. The House Bot runs the table.", p.gray)
        EngineState.NoModel -> Triple("HOUSE BOT", "No model loaded. Download one below and the AI takes over the table.", p.gold)
        is EngineState.Loading -> Triple("WARMING UP…", "Loading ${engine.name} into memory.", p.cabinetGlow)
        is EngineState.Ready -> Triple(engine.name.uppercase(Locale.US), "Loaded and playing every opponent. ${engine.description}", p.green)
        is EngineState.Failed -> Triple("TILT!", engine.message, p.red)
    }
    PixelPanel(Modifier.fillMaxWidth(), title = "NOW PLAYING") {
        PixelText(headline, style = PixelType.Heading, color = color)
        Spacer(Modifier.height(4.dp))
        PixelText(detail, style = PixelType.BodySmall, shadowUnits = 0)
        if (engine is EngineState.Ready) {
            Spacer(Modifier.height(8.dp))
            PixelButton("UNLOAD", actions::unload, variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
        }
    }
}

@Composable
private fun ModelRow(model: LocalModel, engine: EngineState, actions: ModelsActions, removable: Boolean) {
    val p = LocalPixelPalette.current
    val loaded = (engine as? EngineState.Ready)?.modelId == model.id
    val loading = (engine as? EngineState.Loading)?.modelId == model.id
    val busy = engine is EngineState.Loading
    val catalog = model.catalog
    PixelPanel(Modifier.fillMaxWidth(), style = if (loaded) PanelStyle.Felt else PanelStyle.Cabinet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelText(model.name.uppercase(Locale.US), Modifier.weight(1f), style = PixelType.Small, color = p.yellow)
            if (catalog?.recommended == true) {
                PixelText(
                    "★ START HERE",
                    Modifier.background(p.hotPink).padding(horizontal = 4.dp, vertical = 2.dp),
                    style = PixelType.Small, fontSize = PixelType.Small.fontSize * 0.8f,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val meta = listOfNotNull(catalog?.maker, formatSize(model.sizeBytes)).joinToString(" · ")
        PixelText(meta, style = PixelType.BodySmall, color = p.cabinetGlow, shadowUnits = 0)
        if (catalog != null) PixelText(catalog.description, style = PixelType.BodySmall, shadowUnits = 0)
        if (model.isCustom) PixelText(model.fileName, style = PixelType.BodySmall, color = p.gray, shadowUnits = 0, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        val download = model.download
        when {
            download != null && download.state != DownloadProgress.State.FAILED -> {
                ProgressBar(download)
                Spacer(Modifier.height(6.dp))
                PixelButton("CANCEL", { actions.cancel(model) }, variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
            }
            model.isAvailable -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loaded -> PixelText("✓ LOADED", style = PixelType.Small, color = p.green)
                    loading -> PixelText("LOADING…", style = PixelType.Small, color = p.cabinetGlow)
                    else -> PixelButton("LOAD", { actions.load(model) }, variant = PixelButtonVariant.Call, enabled = !busy, textStyle = PixelType.Small)
                }
                if (!loading && removable) {
                    val label = if (model.isDownloaded) "DELETE" else "FORGET"
                    PixelButton(label, { actions.delete(model) }, variant = PixelButtonVariant.Gray, textStyle = PixelType.Small, sound = null)
                }
            }
            else -> Column {
                if (download?.state == DownloadProgress.State.FAILED) {
                    PixelText("Download failed: ${download.error ?: "unknown error"}", style = PixelType.BodySmall, color = p.red, shadowUnits = 0)
                    Spacer(Modifier.height(4.dp))
                }
                PixelButton("DOWNLOAD", { actions.download(model) }, variant = PixelButtonVariant.Bet, textStyle = PixelType.Small)
            }
        }
    }
}

@Composable
private fun ProgressBar(download: DownloadProgress) {
    val p = LocalPixelPalette.current
    val label = when (download.state) {
        DownloadProgress.State.QUEUED -> "WAITING FOR NETWORK…"
        else -> buildString {
            append("${(download.fraction * 100).toInt()}%  ${formatSize(download.bytes)} / ${formatSize(download.total)}")
            if (download.bytesPerSecond > 0) append("  ${formatSize(download.bytesPerSecond)}/s")
        }
    }
    Box(
        Modifier.fillMaxWidth().height(14.dp).background(p.cabinetBlack).border(2.dp, p.ink),
    ) {
        Box(Modifier.fillMaxWidth(download.fraction).height(14.dp).background(p.green))
    }
    Spacer(Modifier.height(4.dp))
    PixelText(label, style = PixelType.BodySmall, color = p.cabinetGlow, shadowUnits = 0)
}

/** Downloads can be deleted and imported files forgotten; a folder's models leave with the folder. */
private fun removable(model: LocalModel, state: ModelsUiState): Boolean {
    val location = model.location
    return model.isDownloaded || (location is ModelLocation.Document && location.uri in state.importedFiles)
}

private fun folderName(uri: Uri): String =
    Uri.decode(uri.lastPathSegment ?: uri.toString()).substringAfterLast(':').ifEmpty { "Folder" }

/** "1.2 GB", "484 MB". */
fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%d MB", bytes / 1_000_000)
    else -> String.format(Locale.US, "%d KB", bytes / 1_000)
}
