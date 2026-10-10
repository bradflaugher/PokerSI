package com.bradflaugher.pokersi.llm

import android.annotation.SuppressLint
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** What the engine is doing, for the UI. */
sealed interface EngineState {
    /** Built with `-PskipNative` or the native library is missing: House Bot only. */
    data object Unavailable : EngineState
    data object NoModel : EngineState
    data class Loading(val modelId: String, val name: String) : EngineState
    data class Ready(val modelId: String, val name: String, val description: String) : EngineState
    data class Failed(val message: String) : EngineState
}

/** Thrown by [LlamaEngine.generate] when no model is loaded. */
class NoModelException : IllegalStateException("No model loaded")

/**
 * The app's one llama.cpp instance: owns at most one loaded model and runs
 * every native call on a single dedicated thread, so calls never overlap.
 * Use [get] for the shared instance. The last loaded model is remembered
 * and loaded again on the next start.
 */
class LlamaEngine private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("llama_engine", Context.MODE_PRIVATE)

    // Big stack: llama.cpp's Jinja template renderer is recursive.
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "llama", 16L shl 20).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<EngineState>(if (LlamaNative.available) EngineState.NoModel else EngineState.Unavailable)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Native handle, only touched on [dispatcher] (read anywhere for [LlamaNative.cancel]). */
    @Volatile private var handle = 0L
    private var descriptor: ParcelFileDescriptor? = null

    val isReady: Boolean get() = state.value is EngineState.Ready

    /** Threads for inference: the big cores, roughly. */
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    init {
        if (LlamaNative.available) {
            scope.launch(dispatcher) { LlamaNative.init(app.applicationInfo.nativeLibraryDir) }
            rememberedModel()?.let { scope.launch { load(it) } }
        }
    }

    /**
     * Loads [model] (unloading any current one) and remembers it for the next
     * start. Returns false, with [state] set to [EngineState.Failed], when it
     * can't be loaded.
     */
    suspend fun load(model: LocalModel): Boolean {
        if (!LlamaNative.available) return false
        val location = model.location ?: return false
        cancelGeneration()
        return withContext(dispatcher) {
            freeCurrent()
            _state.value = EngineState.Loading(model.id, model.name)
            val path = try {
                when (location) {
                    is ModelLocation.FilePath -> location.file.path
                    is ModelLocation.Document -> {
                        val pfd = app.contentResolver.openFileDescriptor(location.uri, "r")
                            ?: error("Can't open ${model.name}")
                        descriptor = pfd
                        "fd:${pfd.fd}"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "open failed", e)
                _state.value = EngineState.Failed("Can't open ${model.name}: ${e.message}")
                return@withContext false
            }
            val h = LlamaNative.loadModel(path, threads, CONTEXT_SIZE)
            if (h == 0L) {
                closeDescriptor()
                _state.value = EngineState.Failed("${model.name} didn't load. The file may be damaged or too big for this phone.")
                false
            } else {
                handle = h
                remember(model)
                _state.value = EngineState.Ready(model.id, model.name, LlamaNative.describe(h))
                true
            }
        }
    }

    /** Unloads the model and forgets it, so it isn't loaded on the next start. */
    suspend fun unload() {
        cancelGeneration()
        withContext(dispatcher) {
            freeCurrent()
            prefs.edit { clear() }
            if (LlamaNative.available) _state.value = EngineState.NoModel
        }
    }

    /**
     * Generates a reply to [messages], constrained by [grammar] (GBNF, or
     * null for free text). [onText] gets the whole reply so far after each
     * token, on the inference thread, and returns false to stop early.
     * Cancelling the calling coroutine stops
     * generation promptly; after [timeoutMs] it throws
     * [kotlinx.coroutines.TimeoutCancellationException].
     */
    suspend fun generate(
        messages: List<ChatMessage>,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        seed: Int,
        timeoutMs: Long = 25_000,
        onText: (String) -> Boolean = { true },
    ): String = withTimeout(timeoutMs) {
        coroutineScope {
            val finished = AtomicBoolean(false)
            // Stops the native loop (even mid-prompt) when we are cancelled or time out.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    if (!finished.get()) handle.takeIf { it != 0L }?.let(LlamaNative::cancel)
                }
            }
            try {
                withContext(dispatcher) {
                    val h = handle.takeIf { it != 0L } ?: throw NoModelException()
                    val job = currentCoroutineContext()
                    val text = StringBuilder()
                    LlamaNative.generate(
                        h,
                        messages.map { it.role }.toTypedArray(),
                        messages.map { it.content }.toTypedArray(),
                        grammar,
                        maxTokens,
                        temperature,
                        seed,
                    ) { piece ->
                        text.append(piece)
                        onText(text.toString()) && job.isActive
                    }
                }
            } finally {
                finished.set(true)
                watcher.cancel()
            }
        }
    }

    private fun cancelGeneration() {
        handle.takeIf { it != 0L }?.let(LlamaNative::cancel)
    }

    /** On [dispatcher] only. */
    private fun freeCurrent() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            LlamaNative.freeModel(h)
        }
        closeDescriptor()
    }

    private fun closeDescriptor() {
        try {
            descriptor?.close()
        } catch (_: Exception) {
        }
        descriptor = null
    }

    private fun remember(model: LocalModel) {
        val location = model.location ?: return
        prefs.edit {
            putString(KEY_ID, model.id)
            putString(KEY_NAME, model.name)
            putString(KEY_FILE, model.fileName)
            when (location) {
                is ModelLocation.FilePath -> putString(KEY_PATH, location.file.path).remove(KEY_URI)
                is ModelLocation.Document -> putString(KEY_URI, location.uri.toString()).remove(KEY_PATH)
            }
        }
    }

    private fun rememberedModel(): LocalModel? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val location = prefs.getString(KEY_PATH, null)?.let { File(it) }?.takeIf { it.isFile }?.let { ModelLocation.FilePath(it) }
            ?: prefs.getString(KEY_URI, null)?.toUri()?.let { ModelLocation.Document(it) }
            ?: return null
        return LocalModel(
            id = id,
            name = prefs.getString(KEY_NAME, null) ?: id,
            fileName = prefs.getString(KEY_FILE, null) ?: "",
            sizeBytes = 0,
            location = location,
            catalog = ModelCatalog.byId(id),
        )
    }

    companion object {
        private const val TAG = "LlamaEngine"
        const val CONTEXT_SIZE = 4096
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_FILE = "file"
        private const val KEY_PATH = "path"
        private const val KEY_URI = "uri"

        // Holds only the application context.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: LlamaEngine? = null

        /** The app-wide engine. */
        fun get(context: Context): LlamaEngine =
            instance ?: synchronized(this) { instance ?: LlamaEngine(context).also { instance = it } }
    }
}
