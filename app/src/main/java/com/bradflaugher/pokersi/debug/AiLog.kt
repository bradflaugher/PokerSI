package com.bradflaugher.pokersi.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a debug console line is about; the console's filter chips pick by kind. */
enum class AiLogKind(val tag: String) {
    /** The messages sent to the model. */
    PROMPT("PROMPT"),
    GRAMMAR("GRAMMAR"),

    /** Raw model output, streaming in. */
    REPLY("REPLY"),

    /** What the reply became: the parsed and coerced decision, timing. */
    DECISION("DECIDE"),

    /** A fallback, a timeout, a failed load, a dropped line. */
    ERROR("ERROR"),

    /** Model load, unload, cancellation. */
    ENGINE("ENGINE"),

    /** Hand starts and actions at the table. */
    TABLE("TABLE"),
}

/** One debug console line. [body] is the long part (a prompt, a raw reply), shown on demand. */
data class AiLogEntry(
    val id: Long,
    val timeMs: Long,
    val kind: AiLogKind,
    /** Who: a persona's name, "ENGINE" or "TABLE". */
    val source: String,
    val title: String,
    val body: String = "",
)

/**
 * The debug console's ring buffer (the last [CAPACITY] entries), app-wide and
 * thread-safe. It records nothing unless [enabled] (SETTINGS > DEBUG CONSOLE),
 * and callers build long text only inside the lambdas, so with the console
 * off it costs one volatile read per call.
 */
object AiLog {
    const val CAPACITY = 400

    private val lock = Any()

    /**
     * Switching off clears the buffer. The flag changes under the buffer's lock, and
     * every write rechecks it there, so a write that raced the switch can't land after the clear.
     */
    @Volatile
    var enabled: Boolean = false
        set(value) = synchronized(lock) {
            field = value
            if (!value) clear()
        }

    private val buffer = ArrayDeque<AiLogEntry>()
    private var nextId = 1L
    private val _entries = MutableStateFlow<List<AiLogEntry>>(emptyList())
    val entries: StateFlow<List<AiLogEntry>> = _entries.asStateFlow()

    /** Clock for timestamps; tests and screenshots pin it. */
    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    /** Adds an entry and returns its id (for [update]), or 0 when the console is off. */
    inline fun add(kind: AiLogKind, source: String, title: () -> String, body: () -> String = { "" }): Long =
        if (enabled) put(kind, source, title(), body()) else 0L

    /** Adds an entry whose title is plain text. */
    fun note(kind: AiLogKind, source: String, title: String): Long = if (enabled) put(kind, source, title, "") else 0L

    @PublishedApi
    internal fun put(kind: AiLogKind, source: String, title: String, body: String): Long = synchronized(lock) {
        if (!enabled) return 0L
        val entry = AiLogEntry(nextId++, clock(), kind, source, title, body)
        buffer.addLast(entry)
        while (buffer.size > CAPACITY) buffer.removeFirst()
        _entries.value = buffer.toList()
        entry.id
    }

    /** Rewrites entry [id] (a reply still streaming); a no-op once it has scrolled out of the buffer. */
    fun update(id: Long, title: String? = null, body: String? = null) {
        if (!enabled || id == 0L) return
        synchronized(lock) {
            if (!enabled) return
            val i = buffer.indexOfLast { it.id == id }
            if (i < 0) return
            val old = buffer[i]
            buffer[i] = old.copy(title = title ?: old.title, body = body ?: old.body)
            _entries.value = buffer.toList()
        }
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
        _entries.value = emptyList()
    }

    /** Replaces everything with [entries] (screenshots). */
    fun replaceAll(entries: List<AiLogEntry>) = synchronized(lock) {
        buffer.clear()
        buffer.addAll(entries.takeLast(CAPACITY))
        nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1
        _entries.value = buffer.toList()
    }

    /** [entries] as plain text, for COPY. */
    fun plainText(entries: List<AiLogEntry>, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String = buildString {
        for (e in entries) {
            append(timeLabel(e.timeMs, zone)).append(' ').append(e.kind.tag).append(' ').append(e.source).append(": ").append(e.title).append('\n')
            if (e.body.isNotEmpty()) append(e.body.trimEnd().prependIndent("    ")).append('\n')
        }
    }

    /** "12:03:04.120", wall-clock time in [zone]. */
    fun timeLabel(ms: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val offset = zone.getOffset(ms)
        val t = Math.floorMod(ms + offset, 86_400_000L)
        val h = t / 3_600_000
        val m = t / 60_000 % 60
        val s = t / 1000 % 60
        val milli = t % 1000
        return "%02d:%02d:%02d.%03d".format(java.util.Locale.US, h, m, s, milli)
    }
}
