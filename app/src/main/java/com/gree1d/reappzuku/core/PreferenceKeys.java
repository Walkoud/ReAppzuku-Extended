package com.gree1d.reappzuku.core;


public final class PreferenceKeys {
    private PreferenceKeys() {
       
    }

    public static final String PREFERENCES_NAME = "AppPreferences";

    // Custom accents
    public static final String KEY_ACCENT_CUSTOM_COLOR = "accent_custom_color";
    public static final String KEY_ACCENT_ON_COLOR = "accent_on_color";
    
    // App Lists
    public static final String KEY_HIDDEN_APPS = "hidden_apps";
    public static final String KEY_WHITELISTED_APPS = "whitelisted_apps";
    public static final String KEY_BLACKLISTED_APPS = "blacklisted_apps";
    public static final String KEY_AUTOSTART_DISABLED_APPS = "autostart_disabled_apps";
    public static final String KEY_MEDIUM_RESTRICTION_APPS = "medium_restriction_apps";
    public static final String KEY_MANUAL_BUCKET_PREFIX = "manual_bucket_";
    public static final String KEY_HARD_RESTRICTION_APPS = "hard_restriction_apps";
    public static final String KEY_MANUAL_RESTRICTION_APPS = "manual_restriction_apps";
    public static final String KEY_MANUAL_OPS_PREFIX = "manual_ops_mask_";
    public static final String KEY_APPLIED_OPS_MASK_PREFIX = "applied_ops_mask_";
    public static final String KEY_MANUAL_WHITELIST_REMOVAL_PREFIX = "manual_whitelist_removal_";

    public static final String KEY_BATTERY_WHITELIST_REMOVED = "battery_whitelist_removed";

    // Kill Mode
    public static final String KEY_KILL_MODE = "kill_mode"; 

    // Auto-Kill Advanced
    public static final String KEY_AUTO_KILL_TYPE = "auto_kill_type";
    public static final String KEY_HW_TRIGGER_HEADSET = "hw_trigger_headset";
    public static final String KEY_HW_TRIGGER_USB = "hw_trigger_usb";
    public static final String KEY_HW_TRIGGER_CHARGER = "hw_trigger_charger";
    public static final String KEY_APP_LAUNCH_TRIGGER_ENABLED = "app_launch_trigger_enabled";
    public static final String KEY_APP_LAUNCH_TRIGGER_PACKAGES = "app_launch_trigger_packages";
    public static final String KEY_HW_TRIGGER_WIFI = "hw_trigger_wifi";
    public static final String KEY_HW_TRIGGER_BLUETOOTH = "hw_trigger_bluetooth";
    public static final String KEY_HW_TRIGGER_GPS = "hw_trigger_gps";
    public static final String KEY_HW_TRIGGER_HOTSPOT = "hw_trigger_hotspot";
    public static final String KEY_APP_LAUNCH_CLEAR_CACHE = "app_launch_clear_cache";

    // Auto-Kill Presets
    public static final String KEY_ACTIVE_PRESET = "active_preset_number";

    // Service & Automation
    public static final String KEY_AUTO_KILL_ENABLED = "autoKillEnabled";
    public static final String KEY_PERIODIC_KILL_ENABLED = "periodicKillEnabled";
    public static final String KEY_AUTO_KILL_PENDING_RSS = "auto_kill_pending_rss";
    public static final String KEY_KILL_INTERVAL = "killInterval";
    public static final String KEY_KILL_ON_SCREEN_OFF = "killOnScreenOff";

    // RAM Threshold
    public static final String KEY_RAM_THRESHOLD = "ramThreshold";
    public static final String KEY_RAM_THRESHOLD_ENABLED = "ramThresholdEnabled";

    // Display Settings
    public static final String KEY_SHOW_SYSTEM_APPS = "showSystemApps";
    public static final String KEY_SHOW_PERSISTENT_APPS = "showPersistentApps";
    public static final String KEY_THEME = "appTheme";
    public static final String KEY_ACCENT = "appAccent";
    public static final String KEY_AMOLED = "appAmoled";
    public static final String KEY_SORT_MODE = "sort_mode";
    public static final String KEY_REPLACEMENT_NOTICE_SHOWN_VERSION = "replacement_notice_shown_version";

    // Notifications
    public static final String KEY_NOTIFICATION_MODE = "notificationMode";
    public static final int NOTIFICATION_MODE_ALL = 0;
    public static final int NOTIFICATION_MODE_IMPORTANT_ONLY = 1;

    // Sleep Mode
    public static final String KEY_SLEEP_MODE_ENABLED = "sleepModeEnabled";
    public static final String KEY_SLEEP_MODE_APPS = "sleepModeApps";
    public static final String KEY_SLEEP_MODE_APPS_PERMANENT = "sleep_mode_apps_permanent";
    public static final String KEY_SLEEP_MODE_DELAY = "sleepModeDelay";
    public static final String KEY_SLEEP_MODE_APPS_FROZEN = "sleep_mode_apps_frozen";
    public static final String KEY_SLEEP_MODE_APPS_SUSPEND_METHOD = "sleep_mode_apps_suspend_method";

}
