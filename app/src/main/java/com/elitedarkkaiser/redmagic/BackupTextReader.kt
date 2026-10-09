package com.elitedarkkaiser.redmagic

import java.io.Reader

internal object BackupTextReader {
    const val MAX_CHARS = 5_000_000
    fun read(reader: Reader, limit: Int = MAX_CHARS): String {
        val output = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer, 0, minOf(buffer.size, limit - output.length + 1))
            if (count < 0) return output.toString()
            require(output.length + count <= limit) { "Backup exceeds the size limit" }
            output.append(buffer, 0, count)
        }
    }
}
