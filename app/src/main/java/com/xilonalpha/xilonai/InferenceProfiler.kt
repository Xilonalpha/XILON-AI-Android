package com.xilonalpha.xilonai

import android.os.Debug
import android.os.SystemClock

/** Process-level memory snapshot. PSS includes shared native pages proportionally. */
object InferenceProfiler {
    data class Snapshot(val timestampMs: Long, val totalPssKiB: Int, val nativeHeapKiB: Int)

    fun snapshot(): Snapshot {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return Snapshot(
            SystemClock.elapsedRealtime(),
            info.totalPss,
            Debug.getNativeHeapAllocatedSize().div(1024L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    fun deltaMiB(before: Snapshot, after: Snapshot): Double =
        (after.totalPssKiB - before.totalPssKiB) / 1024.0
}
