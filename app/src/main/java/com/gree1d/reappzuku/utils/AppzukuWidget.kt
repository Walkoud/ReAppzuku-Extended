package com.gree1d.reappzuku.utils;

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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
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
import com.gree1d.reappzuku.core.AppConstants.STATS_HISTORY_DURATION_MS
import com.gree1d.reappzuku.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.gree1d.reappzuku.R

private val BgSurface     = Color(0xFF1A1C24)
private val BgCard        = Color(0xFF22242E)
private val TextPrimary   = Color(0xFFE8EAF0)
private val TextSecondary = Color(0x80E8EAF0)
private val AccentBlue    = Color(0xFF82CAFF)
private val AccentGreen   = Color(0xFF81C784)
private val AccentAmber   = Color(0xFFFFCA28)
private val AccentRed     = Color(0xFFEF9A9A)

@Keep
class AppzukuWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadData(context) }
        provideContent { WidgetContent(data) }
    }

    @Composable
    private fun WidgetContent(data: WidgetData) {
        val ramColor = when {
            data.ramProgress < 0.40f -> AccentGreen
            data.ramProgress < 0.75f -> AccentBlue
            data.ramProgress < 0.90f -> AccentAmber
            else                     -> AccentRed
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BgSurface)
                .cornerRadius(20.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.labelRam,
                    style = TextStyle(
                        color = ColorProvider(TextSecondary),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(5.dp)
                        .cornerRadius(3.dp)
                ) {
                    LinearProgressIndicator(
                        progress = data.ramProgress,
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                        color = ColorProvider(ramColor),
                        backgroundColor = ColorProvider(Color(0x1AFFFFFF))
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = data.ramLabel,
                    style = TextStyle(
                        color = ColorProvider(ramColor),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(5.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCard(
                    value = data.kills,
                    label = data.labelKills,
                    accentColor = AccentBlue,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                StatCard(
                    value = data.freed,
                    label = data.labelFreed,
                    accentColor = AccentGreen,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                StatCard(
                    value = data.lastKill,
                    label = data.labelLastKill,
                    accentColor = AccentAmber,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    @Composable
    private fun StatCard(
        value: String,
        label: String,
        accentColor: Color,
        modifier: GlanceModifier
    ) {
        Column(
            modifier = modifier
                .background(BgCard)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(0.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(accentColor.copy(alpha = 0.85f)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
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
            GlobalScope.launch { updateAllWidgets(context) }
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
            val ramLabel = if (totalRamMb > 0) "$usedRamMb / $totalRamMb MB" else "—"

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

            val lastKillStr = if (lastKillTime > 0)
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastKillTime))
            else "—"

            return WidgetData(
                ramProgress = ramProgress,
                ramLabel = ramLabel,
                kills = "$totalKills",
                freed = formatSize(totalRecoveredKb),
                lastKill = lastKillStr,
                labelRam = context.getString(R.string.widget_ram),
                labelKills = context.getString(R.string.widget_kills),
                labelFreed = context.getString(R.string.widget_freed),
                labelLastKill = context.getString(R.string.widget_last_kill)
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
        val lastKill: String,
        val labelRam: String,
        val labelKills: String,
        val labelFreed: String,
        val labelLastKill: String
    )
}

@Keep
class AppzukuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AppzukuWidget()
}
