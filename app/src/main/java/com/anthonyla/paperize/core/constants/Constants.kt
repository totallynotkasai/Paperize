package com.anthonyla.paperize.core.constants

object Constants {
    // Database
    const val DATABASE_NAME = "paperize_database"
    const val DATABASE_VERSION = 5  // v5: exclude/favourite/lost-access flags, per-album effects and favourites mode

    // DataStore
    const val PREFERENCES_NAME = "paperize_preferences"

    // Notifications
    const val NOTIFICATION_CHANNEL_ID = "paperize_channel"  // silent "changing wallpaper" notices
    const val ALERT_CHANNEL_ID = "paperize_alerts"  // problems; audible by default
    /** The screen-off/unlock listener's required notice; minimum importance, so it stays out of sight. */
    const val LISTENER_CHANNEL_ID = "paperize_listener"
    const val NOTIFICATION_ID = 1
    const val LISTENER_NOTIFICATION_ID = 3  // 2 is WallpaperNotifier's problem notice

    // Services
    const val ACTION_CHANGE_WALLPAPER = "com.anthonyla.paperize.ACTION_CHANGE_WALLPAPER"
    const val ACTION_APPLY_SPECIFIC_WALLPAPER =
        "com.anthonyla.paperize.ACTION_APPLY_SPECIFIC_WALLPAPER"
    const val ACTION_REAPPLY_EFFECTS = "com.anthonyla.paperize.ACTION_REAPPLY_EFFECTS"
    const val ACTION_RELOAD_WALLPAPER = "com.anthonyla.paperize.ACTION_RELOAD_WALLPAPER"

    // WorkManager
    const val WORK_NAME_HOME = "wallpaper_change_home"
    const val WORK_NAME_LOCK = "wallpaper_change_lock"
    const val WORK_NAME_BOTH = "wallpaper_change_both"
    const val WORK_NAME_LIVE = "wallpaper_change_live"
    const val WORK_NAME_REFRESH = "album_refresh"
    const val WORK_TAG_HOME = "wallpaper_change_home_tag"
    const val WORK_TAG_LOCK = "wallpaper_change_lock_tag"
    const val WORK_TAG_BOTH = "wallpaper_change_both_tag"
    const val WORK_TAG_LIVE = "wallpaper_change_live_tag"
    const val WORK_TAG_REFRESH = "album_refresh_tag"
    const val WORK_NAME_TIMED_CHANGE = "timed_change"
    const val WORK_NAME_DARK_MODE_CHECK = "dark_mode_check"
    const val WORK_TAG_SCHEDULE_EVENTS = "schedule_events_tag"
    /** WorkManager keeps its JobScheduler job IDs at or below this; Paperize's own jobs sit above it. */
    const val MAX_WORK_MANAGER_JOB_ID = 1_000_000
    const val MAX_WORK_RETRY_ATTEMPTS = 3

    // Intents
    const val EXTRA_SCREEN_TYPE = "screen_type"
    const val EXTRA_WALLPAPER_ID = "wallpaper_id"

    // Wallpaper
    const val DEFAULT_BLUR_PERCENTAGE = 0
    const val DEFAULT_DARKEN_PERCENTAGE = 0
    const val DEFAULT_VIGNETTE_PERCENTAGE = 0
    const val DEFAULT_PARALLAX_INTENSITY = 50
    const val DEFAULT_GRAYSCALE_PERCENTAGE = 0
    const val MAX_EFFECT_PERCENTAGE = 100
    const val SLIDER_EFFECT_STEPS = 99
    const val DIALOG_MESSAGE_MAX_LINES = 10
    const val MIN_EFFECT_PERCENTAGE = 0
    const val FLOW_SUBSCRIPTION_TIMEOUT_MS = 5000L

    // Scheduling
    const val MIN_LIVE_INTERVAL_MINUTES = 1
    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_INTERVAL_MINUTES = 43200  // 30 days in minutes
    const val DEFAULT_INTERVAL_MINUTES = 60
    /** Opening the app rescans folders at most this often; the daily 3 AM refresh always runs. */
    const val FOREGROUND_REFRESH_MIN_INTERVAL_MS = 4 * 60 * 60 * 1000L

    // Smarter scheduling (fork plan, Phase 6)
    /** Screen-off and unlock changes: the minimum-gap choices, in minutes (0 = every time). */
    val TRIGGER_GAP_STEPS_MINUTES = listOf(0, 5, 15, 60, 180)
    const val DEFAULT_TRIGGER_GAP_MINUTES = 15
    const val MAX_TRIGGER_GAP_MINUTES = 180
    /** How long a screen-off or unlock change may keep the CPU awake. */
    const val TRIGGER_WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 1000L
    /** Set times, as minutes after midnight: 07:00 and 19:00. */
    val DEFAULT_CHANGE_TIMES = listOf(7 * 60, 19 * 60)
    const val MAX_CHANGE_TIMES = 12
    const val DEFAULT_NIGHT_START_MINUTES = 19 * 60
    const val DEFAULT_DAY_START_MINUTES = 7 * 60
    /**
     * Set times and the day/night switch use inexact alarms, which need no special permission.
     * Android 12+ allows no window shorter than 10 minutes.
     */
    const val TIMED_ALARM_WINDOW_MS = 10 * 60 * 1000L
    /** Catches dark-theme switches made by a schedule, which change no setting Paperize can watch. */
    const val DARK_MODE_CHECK_INTERVAL_MINUTES = 15L

    // UI
    const val ANIMATION_DURATION_LONG_MS = 800  // For item reordering animations
    /** Settings are saved at once; re-rendering the static wallpaper waits this long for more edits. */
    const val SETTINGS_DEBOUNCE_MS = 1500L
    const val WALLPAPER_ASPECT_RATIO = 9f / 16f  // Standard phone aspect ratio
    const val GRID_THUMBNAIL_WIDTH = 300
    const val GRID_THUMBNAIL_HEIGHT = 500
    const val LIST_THUMBNAIL_SIZE = 150
    const val PREVIEW_THUMBNAIL_WIDTH = 600
    const val PREVIEW_THUMBNAIL_HEIGHT = 1200

    // Time conversion
    const val MINUTES_PER_HOUR = 60
    const val MINUTES_PER_DAY = 1440

    // Image processing
    const val MAX_BLUR_RADIUS = 25.0f  // pixels at 100%, as Android blur radii (see blurRadiusToSigma)
    /** Horizontal scrolling keeps at most this many screens of a wide image (panoramas are cropped). */
    const val MAX_SCROLLING_WIDTH_SCREENS = 3
    const val BRIGHTNESS_SAMPLE_SIZE = 10  // Pixel sample size for brightness calculation

    // Luminance coefficients (ITU-R BT.709 standard)
    const val LUMINANCE_RED = 0.2126
    const val LUMINANCE_GREEN = 0.7152
    const val LUMINANCE_BLUE = 0.0722

    // Adaptive brightness thresholds (based on WallYou implementation)
    const val LIGHT_BRIGHTNESS_MIN = 0.8f  // Threshold for bright images in dark mode
    const val DARK_BRIGHTNESS_MAX = 0.3f  // Threshold for dark images in light mode
    const val TARGET_BRIGHTNESS_DARK = 0.7f  // Target brightness in dark mode
    const val TARGET_BRIGHTNESS_LIGHT = 0.4f  // Target brightness in light mode

    // Vignette effect
    const val VIGNETTE_DIVISOR = 150f  // Radius calculation divisor
    const val VIGNETTE_MIN_RADIUS = 1f  // Minimum vignette radius (guards against zero-radius RadialGradient crash)
    const val VIGNETTE_INNER_ALPHA = 0.1f  // Inner alpha for vignette gradient
    const val VIGNETTE_OUTER_ALPHA = 0.8f  // Outer alpha for vignette gradient
    val VIGNETTE_GRADIENT_POSITIONS = floatArrayOf(0f, 0.7f, 1f)  // Vignette gradient positions

    // Input validation
    const val MAX_DAYS_INPUT_LENGTH = 3
    const val MAX_HOURS_MINUTES_INPUT_LENGTH = 2

    // Renderer
    const val CROSSFADE_DURATION_MS = 750f
    const val RELOAD_THROTTLE_MS = 250L
    const val BLUR_MIN_THRESHOLD = 0.01f
    const val PERCENTAGE_DIVISOR = 100f
    const val GL_ES_VERSION = 2

    // Live auto-pan (fork plan 4.2). A sweep is one pass from one edge of the image to the other.
    const val DEFAULT_AUTO_PAN_SWEEP_SECONDS = 60
    /** The speed slider's stops, slowest first. */
    val AUTO_PAN_SWEEP_STEPS_SECONDS = listOf(300, 180, 120, 90, 60, 45, 30, 20, 15, 10)
    /** Live images are decoded with at most this many pixels, so panoramas fit in memory. */
    const val MAX_LIVE_DECODE_PIXELS = 4096L * 4096L

    // Wallpaper loading
    const val MAX_WALLPAPER_LOAD_RETRIES = 10

    // File access. Android 11+ keeps at most 512 persisted grants per app and silently drops the
    // oldest beyond that, so imports stop before the limit and warn as it gets close.
    const val MAX_PERSISTED_URI_GRANTS = 512
    const val PERSISTED_URI_GRANT_WARNING = 400
}

object PreferenceKeys {
    // Theme
    const val DARK_MODE = "dark_mode"
    const val DYNAMIC_THEMING = "dynamic_theming"
    const val ANIMATE = "animate"

    // Scheduling
    const val ENABLE_CHANGER = "enable_changer"
    const val SEPARATE_SCHEDULES = "separate_schedules"
    const val SHUFFLE_ENABLED = "shuffle_enabled"
    const val HOME_ENABLED = "home_enabled"
    const val LOCK_ENABLED = "lock_enabled"
    const val HOME_ALBUM_ID = "home_album_id"
    const val LOCK_ALBUM_ID = "lock_album_id"
    const val HOME_INTERVAL_MINUTES = "home_interval_minutes"
    const val LOCK_INTERVAL_MINUTES = "lock_interval_minutes"

    // Effects - Home
    const val HOME_ENABLE_BLUR = "home_enable_blur"
    const val HOME_BLUR = "home_blur"
    const val HOME_ENABLE_DARKEN = "home_enable_darken"
    const val HOME_DARKEN = "home_darken"
    const val HOME_ENABLE_VIGNETTE = "home_enable_vignette"
    const val HOME_VIGNETTE = "home_vignette"
    const val HOME_ENABLE_GRAYSCALE = "home_enable_grayscale"
    const val HOME_GRAYSCALE = "home_grayscale"

    // Effects - Lock
    const val LOCK_ENABLE_BLUR = "lock_enable_blur"
    const val LOCK_BLUR = "lock_blur"
    const val LOCK_ENABLE_DARKEN = "lock_enable_darken"
    const val LOCK_DARKEN = "lock_darken"
    const val LOCK_ENABLE_VIGNETTE = "lock_enable_vignette"
    const val LOCK_VIGNETTE = "lock_vignette"
    const val LOCK_ENABLE_GRAYSCALE = "lock_enable_grayscale"
    const val LOCK_GRAYSCALE = "lock_grayscale"

    // Interactive Effects - Home (live wallpaper mode only)
    const val HOME_ENABLE_DOUBLE_TAP = "home_enable_double_tap"
    const val HOME_ENABLE_PARALLAX = "home_enable_parallax"
    const val HOME_PARALLAX_INTENSITY = "home_parallax_intensity"

    // Interactive Effects - Lock (live wallpaper mode only)
    const val LOCK_ENABLE_DOUBLE_TAP = "lock_enable_double_tap"
    const val LOCK_ENABLE_PARALLAX = "lock_enable_parallax"
    const val LOCK_PARALLAX_INTENSITY = "lock_parallax_intensity"

    // Live Wallpaper Mode Settings
    const val LIVE_ALBUM_ID = "live_album_id"
    const val LIVE_INTERVAL_MINUTES = "live_interval_minutes"
    const val LIVE_ENABLE_BLUR = "live_enable_blur"
    const val LIVE_BLUR = "live_blur"
    const val LIVE_ENABLE_DARKEN = "live_enable_darken"
    const val LIVE_DARKEN = "live_darken"
    const val LIVE_ENABLE_VIGNETTE = "live_enable_vignette"
    const val LIVE_VIGNETTE = "live_vignette"
    const val LIVE_ENABLE_GRAYSCALE = "live_enable_grayscale"
    const val LIVE_GRAYSCALE = "live_grayscale"
    const val LIVE_ENABLE_DOUBLE_TAP = "live_enable_double_tap"
    const val LIVE_ENABLE_CHANGE_ON_SCREEN_OFF = "live_enable_change_on_screen_off"
    const val LIVE_ENABLE_PARALLAX = "live_enable_parallax"
    const val LIVE_PARALLAX_INTENSITY = "live_parallax_intensity"
    const val LIVE_ENABLE_AUTO_PAN = "live_enable_auto_pan"
    const val LIVE_AUTO_PAN_SWEEP_SECONDS = "live_auto_pan_sweep_seconds"

    // Scaling
    const val HOME_SCALING_TYPE = "home_scaling_type"
    const val LOCK_SCALING_TYPE = "lock_scaling_type"
    const val LIVE_SCALING_TYPE = "live_scaling_type"
    const val HOME_SCROLLING_ENABLED = "home_scrolling_enabled"

    // Behavior
    const val ADAPTIVE_BRIGHTNESS = "adaptive_brightness"

    // Smarter scheduling (fork plan, Phase 6)
    const val ONLY_WHILE_CHARGING = "only_while_charging"
    const val PAUSE_IN_BATTERY_SAVER = "pause_in_battery_saver"
    const val CHANGE_ON_SCREEN_OFF = "change_on_screen_off"
    const val SCREEN_OFF_TARGET = "screen_off_target"
    const val CHANGE_ON_UNLOCK = "change_on_unlock"
    const val UNLOCK_TARGET = "unlock_target"
    const val TRIGGER_GAP_MINUTES = "trigger_gap_minutes"
    const val SCHEDULE_TYPE = "schedule_type"
    const val CHANGE_TIMES = "change_times"  // comma-separated minutes after midnight
    const val HOME_NIGHT_ALBUM_ID = "home_night_album_id"
    const val LOCK_NIGHT_ALBUM_ID = "lock_night_album_id"
    const val LIVE_NIGHT_ALBUM_ID = "live_night_album_id"
    const val NIGHT_TRIGGER = "night_trigger"
    const val NIGHT_START_MINUTES = "night_start_minutes"
    const val DAY_START_MINUTES = "day_start_minutes"
    const val NIGHT_ACTIVE = "night_active"

    // First launch
    const val FIRST_LAUNCH = "first_launch"

    // Wallpaper mode
    const val WALLPAPER_MODE = "wallpaper_mode"
}
