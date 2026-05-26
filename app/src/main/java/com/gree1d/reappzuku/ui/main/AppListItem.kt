package com.gree1d.reappzuku.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val bgColor by animateColorAsState(
        targetValue = when {
            app.isSelected   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            app.isProtected  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            app.isWhitelisted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            else              -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "itemBg",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            AppIcon(app = app)
            Spacer(Modifier.width(10.dp))

            // Name + RAM + tags
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = app.appName ?: app.packageName,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false),
                    )
                    if (app.isSystemApp)     TagChip(stringResource(R.string.filter_system))
                    if (app.isPersistentApp) TagChip(stringResource(R.string.filter_running))
                    if (app.isProtected)     TagChip(stringResource(R.string.settings_mode_whitelist), highlight = true)
                }
                ResourceUsageRow(app = app)
            }

            Spacer(Modifier.width(2.dp))

            // Action buttons
            if (!app.isProtected) {
                // Whitelist toggle
                IconButton(onClick = onToggleWhitelist, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_whitelist),
                        contentDescription = if (app.isWhitelisted)
                            stringResource(R.string.main_removed_from_whitelist)
                        else
                            stringResource(R.string.main_added_to_whitelist),
                        tint = if (app.isWhitelisted) MaterialTheme.colorScheme.primary
                               else                   MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Kill
                IconButton(onClick = onKill, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_force_stop),
                        contentDescription = stringResource(R.string.fab_kill_app),
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }

            // Overflow
            IconButton(onClick = onOverflow, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter            = painterResource(R.drawable.ic_more_vert),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun AppIcon(app: AppModel, modifier: Modifier = Modifier) {
    val drawable = app.appIcon
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap(96, 96) }
        Image(
            painter            = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = app.appName,
            modifier           = modifier.size(44.dp),
        )
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
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

@Composable
private fun ResourceUsageRow(app: AppModel, modifier: Modifier = Modifier) {
    // AppModel stores RAM as formatted string (appRam) and CPU as string (cpuUsage)
    val ramText = app.appRam?.takeIf { it.isNotEmpty() } ?: "—"
    val cpuText = app.cpuUsage?.takeIf { it.isNotEmpty() }

    Row(modifier = modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text  = ramText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cpuText != null) {
            Text(
                text     = "  ·  $cpuText",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Background restriction badge
        if (app.isBackgroundRestricted) {
            Text(
                text       = "  ·  ${stringResource(R.string.restriction_badge_hard)}",
                fontSize   = 10.sp,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TagChip(
    label: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg = if (highlight) MaterialTheme.colorScheme.errorContainer
             else           MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlight) MaterialTheme.colorScheme.onErrorContainer
             else           MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text       = label,
        fontSize   = 9.sp,
        fontWeight = FontWeight.Medium,
        color      = fg,
        modifier   = modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
