package com.gree1d.reappzuku

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gree1d.reappzuku.AppConstants.STATS_HISTORY_DURATION_MS
import com.gree1d.reappzuku.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Keep
class AppzukuWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadData(context) }
        provideContent {
            WidgetContent(data)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xBB17181C))
                .padding(12.dp)
        ) {
            androidx.glance.appwidget.LinearProgressIndicator(
                progress = data.ramProgress,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = ColorProvider(Color(0xFF4FC3F7)),
                backgroundColor = ColorProvider(Color(0x33FFFFFF))
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = data.ramLabel,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(Color(0xCCFFFFFF)),
                    fontSize = 10.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatCell(
                    value = data.kills,
                    label = "kills 12h",
                    modifier = GlanceModifier.defaultWeight()
                )

                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color(0x33FFFFFF))
                ) {
                }

                StatCell(
                    value = data.freed,
                    label = "freed",
                    modifier = GlanceModifier.defaultWeight()
                )

                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color(0x33FFFFFF))
                ) {
                }

                StatCell(
                    value = data.lastKill,
                    label = "last kill",
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    @Composable
    private fun StatCell(value: String, label: String, modifier: GlanceModifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(Color(0x88FFFFFF)),
                    fontSize = 9.sp
                )
            )
        }
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(AppzukuWidget::class.java)
            ids.forEach { AppzukuWidget().update(context, it) }
        }

        @JvmStatic
        fun updateAllWidgetsFromJava(context: Context) {
            GlobalScope.launch {
                updateAllWidgets(context)
            }
        }

        private fun loadData(context: Context): WidgetData {
            var totalRamMb = 0L
            var usedRamMb = 0L
            try {
                RandomAccessFile("/proc/meminfo", "r").use { reader ->
                    val totalKb = reader.readLine().replace(Regex("\\D+"), "").toLong()
                    reader.readLine()
                    val availKb = reader.readLine().replace(Regex("\\D+"), "").toLong()
                    totalRamMb = totalKb / 1024
                    usedRamMb = (totalKb - availKb) / 1024
                }
            } catch (_: Exception) {}

            val ramProgress = if (totalRamMb > 0) (usedRamMb.toFloat() / totalRamMb) else 0f
            val ramLabel = if (totalRamMb > 0) "$usedRamMb / $totalRamMb MB" else "RAM: —"

            var totalKills = 0
            var totalRecoveredKb = 0L
            var lastKillTime = 0L
            try {
                val dao = AppDatabase.getInstance(context).appStatsDao()
                val since = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS
                val stats = dao.getAllStatsSince(since)
                for (s in stats) {
                    totalKills += s.killCount
                    totalRecoveredKb += s.totalRecoveredKb
                    if (s.lastKillTime > lastKillTime) lastKillTime = s.lastKillTime
                }
            } catch (_: Exception) {}

            val lastKillStr = if (lastKillTime > 0) {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastKillTime))
            } else "—"

            return WidgetData(
                ramProgress = ramProgress,
                ramLabel = ramLabel,
                kills = "$totalKills",
                freed = formatSize(totalRecoveredKb),
                lastKill = lastKillStr
            )
        }

        private fun formatSize(kb: Long): String {
            if (kb <= 0) return "0 MB"
            if (kb < 1024) return "$kb KB"
            if (kb < 1024 * 1024) return String.format(Locale.US, "%.1f MB", kb / 1024f)
            return String.format(Locale.US, "%.2f GB", kb / (1024f * 1024f))
        }
    }

    data class WidgetData(
        val ramProgress: Float,
        val ramLabel: String,
        val kills: String,
        val freed: String,
        val lastKill: String
    )
}

@Keep
class AppzukuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AppzukuWidget()
}
