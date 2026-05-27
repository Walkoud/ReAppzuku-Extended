package com.gree1d.reappzuku.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R

private val BadgeSystemText     = Color(0xFF1A5276)
private val BadgeSystemBg       = Color(0xFFD6EAF8)
private val BadgePersistentText = Color(0xFF6C3483)
private val BadgePersistentBg   = Color(0xFFF5EEF8)

private const val ALPHA_PROTECTED   = 0.4f
private const val ALPHA_WHITELISTED = 0.85f
private const val ALPHA_NORMAL      = 1.0f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppModel,
    selectionMode: Boolean,
    onKillApp: () -> Unit,
    onAppClick: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = when {
        app.isProtected   -> ALPHA_PROTECTED
        app.isWhitelisted -> ALPHA_WHITELISTED
        else              -> ALPHA_NORMAL
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            if (!app.isProtected && !app.isWhitelisted) onAppClick()
                        } else {
                            onOverflowClick()
                        }
                    },
                    onLongClick = {
                        if (!selectionMode && !app.isProtected && !app.isWhitelisted) onAppClick()
                    }
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Иконка ────────────────────────────────────────────────────
            val drawable = app.appIcon
            if (drawable != null) {
                val bitmap = remember(drawable) { drawable.toBitmap(144, 144) }
                Image(
                    painter            = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = app.appName,
                    modifier           = Modifier.size(48.dp).alpha(contentAlpha),
                )
            } else {
                Box(
                    modifier         = Modifier
                        .size(48.dp)
                        .alpha(contentAlpha)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = (app.appName?.firstOrNull() ?: '?').uppercaseChar().toString(),
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // ── Текст ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .alpha(contentAlpha),
            ) {
                // Строка 1: имя + иконки
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text     = app.appName ?: app.packageName,
                        fontSize = 16.sp,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.isProtected) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_protected),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.padding(start = 4.dp).size(16.dp),
                        )
                    }
                    if (app.isWhitelisted) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_whitelist),
                            contentDescription = null,
                            tint               = Color(0xFF4CAF50),
                            modifier           = Modifier.padding(start = 8.dp).size(16.dp),
                        )
                    }
                }

                // Строка 2: package
                Text(
                    text     = app.packageName,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Строка 3: ОЗУ + CPU + badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ramText = app.appRam?.takeIf { it.isNotEmpty() }
                    if (ramText != null) {
                        Text(
                            text     = stringResource(R.string.app_ram_label, ramText),
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val cpuText = app.cpuUsage?.takeIf { it.isNotEmpty() }
                    if (cpuText != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text     = cpuText,
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // persistent приоритетнее system — точно как в адаптере
                    when {
                        app.isPersistentApp ->
                            Badge("Persistent", BadgePersistentText, BadgePersistentBg)
                        app.isSystemApp ->
                            Badge("System", BadgeSystemText, BadgeSystemBg)
                    }
                }
            }

            // ── Кнопка действия ───────────────────────────────────────────
            when {
                app.isProtected || app.isWhitelisted -> {
                    // скрыта, но место занимаем чтобы текст не растягивался
                    Spacer(Modifier.size(48.dp))
                }
                selectionMode -> {
                    IconButton(onClick = onAppClick, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(
                                if (app.isSelected) R.drawable.ic_checkbox_checked
                                else                R.drawable.ic_checkbox_unchecked
                            ),
                            contentDescription = null,
                            // красный чекбокс для выбранных
                            tint = if (app.isSelected) MaterialTheme.colorScheme.error
                                   else                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                else -> {
                    IconButton(onClick = onKillApp, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_force_stop),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, textColor: Color, bgColor: Color) {
    Spacer(Modifier.width(6.dp))
    Text(
        text       = text,
        fontSize   = 10.sp,
        fontWeight = FontWeight.Bold,
        color      = textColor,
        modifier   = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
