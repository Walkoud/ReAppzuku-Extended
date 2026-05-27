package com.gree1d.reappzuku.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R
import com.gree1d.reappzuku.ui.theme.LocalOnAccentColor

// ─────────────────────────────────────────────────────────────────────────────
// Nav destinations
// ─────────────────────────────────────────────────────────────────────────────

enum class NavDestination { MAIN, SETTINGS, STATISTICS }

// ─────────────────────────────────────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────────────────────────────────────

data class MainScreenState(
    val apps: List<AppModel>    = emptyList(),
    val isRefreshing: Boolean   = false,
    val ramPercent: Float       = 0f,
    val ramLabel: String        = "",
    val selectedCount: Int      = 0,
    val searchQuery: String     = "",
    val isSearchActive: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// MainScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainScreenState,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSortClick: () -> Unit,
    onScanClick: () -> Unit,
    onKillSelected: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onKillApp: (AppModel) -> Unit,
    onAppClick: (AppModel) -> Unit,
    onAppOverflow: (AppModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAccent     = LocalOnAccentColor.current
    val primary      = MaterialTheme.colorScheme.primary
    val hasSelection = state.selectedCount > 0
    val pullState    = rememberPullToRefreshState()
    val listState    = rememberLazyListState()
    val selectionMode = state.selectedCount > 0

    // Высота системного навбара для корректного отступа kill button
    val navBarInsets = WindowInsets.navigationBars

    Scaffold(
        modifier  = modifier,
        topBar    = {
            MainTopBar(
                title                = stringResource(R.string.app_name),
                isSearchActive       = state.isSearchActive,
                searchQuery          = state.searchQuery,
                hasSelection         = hasSelection,
                onSearchQueryChange  = onSearchQueryChange,
                onSearchActiveChange = onSearchActiveChange,
                onSelectAllToggle    = if (hasSelection) onDeselectAll else onSelectAll,
                onSortClick          = onSortClick,
                backgroundColor      = primary,
                contentColor         = onAccent,
            )
        },
        // Навбар или kill button внизу — никогда оба одновременно
        bottomBar = {
            AnimatedVisibility(
                visible = !hasSelection,
                enter   = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                exit    = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            ) {
                // windowInsetsPadding внутри NavigationBar обеспечивает
                // корректный отступ для жестовой навигации и кнопочной
                AppNavigationBar(current = NavDestination.MAIN, onNavigate = onNavigate)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Строка: кол-во приложений + кнопка Scan + RAM bar ─────
                // Порядок как в оригинале: счётчик → RAM → список
                AppCountRow(count = state.apps.size, onScanClick = onScanClick)
                RamBar(percent = state.ramPercent, label = state.ramLabel)

                // ── Список приложений ─────────────────────────────────────
                PullToRefreshBox(
                    state        = pullState,
                    isRefreshing = state.isRefreshing,
                    onRefresh    = onRefresh,
                    modifier     = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = state.apps,
                            key   = { _, app -> app.packageName }
                        ) { _, app ->
                            AppListItem(
                                app             = app,
                                selectionMode   = selectionMode,
                                onKillApp       = { onKillApp(app) },
                                onAppClick      = { onAppClick(app) },
                                onOverflowClick = { onAppOverflow(app) },
                            )
                        }
                    }
                }
            }

            // ── Kill button — поверх списка, прижат к низу ───────────────
            // Точно как в оригинале: высота 64dp + высота системного навбара
            AnimatedVisibility(
                visible  = hasSelection,
                enter    = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit     = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                KillButton(
                    count        = state.selectedCount,
                    onClick      = onKillSelected,
                    navBarInsets = navBarInsets,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TopAppBar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    isSearchActive: Boolean,
    searchQuery: String,
    hasSelection: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectAllToggle: () -> Unit,
    onSortClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
) {
    if (isSearchActive) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query            = searchQuery,
                    onQueryChange    = onSearchQueryChange,
                    onSearch         = {},
                    expanded         = true,
                    onExpandedChange = onSearchActiveChange,
                    placeholder      = { Text(stringResource(R.string.main_search_hint)) },
                )
            },
            expanded         = true,
            onExpandedChange = onSearchActiveChange,
            modifier         = Modifier.fillMaxWidth(),
            content          = {},
        )
    } else {
        TopAppBar(
            title = {
                Text(text = title, fontWeight = FontWeight.SemiBold, color = contentColor)
            },
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector        = Icons.Default.Search,
                        contentDescription = stringResource(R.string.main_search_hint),
                        tint               = contentColor,
                    )
                }
                IconButton(onClick = onSortClick) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_sort),
                        contentDescription = stringResource(R.string.sort_title),
                        tint               = contentColor,
                        modifier           = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onSelectAllToggle) {
                    Icon(
                        painter = painterResource(
                            if (hasSelection) R.drawable.ic_unselect_all
                            else              R.drawable.ic_select_all
                        ),
                        contentDescription = if (hasSelection)
                            stringResource(R.string.menu_deselect_all)
                        else
                            stringResource(R.string.menu_select_all),
                        tint     = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor         = backgroundColor,
                scrolledContainerColor = backgroundColor,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App count + Scan row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppCountRow(count: Int, onScanClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = stringResource(R.string.main_active_apps_count, count),
            fontSize = 18.sp,
            color    = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .clickable(onClick = onScanClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_scan),
                contentDescription = null,
                modifier           = Modifier.size(18.dp),
                tint               = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text     = stringResource(R.string.scan_button_label),
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RAM bar — воспроизводит progress_bar.xml:
// background #33FFFFFF, track #55FFFFFF, progress #5C6BC0 (Indigo)
// при наличии акцента — прогресс берётся из primary
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// RAM виджет — крупный акцентный блок
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RamBar(percent: Float, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier      = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape         = RoundedCornerShape(16.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "RAM",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Text(
                    text      = label,
                    fontSize  = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color     = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Прогресс-бар
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                )
                            ),
                            shape = RoundedCornerShape(3.dp),
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kill button
// Логика из оригинала: высота = 64dp + systemNavBar.bottom
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KillButton(
    count: Int,
    onClick: () -> Unit,
    navBarInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val onAccent      = LocalOnAccentColor.current
    val navBarPadding = navBarInsets.asPaddingValues().calculateBottomPadding()
    val label = if (count >= 2)
        "${stringResource(R.string.fab_kill_apps)} ($count)"
    else
        "${stringResource(R.string.fab_kill_app)} ($count)"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RectangleShape)
            .clickable(onClick = onClick)
            // padding снизу = высота системного навбара (жестовая / кнопочная)
            .padding(bottom = navBarPadding)
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom NavigationBar
// windowInsetsPadding внутри NavigationBar обеспечивает правильный padding
// для системных кнопок навигации
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppNavigationBar(
    current: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier    = modifier,
        tonalElevation = 0.dp,   // без тонального оттенка — как в оригинале
    ) {
        // windowInsetsPadding уже применяется внутри NavigationBar автоматически
        NavigationBarItem(
            selected = current == NavDestination.MAIN,
            onClick  = { onNavigate(NavDestination.MAIN) },
            icon     = { Icon(painterResource(R.drawable.ic_main), contentDescription = null) },
            label    = { Text(stringResource(R.string.nav_main)) },
            colors   = navItemColors(),
        )
        NavigationBarItem(
            selected = current == NavDestination.SETTINGS,
            onClick  = { onNavigate(NavDestination.SETTINGS) },
            icon     = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
            label    = { Text(stringResource(R.string.nav_settings)) },
            colors   = navItemColors(),
        )
        NavigationBarItem(
            selected = current == NavDestination.STATISTICS,
            onClick  = { onNavigate(NavDestination.STATISTICS) },
            icon     = { Icon(painterResource(R.drawable.ic_stats), contentDescription = null) },
            label    = { Text(stringResource(R.string.nav_statistics)) },
            colors   = navItemColors(),
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    // Активная иконка и текст — onSurface (белый/чёрный), не primary
    // Это поведение оригинала: selected иконка окрашивается в цвет текста навбара
    selectedIconColor   = MaterialTheme.colorScheme.onSurface,
    selectedTextColor   = MaterialTheme.colorScheme.onSurface,
    // Индикатор под иконкой — primaryContainer (цветной пузырь)
    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
