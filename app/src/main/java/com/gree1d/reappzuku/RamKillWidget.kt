package com.gree1d.reappzuku

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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

@Keep
class RamKillWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadRamData(context) }
        provideContent { WidgetContent(data) }
    }

    @Composable
    private fun WidgetContent(data: RamData) {
        val context = LocalContext.current
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMode == Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isDark) Color.Black else Color.White
        val textColor = if (isDark) Color.White else Color.Black
        val subColor = if (isDark) Color(0xCCFFFFFF) else Color(0xCC000000)

        val killIntent = Intent(context, ShappkyService::class.java).apply {
            action = "WIDGET_KILL"
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(24.dp)
                .padding(4.dp)
                .clickable(actionStartService(killIntent, isForegroundService = true)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${data.percent}%",
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = data.label,
                style = TextStyle(
                    color = ColorProvider(subColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            )
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
                    var linesRead = 0
                    while (linesRead < 3) {
                        val line = reader.readLine() ?: break
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
            val unit = if (context.resources.configuration.locales[0].language == "ru") "ГБ" else "GB"
            val used = String.format(Locale.getDefault(), "%.1f", usedKb / (1024f * 1024f))
            val total = String.format(Locale.getDefault(), "%.1f", totalKb / (1024f * 1024f))
            return RamData(percent, "$used/$total $unit")
        }

        private fun parseMemValue(line: String): Long {
            val parts = line.trim().split(Regex("\\s+"))
            return if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
        }
    }

    data class RamData(val percent: Int, val label: String)
}

@Keep
class RamKillWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RamKillWidget()
}
