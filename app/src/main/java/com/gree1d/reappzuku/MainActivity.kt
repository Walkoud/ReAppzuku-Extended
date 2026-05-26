package com.gree1d.reappzuku

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gree1d.reappzuku.AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR
import com.gree1d.reappzuku.AppConstants.ACCENT_ON_BLACK
import com.gree1d.reappzuku.AppConstants.ACCENT_SYSTEM
import com.gree1d.reappzuku.PreferenceKeys.KEY_ACCENT
import com.gree1d.reappzuku.PreferenceKeys.KEY_ACCENT_CUSTOM_COLOR
import com.gree1d.reappzuku.PreferenceKeys.KEY_ACCENT_ON_COLOR
import com.gree1d.reappzuku.PreferenceKeys.KEY_AMOLED
import com.gree1d.reappzuku.PreferenceKeys.KEY_THEME
import com.gree1d.reappzuku.ui.main.AppOptionsBottomSheet
import com.gree1d.reappzuku.ui.main.AppOptionsCallbacks
import com.gree1d.reappzuku.ui.main.AppOptionsState
import com.gree1d.reappzuku.ui.main.MainScreen
import com.gree1d.reappzuku.ui.main.MainScreenState
import com.gree1d.reappzuku.ui.main.MainViewModel
import com.gree1d.reappzuku.ui.main.NavDestination
import com.gree1d.reappzuku.ui.theme.AppTheme
import com.gree1d.reappzuku.ui.theme.AppThemeConfig
import rikka.shizuku.Shizuku
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val prefs by lazy {
        getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    }

    private var appliedThemeConfig = AppThemeConfig()

    private val ramMonitor by lazy {
        RamMonitorCompose(this) { percent, label ->
            viewModel.onRamUpdate(percent, label)
        }
    }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadBackgroundApps()
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        appliedThemeConfig = readThemeConfig()

        viewModel.shellManager.setShizukuPermissionListener(shizukuListener)
        viewModel.shellManager.checkShellPermissions()
        viewModel.loadBackgroundApps()

        setContent {
            val themeConfig = appliedThemeConfig

            AppTheme(config = themeConfig) {

                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                var sheetApp by remember { mutableStateOf<AppModel?>(null) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                val screenState = MainScreenState(
                    apps           = uiState.filteredApps,
                    isRefreshing   = uiState.isRefreshing,
                    ramPercent     = uiState.ramPercent,
                    ramLabel       = uiState.ramLabel,
                    selectedCount  = uiState.selectedCount,
                    searchQuery    = uiState.searchQuery,
                    isSearchActive = uiState.isSearchActive,
                )

                MainScreen(
                    state                = screenState,
                    onRefresh            = viewModel::loadBackgroundApps,
                    onSearchQueryChange  = viewModel::onSearchQueryChange,
                    onSearchActiveChange = viewModel::onSearchActiveChange,
                    onSelectAll          = viewModel::selectAll,
                    onDeselectAll        = viewModel::deselectAll,
                    onSortClick          = { showSortDialog() },
                    onScanClick          = { showSystemScanDialog() },
                    onKillSelected       = viewModel::killSelected,
                    onNavigate           = ::handleNavigation,
                    onKillApp            = viewModel::killApp,
                    onToggleWhitelist    = { app ->
                        val isNow = viewModel.toggleWhitelist(app)
                        val msg = if (isNow)
                            getString(R.string.main_added_to_whitelist)
                        else
                            getString(R.string.main_removed_from_whitelist)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    },
                    onAppClick           = viewModel::onAppClick,
                    onAppOverflow        = { app -> sheetApp = app },
                )

                sheetApp?.let { app ->
                    AppOptionsBottomSheet(
                        state = AppOptionsState(
                            app                              = app,
                            isWhitelisted                    = viewModel.appManager.getWhitelistedApps().contains(app.packageName),
                            isBlacklisted                    = viewModel.autoKillManager.getBlacklistedApps().contains(app.packageName),
                            isHidden                         = viewModel.appManager.getHiddenApps().contains(app.packageName),
                            supportsBackgroundRestriction    = viewModel.appManager.supportsBackgroundRestriction(),
                            backgroundRestrictionDesired     = app.isBackgroundRestrictionDesired,
                            backgroundRestrictionMenuTitle   = getBackgroundRestrictionTitle(app),
                        ),
                        callbacks = AppOptionsCallbacks(
                            onAppInfo    = { openAppInfo(app.packageName) },
                            onAppTriggers = { showAppTriggersDialog(app) },
                            onUninstall  = { showUninstallConfirmation(app) },
                            onToggleWhitelist = {
                                viewModel.toggleListMembership(app, "whitelist")
                            },
                            onToggleBlacklist = {
                                viewModel.toggleListMembership(app, "blacklist")
                            },
                            onToggleHidden = {
                                viewModel.toggleListMembership(app, "hidden")
                            },
                            onToggleBackgroundRestriction = {
                                handleBackgroundRestriction(app)
                            },
                            onDismiss = { sheetApp = null },
                        ),
                        sheetState = sheetState,
                    )
                }
            }
        }

        ramMonitor.startMonitoring()
    }

    override fun onResume() {
        super.onResume()

        val newConfig = readThemeConfig()
        if (newConfig != appliedThemeConfig) {
            recreate()
            return
        }

        viewModel.loadBackgroundApps()
        viewModel.startCpuMonitor()
        ensureServiceRunning()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopCpuMonitor()
    }

    override fun onDestroy() {
        super.onDestroy()
        ramMonitor.stopMonitoring()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }


    private fun handleNavigation(dest: NavDestination) {
        when (dest) {
            NavDestination.MAIN       -> { /* already here */ }
            NavDestination.SETTINGS   -> startActivity(Intent(this, SettingsActivity::class.java))
            NavDestination.STATISTICS -> startActivity(Intent(this, StatisticsActivity::class.java))
        }
    }



    private fun showSortDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort, null)
        val radioGroup         = dialogView.findViewById<android.widget.RadioGroup>(R.id.sort_radio_group)
        val checkboxSystem     = dialogView.findViewById<android.widget.CheckBox>(R.id.checkbox_show_system)
        val checkboxPersistent = dialogView.findViewById<android.widget.CheckBox>(R.id.checkbox_show_persistent)

        val state = viewModel.uiState.value

        val selectedRadioId = when (state.sortMode) {
            AppConstants.SORT_MODE_RAM_DESC  -> R.id.sort_ram_desc
            AppConstants.SORT_MODE_RAM_ASC   -> R.id.sort_ram_asc
            AppConstants.SORT_MODE_NAME_ASC  -> R.id.sort_name_asc
            AppConstants.SORT_MODE_NAME_DESC -> R.id.sort_name_desc
            AppConstants.SORT_MODE_CPU_DESC  -> R.id.sort_cpu_desc
            AppConstants.SORT_MODE_CPU_ASC   -> R.id.sort_cpu_asc
            else                             -> R.id.sort_default
        }
        radioGroup.check(selectedRadioId)
        checkboxSystem.isChecked     = state.showSystemApps
        checkboxPersistent.isChecked = state.showPersistentApps

        checkboxSystem.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked && !prefs.getBoolean("system_apps_warning_shown", false)) {
                btn.isChecked = false
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.settings_system_apps_warning_title))
                    .setMessage(getString(R.string.settings_system_apps_warning_message))
                    .setPositiveButton(getString(R.string.settings_system_apps_i_understand)) { _, _ ->
                        prefs.edit().putBoolean("system_apps_warning_shown", true).apply()
                        btn.isChecked = true
                    }
                    .setNegativeButton(getString(R.string.dialog_cancel), null)
                    .show()
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_apply)) { _, _ ->
                val newSort = when (radioGroup.checkedRadioButtonId) {
                    R.id.sort_ram_desc  -> AppConstants.SORT_MODE_RAM_DESC
                    R.id.sort_ram_asc   -> AppConstants.SORT_MODE_RAM_ASC
                    R.id.sort_name_asc  -> AppConstants.SORT_MODE_NAME_ASC
                    R.id.sort_name_desc -> AppConstants.SORT_MODE_NAME_DESC
                    R.id.sort_cpu_desc  -> AppConstants.SORT_MODE_CPU_DESC
                    R.id.sort_cpu_asc   -> AppConstants.SORT_MODE_CPU_ASC
                    else                -> AppConstants.SORT_MODE_DEFAULT
                }
                viewModel.applySortAndFilters(
                    sortMode       = newSort,
                    showSystem     = checkboxSystem.isChecked,
                    showPersistent = checkboxPersistent.isChecked,
                )
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }


    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.main_open_app_info_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUninstallConfirmation(app: AppModel) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_uninstall_title, app.appName))
            .setMessage(getString(R.string.main_uninstall_message))
            .setPositiveButton(getString(R.string.main_uninstall_confirm)) { _, _ ->
                viewModel.autoKillManager.uninstallPackage(app.packageName) {
                    viewModel.loadBackgroundApps()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showAppTriggersDialog(app: AppModel) {
        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${getString(R.string.menu_app_triggers)}: ${app.appName}")
            .setMessage(getString(R.string.triggers_loading))
            .setCancelable(true)
            .create()
        loadingDialog.show()

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val handler  = android.os.Handler(android.os.Looper.getMainLooper())
        executor.execute {
            val analyzer = AppTriggersAnalyzer(this, viewModel.shellManager)
            val triggers = analyzer.analyze(app.packageName)
            val status   = analyzer.resolveAppStatus(app.packageName)
            val score    = analyzer.calculateAggressionScore(triggers)
            handler.post {
                loadingDialog.dismiss()
                if (isFinishing || isDestroyed) return@post
            }
        }
    }

    private fun handleBackgroundRestriction(app: AppModel) {
        when {
            app.needsBackgroundRestrictionReapply() -> showOutOfSyncRestrictionDialog(app)
            app.isBackgroundRestrictionExternal()   -> showExternalRestrictionDialog(app)
            !app.isBackgroundRestrictionDesired && app.isSystemApp -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_system_app_warning_title))
                    .setMessage(getString(R.string.main_system_app_restriction_warning))
                    .setPositiveButton(getString(R.string.dialog_apply)) { _, _ ->
                        applyBackgroundRestriction(app, true)
                    }
                    .setNegativeButton(getString(R.string.dialog_cancel), null)
                    .show()
            }
            else -> applyBackgroundRestriction(app, !app.isBackgroundRestrictionDesired)
        }
    }

    private fun showOutOfSyncRestrictionDialog(app: AppModel) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_restriction_out_of_sync_title))
            .setMessage(getString(R.string.main_restriction_out_of_sync_message, app.appName))
            .setPositiveButton(getString(R.string.main_restriction_resume)) { _, _ -> applyBackgroundRestriction(app, true) }
            .setNeutralButton(getString(R.string.main_restriction_remove_from_list)) { _, _ -> applyBackgroundRestriction(app, false) }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showExternalRestrictionDialog(app: AppModel) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_restriction_external_title))
            .setMessage(getString(R.string.main_restriction_external_message, app.appName))
            .setPositiveButton(getString(R.string.main_restriction_add_to_reappzuku)) { _, _ -> applyBackgroundRestriction(app, true) }
            .setNeutralButton(getString(R.string.main_restriction_remove)) { _, _ -> applyBackgroundRestriction(app, false) }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun applyBackgroundRestriction(app: AppModel, enable: Boolean) {
        viewModel.appManager.setBackgroundRestricted(app.packageName, enable) {
            viewModel.loadBackgroundApps()
        }
    }

    private fun getBackgroundRestrictionTitle(app: AppModel): String = when {
        app.needsBackgroundRestrictionReapply()                                 -> getString(R.string.main_restriction_menu_out_of_sync)
        app.isBackgroundRestrictionExternal()                                   -> getString(R.string.main_restriction_menu_external)
        app.isBackgroundRestrictionDesired && !app.isBackgroundRestrictionActualKnown() -> getString(R.string.main_restriction_menu_saved)
        else                                                                    -> getString(R.string.main_restriction_menu_default)
    }


    private fun showSystemScanDialog() {
        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.scansystem_dialog_title))
            .setMessage(getString(R.string.scansystem_scanning))
            .setCancelable(true)
            .create()
        loadingDialog.show()

        val snapshot = viewModel.uiState.value.fullApps
            .filter { !it.isProtected && !it.isPersistentApp && !it.isWhitelisted && it.packageName != packageName }

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val handler  = android.os.Handler(android.os.Looper.getMainLooper())
        executor.execute {
            val scanner = ScanSystem(this, viewModel.shellManager)
            val loads   = scanner.scan(snapshot)
            handler.post {
                loadingDialog.dismiss()
                if (isFinishing || isDestroyed) return@post
                if (loads.isEmpty()) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.scansystem_dialog_title))
                        .setMessage(getString(R.string.scansystem_no_load))
                        .setPositiveButton(getString(R.string.dialog_close), null)
                        .show()
                    return@post
                }
                val dialogView = layoutInflater.inflate(R.layout.dialog_system_scan, null)
                val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.scan_recycler)
                recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
                recycler.adapter = ScanResultAdapter(this, loads)
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.scansystem_dialog_title))
                    .setView(dialogView)
                    .setPositiveButton(getString(R.string.dialog_close), null)
                    .show()
            }
        }
    }


    private fun ensureServiceRunning() {
        if (prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false) &&
            !ShappkyService.isRunning()
        ) {
            val intent = Intent(this, ShappkyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }
    }


    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun readThemeConfig(): AppThemeConfig {
        val accentId    = prefs.getInt(KEY_ACCENT, ACCENT_SYSTEM)
        val isAmoled    = prefs.getBoolean(KEY_AMOLED, false)
        val nightMode   = prefs.getInt(KEY_THEME, -1)
        val customColor = prefs.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
        val onColorPref = prefs.getInt(KEY_ACCENT_ON_COLOR, 0)
        return AppThemeConfig(
            accentId       = accentId,
            isAmoled       = isAmoled,
            nightMode      = nightMode,
            customColor    = ComposeColor(customColor),
            onColorIsBlack = onColorPref == ACCENT_ON_BLACK,
        )
    }
}
