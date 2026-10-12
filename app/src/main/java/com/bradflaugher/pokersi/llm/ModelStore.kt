package com.bradflaugher.pokersi.llm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Where a model file lives. */
sealed interface ModelLocation {
    /** A file in app storage (downloads). */
    data class FilePath(val file: File) : ModelLocation

    /** A Storage Access Framework document the user picked (loaded through its file descriptor). */
    data class Document(val uri: Uri) : ModelLocation

    /** The id of a model file that is not in the catalog: where it lives. */
    val customId: String
        get() = when (this) {
            is FilePath -> file.path
            is Document -> uri.toString()
        }
}

/**
 * A model as the model screen shows it: a catalog entry (downloaded or
 * not, maybe downloading) or a GGUF the user brought along.
 */
data class LocalModel(
    /** Catalog id, or a path/URI for custom files. Stable across launches. */
    val id: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Null until the file is on the device. */
    val location: ModelLocation?,
    /** Null for the user's own files. */
    val catalog: CatalogModel?,
    /** Non-null while a download is queued, running or has failed. */
    val download: DownloadProgress? = null,
) {
    val isAvailable: Boolean get() = location != null
    val isCustom: Boolean get() = catalog == null

    /** Downloaded into app storage (so the app may delete it). */
    val isDownloaded: Boolean get() = location is ModelLocation.FilePath
}

/**
 * Knows every model on the device: downloads in app storage, GGUFs in
 * folders the user granted (ACTION_OPEN_DOCUMENT_TREE) and single files the
 * user imported (ACTION_OPEN_DOCUMENT). [models] merges them with the
 * catalog and live download progress. Use [get] for the shared instance.
 */
class ModelStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("model_store", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanLock = Mutex()

    val downloads = ModelDownloads(app)

    private class Found(val name: String, val size: Long, val location: ModelLocation)

    private val found = MutableStateFlow<List<Found>>(emptyList())

    private val _folders = MutableStateFlow(savedUris(KEY_FOLDERS))
    /** Folders the user granted, as tree URIs. */
    val folders: StateFlow<List<Uri>> = _folders.asStateFlow()

    private val _files = MutableStateFlow(savedUris(KEY_FILES))
    /** Single files the user imported. */
    val files: StateFlow<List<Uri>> = _files.asStateFlow()

    /** Catalog entries first (in catalog order), then the user's own files by name. */
    val models: StateFlow<List<LocalModel>> = combine(found, downloads.progress) { files, progress ->
        merge(files, progress)
    }.stateIn(scope, SharingStarted.Eagerly, merge(emptyList(), emptyMap()))

    init {
        refresh()
        // A finished (or cancelled) download changes what is on disk.
        scope.launch { downloads.progress.map { it.keys }.distinctUntilChanged().collect { refresh() } }
    }

    fun find(id: String): LocalModel? = models.value.firstOrNull { it.id == id }

    /** Rescans app storage and the user's folders and files. */
    fun refresh() {
        scope.launch {
            scanLock.withLock {
                found.value = withContext(Dispatchers.IO) { scan() }
            }
        }
    }

    /** Starts (or resumes) downloading a catalog model. */
    fun download(model: CatalogModel) = downloads.enqueue(model)

    fun cancelDownload(modelId: String) = downloads.cancel(modelId)

    /** Adds a folder from ACTION_OPEN_DOCUMENT_TREE and keeps access to it. */
    fun addFolder(treeUri: Uri) {
        persist(treeUri)
        _folders.value = (_folders.value + treeUri).distinct()
        save(KEY_FOLDERS, _folders.value)
        refresh()
    }

    fun removeFolder(treeUri: Uri) {
        release(treeUri)
        _folders.value = _folders.value - treeUri
        save(KEY_FOLDERS, _folders.value)
        refresh()
    }

    /** Adds a single GGUF from ACTION_OPEN_DOCUMENT and keeps access to it. */
    fun importFile(uri: Uri) {
        persist(uri)
        _files.value = (_files.value + uri).distinct()
        save(KEY_FILES, _files.value)
        refresh()
    }

    /**
     * Deletes a downloaded model (and any partial download), or forgets an
     * imported file. Files inside the user's folders are never deleted.
     */
    suspend fun delete(model: LocalModel) {
        model.catalog?.let { downloads.cancel(it.id) }
        when (val location = model.location) {
            is ModelLocation.FilePath -> withContext(Dispatchers.IO) {
                location.file.delete()
                File(location.file.path + ModelDownloadWorker.PART_SUFFIX).delete()
            }
            is ModelLocation.Document -> if (location.uri in _files.value) {
                release(location.uri)
                _files.value = _files.value - location.uri
                save(KEY_FILES, _files.value)
            }
            null -> model.catalog?.let { withContext(Dispatchers.IO) { partFile(app, it.fileName).delete() } }
        }
        refresh()
    }

    private fun merge(files: List<Found>, progress: Map<String, DownloadProgress>): List<LocalModel> {
        val byName = files.groupBy { it.name }
        val catalog = ModelCatalog.models.map { c ->
            // Prefer our own download, then a copy in one of the user's folders.
            val file = byName[c.fileName]?.minByOrNull { if (it.location is ModelLocation.FilePath) 0 else 1 }
            LocalModel(c.id, c.name, c.fileName, c.sizeBytes, file?.location, c, progress[c.id])
        }
        val catalogNames = ModelCatalog.models.map { it.fileName }.toSet()
        val custom = files.filter { it.name !in catalogNames }.map { f ->
            LocalModel(f.location.customId, f.name.removeSuffix(".gguf"), f.name, f.size, f.location, null)
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        return catalog + custom
    }

    private fun scan(): List<Found> {
        val out = mutableListOf<Found>()
        downloadDir(app).listFiles()?.filter { it.isFile && isModelFile(it.name) }?.forEach {
            out += Found(it.name, it.length(), ModelLocation.FilePath(it))
        }
        for (tree in _folders.value) {
            try {
                DocumentFile.fromTreeUri(app, tree)?.let { scanTree(it, out, depth = 0) }
            } catch (e: Exception) {
                Log.w(TAG, "can't read folder $tree", e)
            }
        }
        for (uri in _files.value) {
            try {
                val doc = DocumentFile.fromSingleUri(app, uri) ?: continue
                if (doc.exists()) out += Found(doc.name ?: uri.lastPathSegment.orEmpty(), doc.length(), ModelLocation.Document(uri))
            } catch (e: Exception) {
                Log.w(TAG, "can't read file $uri", e)
            }
        }
        return out
    }

    private fun scanTree(dir: DocumentFile, out: MutableList<Found>, depth: Int) {
        for (doc in dir.listFiles()) {
            val name = doc.name ?: continue
            when {
                doc.isDirectory && depth < MAX_DEPTH -> scanTree(doc, out, depth + 1)
                doc.isFile && isModelFile(name) -> out += Found(name, doc.length(), ModelLocation.Document(doc.uri))
            }
        }
    }

    private fun persist(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "no persistable permission for $uri", e)
        }
    }

    private fun release(uri: Uri) {
        try {
            app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    private fun savedUris(key: String): List<Uri> =
        prefs.getStringSet(key, emptySet()).orEmpty().sorted().map { it.toUri() }

    private fun save(key: String, uris: List<Uri>) =
        prefs.edit { putStringSet(key, uris.map { it.toString() }.toSet()) }

    companion object {
        private const val TAG = "ModelStore"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_FILES = "files"
        private const val MAX_DEPTH = 3

        // Holds only the application context.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: ModelStore? = null

        fun get(context: Context): ModelStore =
            instance ?: synchronized(this) { instance ?: ModelStore(context).also { instance = it } }

        /** Where downloads go: app-specific external storage (no permission needed), else internal. */
        fun downloadDir(context: Context): File =
            (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

        fun partFile(context: Context, fileName: String): File =
            File(downloadDir(context), fileName + ModelDownloadWorker.PART_SUFFIX)

        /** A loadable GGUF: not a vision projector, not a partial download. */
        fun isModelFile(name: String): Boolean =
            name.endsWith(".gguf", ignoreCase = true) && !name.contains("mmproj", ignoreCase = true)
    }
}
