package com.gree1d.reappzuku

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.util.Locale

private val BgGreen  = Color(0xFF2E7D32)
private val BgBlue   = Color(0xFF1565C0)
private val BgAmber  = Color(0xFFF57F17)
private val BgRed    = Color(0xFFC62828)
private val TextMain = Color(0xFFFFFFFF)
private val TextSub  = Color(0xCCFFFFFF)

@Keep
class RamKillWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadRamData(context) }
        provideContent { WidgetContent(context, data) }
    }

    @Composable
    private fun WidgetContent(context: Context, data: RamData) {
        val bgColor = when {
            data.percent < 40 -> BgGreen
            data.percent < 80 -> BgBlue
            data.percent < 90 -> BgAmber
            else              -> BgRed
        }

        val killIntent = Intent(context, ShappkyService::class.java).apply {
            action = "WIDGET_KILL"
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(24.dp)
                .clickable(actionStartService(killIntent, isForegroundService = true)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${data.percent}%",
                    style = TextStyle(
                        color = ColorProvider(TextMain),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
                Text(
                    text = data.label,
                    style = TextStyle(
                        color = ColorProvider(TextSub),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(RamKillWidget::class.java)
            ids.forEach { RamKillWidget().update(context, it) }
        }

        @JvmStatic
        fun updateAllWidgetsFromJava(context: Context) {
            GlobalScope.launch { updateAllWidgets(context) }
        }

        private fun loadRamData(context: Context): RamData {
            var totalKb = 0L
            var availKb = 0L
            try {
                RandomAccessFile("/proc/meminfo", "r").use { reader ->
                    var line: String?
                    var linesRead = 0
                    while (linesRead < 3) {
                        line = reader.readLine() ?: break
                        when {
                            line.startsWith("MemTotal") -> totalKb = parseMemValue(line)
                            line.startsWith("MemAvailable") -> availKb = parseMemValue(line)
                        }
                        linesRead++
                    }
                }
            } catch (_: Exception) {}

            if (totalKb <= 0) return RamData(0, "—")

            val usedKb = totalKb - availKb
            val percent = (usedKb * 100 / totalKb).toInt()
            val label = formatGb(usedKb, context) + "/" + formatGb(totalKb, context) + " " + gbUnit(context)
            return RamData(percent, label)
        }

        private fun parseMemValue(line: String): Long {
            val parts = line.trim().split(Regex("\\s+"))
            return if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
        }

        private fun formatGb(kb: Long, context: Context): String {
            val gb = kb / (1024f * 1024f)
            return String.format(Locale.getDefault(), "%.1f", gb)
        }

        private fun gbUnit(context: Context): String {
            val locale = context.resources.configuration.locales[0]
            return if (locale.language == "ru") "ГБ" else "GB"
        }
    }

    data class RamData(val percent: Int, val label: String)
}

@Keep
class RamKillWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RamKillWidget()
}
