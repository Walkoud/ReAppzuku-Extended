package com.gree1d.reappzuku

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Kotlin replacement for RamMonitor that works without Views.
 * Calls [onUpdate] with (usedFraction 0..1, label String) on the main thread.
 */
class RamMonitorCompose(
    private val context: Context,
    private val onUpdate: (fraction: Float, label: String) -> Unit,
) {
    private val handler  = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var isMonitoring = false
    private var monitorRunnable: Runnable? = null

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        monitorRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                executor.execute {
                    val info = readRamUsage()
                    handler.post {
                        if (!isMonitoring) return@post
                        if (info != null && info.totalMb > 0) {
                            val fraction = (info.usedMb.toFloat() / info.totalMb).coerceIn(0f, 1f)
                            val label = context.getString(R.string.ram_usage, info.usedMb, info.totalMb)
                            onUpdate(fraction, label)
                        } else {
                            onUpdate(0f, context.getString(R.string.ram_usage_unavailable))
                        }
                    }
                }
                if (isMonitoring) {
                    handler.postDelayed(this, UPDATE_INTERVAL_MS)
                }
            }
        }
        handler.post(monitorRunnable!!)
    }

    fun stopMonitoring() {
        isMonitoring = false
        monitorRunnable?.let { handler.removeCallbacks(it) }
        monitorRunnable = null
    }

    private data class RamInfo(val usedMb: Long, val totalMb: Long)

    private fun readRamUsage(): RamInfo? {
        return try {
            var memTotal = 0L
            var memAvailable = 0L
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                var linesRead = 0
                while (linesRead < 10) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("MemTotal:")     -> memTotal     = parseKb(line)
                        line.startsWith("MemAvailable:") -> memAvailable = parseKb(line)
                    }
                    linesRead++
                    if (memTotal > 0 && memAvailable > 0) break
                }
            }
            if (memTotal > 0) RamInfo((memTotal - memAvailable) / 1024, memTotal / 1024)
            else null
        } catch (e: IOException) {
            Log.w("RamMonitorCompose", "Failed to read RAM", e)
            null
        }
    }

    private fun parseKb(line: String): Long {
        // "MemTotal:       8051352 kB"
        val parts = line.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 3000L
    }
}
