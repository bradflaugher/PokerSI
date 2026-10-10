package com.bradflaugher.pokersi.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bradflaugher.pokersi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one model into [ModelStore.downloadDir] as a foreground
 * (dataSync) job with a progress notification. Writes to `<file>.part`,
 * resumes it with an HTTP Range request after a retry or network loss,
 * and renames it into place only once complete.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val id = inputData.getString(KEY_ID).orEmpty()
    private val name = inputData.getString(KEY_NAME).orEmpty()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val notificationId = NOTIFICATION_BASE + id.hashCode().and(0xFFFF)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, inputData.getLong(KEY_SIZE, 0), 0)

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "could not go foreground", e)
        }
        val url = inputData.getString(KEY_URL) ?: return fail("Missing URL")
        val fileName = inputData.getString(KEY_FILE) ?: return fail("Missing file name")
        val expected = inputData.getLong(KEY_SIZE, 0)
        val dir = ModelStore.downloadDir(applicationContext)
        val target = File(dir, fileName)
        val part = File(dir, fileName + PART_SUFFIX)

        if (expected > 0 && freeBytes(dir) < expected - part.length() + SPACE_MARGIN) {
            return fail(applicationContext.getString(R.string.download_no_space))
        }
        return try {
            withContext(Dispatchers.IO) { download(url, part, expected) }
            if (isStopped) return Result.failure()
            if (!part.renameTo(target)) return fail("Could not save $fileName")
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "download of $id interrupted", e)
            // Keep the .part: the retry picks up where this left off.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else fail(e.message ?: "Network error")
        }
    }

    private suspend fun download(url: String, part: File, expected: Long) {
        var existing = part.length()
        if (expected > 0 && existing == expected) return
        if (expected > 0 && existing > expected) {
            part.delete()
            existing = 0
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false
                416 -> if (expected > 0 && existing >= expected) return else throw IOException("HTTP 416")
                else -> throw IOException("HTTP $code")
            }
            if (!append) existing = 0
            val length = connection.contentLengthLong
            val total = if (length > 0) existing + length else expected

            var bytes = existing
            var lastReport = 0L
            var windowStart = System.nanoTime()
            var windowBytes = 0L
            var speed = 0L
            connection.inputStream.use { input ->
                FileOutputStream(part, append).buffered(BUFFER).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (!isStopped) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        bytes += n
                        windowBytes += n
                        val now = System.nanoTime()
                        if (now - windowStart >= 1_000_000_000L) {
                            speed = windowBytes * 1_000_000_000L / (now - windowStart)
                            windowStart = now
                            windowBytes = 0
                        }
                        if (now - lastReport >= REPORT_INTERVAL_NS) {
                            lastReport = now
                            report(bytes, total, speed)
                        }
                    }
                }
            }
            if (!isStopped && total > 0 && part.length() != total) {
                throw IOException("Download ended early (${part.length()} of $total bytes)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun report(bytes: Long, total: Long, speed: Long) {
        setProgress(workDataOf(KEY_BYTES to bytes, KEY_TOTAL to total, KEY_SPEED to speed))
        try {
            setForeground(foregroundInfo(bytes, total, speed))
        } catch (e: Exception) {
            Log.w(TAG, "could not update the notification", e)
        }
    }

    /** Free space including cache the system would clear for us. */
    private fun freeBytes(dir: File): Long = try {
        val storage = applicationContext.getSystemService(StorageManager::class.java)
        storage.getAllocatableBytes(storage.getUuidForPath(dir))
    } catch (_: IOException) {
        dir.usableSpace
    }

    private fun fail(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    private fun foregroundInfo(bytes: Long, total: Long, speed: Long): ForegroundInfo {
        val context = applicationContext
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val percent = if (total > 0) (bytes * 100 / total).toInt() else 0
        val detail = if (total > 0) {
            context.getString(
                R.string.download_progress,
                Formatter.formatShortFileSize(context, bytes),
                Formatter.formatShortFileSize(context, total),
                Formatter.formatShortFileSize(context, speed),
            )
        } else {
            null
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_title, name))
            .setContentText(detail)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.download_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(getId()),
            )
            .build()
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val TAG = "ModelDownload"
        const val PART_SUFFIX = ".part"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_URL = "url"
        const val KEY_FILE = "file"
        const val KEY_SIZE = "size"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "model_downloads"
        private const val NOTIFICATION_BASE = 0x5000
        private const val BUFFER = 256 * 1024
        private const val REPORT_INTERVAL_NS = 500_000_000L
        private const val MAX_ATTEMPTS = 20
        private const val SPACE_MARGIN = 50L shl 20
    }
}
