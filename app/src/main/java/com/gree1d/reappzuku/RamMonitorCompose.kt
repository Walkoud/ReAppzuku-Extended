package com.gree1d.reappzuku

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.Executors

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
                            val label    = context.getString(R.string.ram_usage, info.usedMb, info.totalMb)
                            onUpdate(fraction, label)
                        } else {
                            onUpdate(0f, context.getString(R.string.ram_usage_unavailable))
                        }
                    }
                }

                if (isMonitoring) {
                    handler.postDelayed(this, AppConstants.RAM_MONITOR_UPDATE_INTERVAL_MS.toLong())
                }
            }
        }

        handler.post(monitorRunnable!!)
    }

    fun stopMonitoring() {
        isMonitoring = false
        monitorRunnable?.let { handler.removeCallbacks(it) }
        monitorRunnable = null
        executor.shutdownNow()
    }


    private data class RamInfo(val usedMb: Long, val totalMb: Long)

    private fun readRamUsage(): RamInfo? {
        return try {
            RandomAccessFile("/proc/meminfo", "r").use { reader ->
                var memTotal     = 0L
                var memAvailable = 0L
                var line: String?
                var i = 0
                while (i < 3 && reader.readLine().also { line = it } != null) {
                    when {
                        line!!.startsWith("MemTotal")     -> memTotal     = parseMemValue(line!!)
                        line!!.startsWith("MemAvailable") -> memAvailable = parseMemValue(line!!)
                    }
                    i++
                }
                if (memTotal > 0) RamInfo((memTotal - memAvailable) / 1024, memTotal / 1024)
                else null
            }
        } catch (e: IOException) {
            Log.w("RamMonitorCompose", "Failed to read RAM usage", e)
            null
        } catch (e: NumberFormatException) {
            Log.w("RamMonitorCompose", "Failed to parse RAM value", e)
            null
        }
    }

    private fun parseMemValue(line: String): Long {
        val parts = line.trim().split("\\s+".toRegex())
        return if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
    }
}
