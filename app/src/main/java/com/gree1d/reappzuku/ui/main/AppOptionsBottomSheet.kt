package com.gree1d.reappzuku.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R

data class AppOptionsState(
    val app: AppModel,
    val isWhitelisted: Boolean,
    val isBlacklisted: Boolean,
    val isHidden: Boolean,
    val supportsBackgroundRestriction: Boolean,
    val backgroundRestrictionDesired: Boolean,
    val backgroundRestrictionMenuTitle: String,
)

data class AppOptionsCallbacks(
    val onAppInfo: () -> Unit,
    val onAppTriggers: () -> Unit,
    val onUninstall: () -> Unit,
    val onToggleWhitelist: () -> Unit,
    val onToggleBlacklist: () -> Unit,
    val onToggleHidden: () -> Unit,
    val onToggleBackgroundRestriction: () -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOptionsBottomSheet(
    state: AppOptionsState,
    callbacks: AppOptionsCallbacks,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(app = state.app)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            if (state.app.isProtected) {
                CheckableItem(
                    title   = stringResource(R.string.menu_hidden),
                    checked = state.isHidden,
                    onClick = { callbacks.onToggleHidden(); callbacks.onDismiss() },
                )
            } else {
                PlainItem(
                    title   = stringResource(R.string.menu_app_info),
                    iconRes = android.R.drawable.ic_menu_info_details,
                    onClick = { callbacks.onAppInfo(); callbacks.onDismiss() },
                )
                PlainItem(
                    title   = stringResource(R.string.menu_app_triggers),
                    iconRes = android.R.drawable.ic_menu_search,
                    onClick = { callbacks.onAppTriggers(); callbacks.onDismiss() },
                )
                if (!state.app.isSystemApp) {
                    PlainItem(
                        title       = stringResource(R.string.menu_uninstall),
                        iconRes     = android.R.drawable.ic_menu_delete,
                        onClick     = { callbacks.onUninstall(); callbacks.onDismiss() },
                        destructive = true,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ExpandableGroup(title = stringResource(R.string.menu_add_to)) {
                    CheckableItem(
                        title   = stringResource(R.string.settings_mode_whitelist),
                        checked = state.isWhitelisted,
                        onClick = { callbacks.onToggleWhitelist(); callbacks.onDismiss() },
                    )
                    CheckableItem(
                        title   = stringResource(R.string.settings_mode_blacklist),
                        checked = state.isBlacklisted,
                        onClick = { callbacks.onToggleBlacklist(); callbacks.onDismiss() },
                    )
                    CheckableItem(
                        title   = stringResource(R.string.menu_hidden),
                        checked = state.isHidden,
                        onClick = { callbacks.onToggleHidden(); callbacks.onDismiss() },
                    )
                    if (state.supportsBackgroundRestriction) {
                        CheckableItem(
                            title   = state.backgroundRestrictionMenuTitle,
                            checked = state.backgroundRestrictionDesired,
                            onClick = { callbacks.onToggleBackgroundRestriction(); callbacks.onDismiss() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetHeader(app: AppModel) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val drawable = app.appIcon
        if (drawable != null) {
            val bitmap = remember(drawable) { drawable.toBitmap(96, 96) }
            Image(
                painter            = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = null,
                modifier           = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(
                text       = app.appName ?: app.packageName,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text     = app.packageName,
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlainItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error
                else             MaterialTheme.colorScheme.onSurface
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter            = painterResource(iconRes),
            contentDescription = null,
            modifier           = Modifier.size(20.dp),
            tint               = color,
        )
        Spacer(Modifier.width(14.dp))
        Text(text = title, fontSize = 15.sp, color = color)
    }
}


@Composable
private fun CheckableItem(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = title,
            fontSize = 15.sp,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked         = checked,
            onCheckedChange = { onClick() },
            colors          = CheckboxDefaults.colors(
                checkedColor   = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun ExpandableGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = title,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier.weight(1f),
            )
            Icon(
                imageVector        = if (expanded) Icons.Outlined.KeyboardArrowUp
                                     else          Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(tween(200)),
            exit    = shrinkVertically(tween(160)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(start = 16.dp),
            ) { content() }
        }
    }
}
