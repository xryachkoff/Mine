package metrovoice.app

import metrovoice.core.trace.TraceCodec
import metrovoice.core.trace.TraceRecord
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream

/**
 * Appends JSON Lines to a gzip-compressed trip file (~5–10x smaller than plain JSONL).
 * Sync-flushes every few seconds so a killed app still leaves a readable prefix.
 * Called only from the trip thread.
 */
class TraceWriter(val file: File) {
    private val gzip = GZIPOutputStream(FileOutputStream(file), 1 shl 16, true)
    private val out = BufferedWriter(OutputStreamWriter(gzip, Charsets.UTF_8), 1 shl 16)
    private var lastFlushMs = System.currentTimeMillis()

    fun write(record: TraceRecord) {
        out.write(TraceCodec.encode(record))
        out.newLine()
        val now = System.currentTimeMillis()
        if (now - lastFlushMs > 5_000) {
            out.flush()
            lastFlushMs = now
        }
    }

    fun close() {
        runCatching { out.flush(); out.close() }
    }
}
