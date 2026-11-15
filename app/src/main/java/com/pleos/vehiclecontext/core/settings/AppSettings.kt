package com.pleos.vehiclecontext.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos HMI 화면 테마 모드:
 * - AUTO: 차량 시간 06:00~17:59 주간(Light) / 18:00~05:59 야간(Dark) 자동 전환
 * - LIGHT: 주간(Light) 고정
 * - DARK: 야간(Dark) 고정
 */
enum class ThemeMode(val label: String) {
    AUTO("시간별 자동"),
    LIGHT("주간 모드"),
    DARK("야간 모드");

    fun isDark(hour: Int): Boolean = when (this) {
        AUTO -> hour !in 6..17
        LIGHT -> false
        DARK -> true
    }
}

/**
 * 하차 전 알림 발동 시점 설정
 */
enum class AlertSensitivity(val label: String) {
    PARK_STANDARD("표준 (P단 주차)"),
    APPROACH_200M("민감 (목적지 200m 전)")
}

/**
 * 앱 전역 사용자 설정 모델 (UI 설정 패널 + 백엔드 엔진/음성 동작 공유)
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val alertSensitivity: AlertSensitivity = AlertSensitivity.PARK_STANDARD,
    val arrivalAlertEnabled: Boolean = true,
    val autoCoolingEnabled: Boolean = true,
    val smartphoneSyncEnabled: Boolean = true,
    val autoVoiceOnTrunkClose: Boolean = true,
    val askPurposeOnVoice: Boolean = true,
    val autoShoppingBundle: Boolean = true
)

interface AppSettingsStore {
    val settings: StateFlow<AppSettings>
    fun save(value: AppSettings)
    fun update(transform: (AppSettings) -> AppSettings) {
        save(transform(settings.value))
    }
}

class InMemoryAppSettingsStore(initial: AppSettings = AppSettings()) : AppSettingsStore {
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    override fun save(value: AppSettings) {
        _settings.value = value
    }
}

class SharedPrefsAppSettingsStore(context: Context) : AppSettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.AUTO,
        alertSensitivity = AlertSensitivity.entries.firstOrNull { it.name == prefs.getString(KEY_SENSITIVITY, null) }
            ?: AlertSensitivity.PARK_STANDARD,
        arrivalAlertEnabled = prefs.getBoolean(KEY_ARRIVAL_ALERT, true),
        autoCoolingEnabled = prefs.getBoolean(KEY_AUTO_COOLING, true),
        smartphoneSyncEnabled = prefs.getBoolean(KEY_SMARTPHONE_SYNC, true),
        autoVoiceOnTrunkClose = prefs.getBoolean(KEY_AUTO_VOICE, true),
        askPurposeOnVoice = prefs.getBoolean(KEY_ASK_PURPOSE, true),
        autoShoppingBundle = prefs.getBoolean(KEY_AUTO_SHOPPING, true)
    )

    override fun save(value: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME, value.themeMode.name)
            .putString(KEY_SENSITIVITY, value.alertSensitivity.name)
            .putBoolean(KEY_ARRIVAL_ALERT, value.arrivalAlertEnabled)
            .putBoolean(KEY_AUTO_COOLING, value.autoCoolingEnabled)
            .putBoolean(KEY_SMARTPHONE_SYNC, value.smartphoneSyncEnabled)
            .putBoolean(KEY_AUTO_VOICE, value.autoVoiceOnTrunkClose)
            .putBoolean(KEY_ASK_PURPOSE, value.askPurposeOnVoice)
            .putBoolean(KEY_AUTO_SHOPPING, value.autoShoppingBundle)
            .apply()
        _settings.value = value
    }

    companion object {
        private const val PREFS_NAME = "pleos_app_settings_v1"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SENSITIVITY = "alert_sensitivity"
        private const val KEY_ARRIVAL_ALERT = "arrival_alert_enabled"
        private const val KEY_AUTO_COOLING = "auto_cooling_enabled"
        private const val KEY_SMARTPHONE_SYNC = "smartphone_sync_enabled"
        private const val KEY_AUTO_VOICE = "auto_voice_on_trunk_close"
        private const val KEY_ASK_PURPOSE = "ask_purpose_on_voice"
        private const val KEY_AUTO_SHOPPING = "auto_shopping_bundle"
    }
}

private val shoppingTerms = listOf("쇼핑", "장보기", "장바구니", "마트", "백화점", "아울렛")

fun isShoppingContext(name: String, destination: String): Boolean =
    listOf(name, destination).any { text ->
        val compact = text.filterNot(Char::isWhitespace)
        shoppingTerms.any(compact::contains)
    }
