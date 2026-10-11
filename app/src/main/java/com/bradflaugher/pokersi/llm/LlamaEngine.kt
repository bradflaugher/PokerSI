package com.bradflaugher.pokersi.llm

import android.annotation.SuppressLint
import android.app.ActivityManager
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.debug.AiLogKind
import java.io.File
import java.util.Locale
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
 * Whether a model fits in this phone's memory. llama.cpp maps the weights,
 * but every token touches all of them, so in practice they must stay
 * resident, plus the KV cache and compute buffers. Loading something far
 * bigger than RAM gets the app killed by the low-memory killer instead of
 * failing cleanly, so the engine refuses it up front.
 */
object MemoryCheck {
    /** Rough RAM a loaded model needs: weights, a 4K-token KV cache and compute buffers. */
    fun estimateBytes(modelBytes: Long): Long = modelBytes + modelBytes / 5 + (256L shl 20)

    /** Why [modelBytes] can't be loaded on a phone with [totalMem] RAM, [availMem] of it free; null when it fits. */
    fun problem(name: String, modelBytes: Long, totalMem: Long, availMem: Long): String? {
        if (modelBytes <= 0 || totalMem <= 0) return null
        val need = estimateBytes(modelBytes)
        return when {
            need > totalMem * 0.7 ->
                "$name is too big for this phone: it needs about ${gb(need)} of memory and the phone has ${gb(totalMem)}. Try a smaller model."
            need > availMem + totalMem / 4 ->
                "Not enough free memory for $name right now (needs about ${gb(need)}, ${gb(availMem)} free). Close some apps and try again."
            else -> null
        }
    }

    private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
}

/**
 * The app's one llama.cpp instance: owns at most one loaded model and runs
 * every native call on a single dedicated thread, so calls never overlap
 * (a load or unload waits for a generation in flight, which it cancels
 * first). Use [get] for the shared instance. The last loaded model is
 * remembered and loaded again on the next start, off the main thread.
 */
class LlamaEngine private constructor(context: Context) : LlmBackend {
    private val app = context.applicationContext
    private val prefs by lazy { app.getSharedPreferences("llama_engine", Context.MODE_PRIVATE) }

    // Big stack: llama.cpp's Jinja template renderer is recursive.
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "llama", 16L shl 20).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // NoModel until the library check on the llama thread says otherwise (loading
    // the native libraries is too slow for the main thread).
    private val _state = MutableStateFlow<EngineState>(EngineState.NoModel)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Sets [state] and notes the change in the debug console. */
    private fun setState(next: EngineState) {
        _state.value = next
        AiLog.add(if (next is EngineState.Failed) AiLogKind.ERROR else AiLogKind.ENGINE, ENGINE, {
            when (next) {
                EngineState.Unavailable -> "native library not built: House Bot only"
                EngineState.NoModel -> "no model loaded"
                is EngineState.Loading -> "loading ${next.name}"
                is EngineState.Ready -> "ready: ${next.name}"
                is EngineState.Failed -> "load failed: ${next.message}"
            }
        }, { (next as? EngineState.Ready)?.description.orEmpty() })
    }

    /**
     * Native handle. Written only on [dispatcher], and only under [handleLock]
     * so that [cancelGeneration] (any thread) never signals a freed engine.
     */
    @Volatile private var handle = 0L
    private val handleLock = Any()
    private var descriptor: ParcelFileDescriptor? = null

    /** True once the native library is known to be loaded. Read on [dispatcher]. */
    private var available = false

    override val isReady: Boolean get() = state.value is EngineState.Ready

    /** Threads for inference: the big cores, roughly. */
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    init {
        scope.launch(dispatcher) {
            available = LlamaNative.available
            if (!available) {
                setState(EngineState.Unavailable)
                return@launch
            }
            LlamaNative.init(app.applicationInfo.nativeLibraryDir)
            val crashed = prefs.getString(KEY_LOADING, null)
            if (crashed != null) {
                // The last load never finished: the process died in it (out of memory,
                // or a file llama.cpp chokes on). Don't crash again on every start.
                forget()
                setState(EngineState.Failed(
                    "$crashed didn't finish loading last time (the phone may have run out of memory), so it wasn't loaded again.",
                ))
                return@launch
            }
            val remembered = rememberedModel() ?: return@launch
            if (!loadOnThread(remembered)) forget()
        }
    }

    /**
     * Loads [model] (unloading any current one) and remembers it for the next
     * start. Returns false, with [state] set to [EngineState.Failed], when it
     * can't be loaded.
     */
    suspend fun load(model: LocalModel): Boolean {
        if (model.location == null) return false
        cancelGeneration()
        return withContext(dispatcher) { loadOnThread(model) }
    }

    /** On [dispatcher] only. */
    private fun loadOnThread(model: LocalModel): Boolean {
        if (!available) return false
        val location = model.location ?: return false
        freeCurrent()
        setState(EngineState.Loading(model.id, model.name))
        val (path, size) = try {
            when (location) {
                is ModelLocation.FilePath -> {
                    if (!location.file.isFile) error("the file is gone")
                    location.file.path to location.file.length()
                }
                is ModelLocation.Document -> {
                    val pfd = app.contentResolver.openFileDescriptor(location.uri, "r") ?: error("no access")
                    descriptor = pfd
                    "fd:${pfd.fd}" to pfd.statSize
                }
            }
        } catch (e: Exception) {
            // Includes SecurityException when a picked folder's permission was revoked.
            Log.w(TAG, "open failed", e)
            closeDescriptor()
            setState(EngineState.Failed("Can't open ${model.name}: ${e.message}"))
            return false
        }
        val memory = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        MemoryCheck.problem(model.name, size, memory.totalMem, memory.availMem)?.let { why ->
            closeDescriptor()
            setState(EngineState.Failed(why))
            return false
        }
        // Marks the load in progress until it returns (see init).
        prefs.edit(commit = true) { putString(KEY_LOADING, model.name) }
        val h = try {
            LlamaNative.loadModel(path, threads, CONTEXT_SIZE)
        } finally {
            prefs.edit(commit = true) { remove(KEY_LOADING) }
        }
        return if (h == 0L) {
            closeDescriptor()
            setState(EngineState.Failed("${model.name} didn't load. The file may be damaged or too big for this phone."))
            false
        } else {
            synchronized(handleLock) { handle = h }
            remember(model)
            setState(EngineState.Ready(model.id, model.name, LlamaNative.describe(h)))
            true
        }
    }

    /** Unloads the model and forgets it, so it isn't loaded on the next start. */
    suspend fun unload() {
        cancelGeneration()
        withContext(dispatcher) {
            freeCurrent()
            forget()
            if (available) setState(EngineState.NoModel)
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
    override suspend fun generate(
        messages: List<ChatMessage>,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        seed: Int,
        timeoutMs: Long,
        onText: (String) -> Boolean,
    ): String = withTimeout(timeoutMs) {
        coroutineScope {
            val started = AtomicBoolean(false)
            val finished = AtomicBoolean(false)
            // Stops the native loop (even mid-prompt) when we are cancelled or time out.
            // Only once our own call is on the thread: never another caller's generation.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    if (started.get() && !finished.get()) cancelGeneration()
                }
            }
            // The native call runs as its own job, so a timeout or cancellation returns
            // to the caller at once instead of waiting for the native call to unwind
            // (the watcher's cancel() stops it shortly after).
            val work = scope.async(dispatcher) {
                val h = handle.takeIf { it != 0L } ?: throw NoModelException()
                val job = currentCoroutineContext()
                started.set(true)
                try {
                    // Cancelled while queued behind another call: don't start at all.
                    job.ensureActive()
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
                        // Checked every token: a cancel that raced the native flag reset still stops it.
                        job.isActive && onText(text.toString())
                    }
                } finally {
                    finished.set(true)
                }
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                work.cancel()
                if (started.get() && !finished.get()) cancelGeneration()
                throw e
            } finally {
                watcher.cancel()
            }
        }
    }

    private fun cancelGeneration() {
        synchronized(handleLock) {
            handle.takeIf { it != 0L }?.let {
                AiLog.note(AiLogKind.ENGINE, ENGINE, "cancel signalled")
                LlamaNative.cancel(it)
            }
        }
    }

    /** On [dispatcher] only. */
    private fun freeCurrent() {
        val h = synchronized(handleLock) { handle.also { handle = 0L } }
        if (h != 0L) LlamaNative.freeModel(h)
        closeDescriptor()
    }

    private fun closeDescriptor() {
        try {
            descriptor?.close()
        } catch (_: Exception) {
        }
        descriptor = null
    }

    private fun forget() = prefs.edit { clear() }

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
        val location = prefs.getString(KEY_PATH, null)?.let { ModelLocation.FilePath(File(it)) }
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
        private const val ENGINE = "ENGINE"
        const val CONTEXT_SIZE = 4096
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_FILE = "file"
        private const val KEY_PATH = "path"
        private const val KEY_URI = "uri"
        private const val KEY_LOADING = "loading"

        // Holds only the application context.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: LlamaEngine? = null

        /** The app-wide engine. */
        fun get(context: Context): LlamaEngine =
            instance ?: synchronized(this) { instance ?: LlamaEngine(context).also { instance = it } }
    }
}
