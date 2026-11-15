package metrovoice.app

import metrovoice.core.trace.TraceCodec
import metrovoice.core.trace.TraceRecord
import java.io.BufferedWriter
import java.io.File

/** Appends JSON Lines to a trip file. Called only from the trip thread. */
class TraceWriter(val file: File) {
    private val out: BufferedWriter = file.bufferedWriter(bufferSize = 1 shl 16)
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
