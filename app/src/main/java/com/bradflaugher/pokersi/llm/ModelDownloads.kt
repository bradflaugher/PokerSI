package com.bradflaugher.pokersi.llm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** A model download in flight (or stuck). */
data class DownloadProgress(
    val state: State,
    val bytes: Long,
    /** 0 when not known yet. */
    val total: Long,
    val bytesPerSecond: Long = 0,
    /** Why it failed, for [State.FAILED]. */
    val error: String? = null,
) {
    enum class State {
        /** Waiting to start, for a network connection, or to retry. */
        QUEUED,
        RUNNING,
        FAILED,
    }

    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Enqueues, cancels and observes model downloads ([ModelDownloadWorker]),
 * keyed by catalog id. Each model is one unique WorkManager job, so tapping
 * download twice doesn't download twice.
 */
class ModelDownloads(context: Context) {
    private val app = context.applicationContext
    private val workManager = WorkManager.getInstance(app)

    fun enqueue(model: CatalogModel) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_ID to model.id,
                    ModelDownloadWorker.KEY_NAME to model.name,
                    ModelDownloadWorker.KEY_URL to model.url,
                    ModelDownloadWorker.KEY_FILE to model.fileName,
                    ModelDownloadWorker.KEY_SIZE to model.sizeBytes,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(TAG_ID_PREFIX + model.id)
            .build()
        // Finished attempts (an old failure) would otherwise linger next to this one and
        // resurface once it succeeds. WorkManager runs these two operations in order.
        workManager.pruneWork()
        workManager.enqueueUniqueWork(workName(model.id), ExistingWorkPolicy.KEEP, request)
    }

    /** Stops a download and throws away the partial file. */
    fun cancel(modelId: String) {
        workManager.cancelUniqueWork(workName(modelId))
        ModelCatalog.byId(modelId)?.let { ModelStore.partFile(app, it.fileName).delete() }
    }

    /** Downloads that are queued, running or failed, by catalog id. */
    val progress: Flow<Map<String, DownloadProgress>> =
        workManager.getWorkInfosByTagFlow(TAG).map { infos ->
            // An old failed attempt may linger next to a fresh one: any other attempt
            // (queued, running, or finished successfully) supersedes a failure.
            infos.groupBy { idOf(it) }.mapNotNull { (id, attempts) ->
                if (id == null) return@mapNotNull null
                val current = attempts.firstOrNull { it.state != WorkInfo.State.FAILED } ?: attempts.first()
                toProgress(current)?.let { id to it }
            }.toMap()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()

    fun progress(modelId: String): Flow<DownloadProgress?> = progress.map { it[modelId] }.distinctUntilChanged()

    /** Clears a failed download from [progress] (and its partial file). */
    fun dismiss(modelId: String) {
        cancel(modelId)
        workManager.pruneWork()
    }

    private fun idOf(info: WorkInfo): String? =
        info.tags.firstOrNull { it.startsWith(TAG_ID_PREFIX) }?.removePrefix(TAG_ID_PREFIX)

    private fun toProgress(info: WorkInfo): DownloadProgress? {
        val id = idOf(info) ?: return null
        val catalog = ModelCatalog.byId(id)
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadProgress(
                DownloadProgress.State.QUEUED,
                catalog?.let { ModelStore.partFile(app, it.fileName).length() } ?: 0,
                catalog?.sizeBytes ?: 0,
            )
            WorkInfo.State.RUNNING -> DownloadProgress(
                DownloadProgress.State.RUNNING,
                info.progress.getLong(ModelDownloadWorker.KEY_BYTES, 0),
                info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, catalog?.sizeBytes ?: 0),
                info.progress.getLong(ModelDownloadWorker.KEY_SPEED, 0),
            )
            WorkInfo.State.FAILED -> DownloadProgress(
                DownloadProgress.State.FAILED,
                catalog?.let { ModelStore.partFile(app, it.fileName).length() } ?: 0,
                catalog?.sizeBytes ?: 0,
                error = info.outputData.getString(ModelDownloadWorker.KEY_ERROR),
            )
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
        }
    }

    private companion object {
        const val TAG = "model-download"
        const val TAG_ID_PREFIX = "model-download:"

        fun workName(id: String) = "download-$id"
    }
}
