package com.gree1d.reappzuku.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R

// Цвета бейджей как в оригинальном XML
private val BadgeSystemText       = Color(0xFF1A5276)
private val BadgeSystemBg         = Color(0xFFD6EAF8)
private val BadgePersistentText   = Color(0xFF6C3483)
private val BadgePersistentBg     = Color(0xFFF5EEF8)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppModel,
    onKill: () -> Unit,
    onToggleWhitelist: () -> Unit,
    onClick: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Фон строки как в list_item_background — выделение при selection
    val bgColor = if (app.isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else
        Color.Transparent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)   // android:layout_marginBottom="2dp"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)         // android:layout_height="60dp"
                .background(bgColor)
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .padding(horizontal = 8.dp), // paddingStart/End="8dp"
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── App icon 48×48 ────────────────────────────────────────────
            AppIcon(app = app)

            Spacer(Modifier.width(8.dp))  // layout_marginEnd="8dp"

            // ── Text column (weight=1) ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),  // layout_marginEnd="8dp"
                verticalArrangement = Arrangement.Center,
            ) {
                // Строка 1: имя + иконки protected/whitelist
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text      = app.appName ?: app.packageName,
                        fontSize  = 16.sp,
                        color     = MaterialTheme.colorScheme.onSurface,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                        modifier  = Modifier.weight(1f, fill = false),
                    )
                    // protected_icon
                    if (app.isProtected) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_protected),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                        )
                    }
                    // whitelist_icon
                    if (app.isWhitelisted) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_whitelist),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp),
                        )
                    }
                }

                // Строка 2: package name
                Text(
                    text     = app.packageName,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Строка 3: RAM + CPU + badge_system + badge_persistent
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // RAM
                    val ramText = app.appRam?.takeIf { it.isNotEmpty() }
                    if (ramText != null) {
                        Text(
                            text     = ramText,
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    // CPU
                    val cpuText = app.cpuUsage?.takeIf { it.isNotEmpty() }
                    if (cpuText != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text     = cpuText,
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    // badge_system
                    if (app.isSystemApp) {
                        Spacer(Modifier.width(6.dp))
                        Badge(text = "System", textColor = BadgeSystemText, bgColor = BadgeSystemBg)
                    }
                    // badge_persistent
                    if (app.isPersistentApp) {
                        Spacer(Modifier.width(6.dp))
                        Badge(text = "Persistent", textColor = BadgePersistentText, bgColor = BadgePersistentBg)
                    }
                }
            }

            // ── Overflow button 40×48 ─────────────────────────────────────
            IconButton(
                onClick  = onOverflow,
                modifier = Modifier.size(width = 40.dp, height = 48.dp),
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_more_vert),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(24.dp),
                )
            }

            // ── Kill / action button 48×48 (hidden for protected/whitelisted) ──
            if (!app.isProtected && !app.isWhitelisted) {
                IconButton(
                    onClick  = onKill,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_force_stop),
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(28.dp),
                    )
                }
            } else {
                // Держим размер чтобы выравнивание не прыгало
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

// ── App icon ──────────────────────────────────────────────────────────────────

@Composable
private fun AppIcon(app: AppModel) {
    val drawable = app.appIcon
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap(144, 144) }
        Image(
            painter            = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = app.appName,
            modifier           = Modifier.size(48.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
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
}

// ── Badge chip (System / Persistent) ─────────────────────────────────────────

@Composable
private fun Badge(text: String, textColor: Color, bgColor: Color) {
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
