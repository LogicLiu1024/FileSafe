package cn.logicliu.filesafe.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_settings")

class SecuritySettingsManager(
    private val context: Context
) {
    private val dataStore = context.settingsDataStore

    val autoLockTime: Flow<Long> = dataStore.data.map { preferences ->
        preferences[AUTO_LOCK_TIME_KEY] ?: DEFAULT_AUTO_LOCK_TIME
    }

    val screenshotEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SCREENSHOT_ENABLED_KEY] ?: false
    }

    val biometricEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRIC_ENABLED_KEY] ?: false
    }

    val screenOffLockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SCREEN_OFF_LOCK_ENABLED_KEY] ?: false
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val modeString = preferences[THEME_MODE_KEY] ?: ThemeMode.LIGHT.name
        try {
            ThemeMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            ThemeMode.LIGHT
        }
    }

    val encryptionMode: Flow<EncryptionMode> = dataStore.data.map { preferences ->
        val modeString = preferences[ENCRYPTION_MODE_KEY] ?: EncryptionMode.ENCRYPT.name
        try {
            EncryptionMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            EncryptionMode.ENCRYPT
        }
    }

    suspend fun setAutoLockTime(timeMillis: Long) {
        dataStore.edit { preferences ->
            preferences[AUTO_LOCK_TIME_KEY] = timeMillis
        }
    }

    suspend fun setScreenshotEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCREENSHOT_ENABLED_KEY] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setScreenOffLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCREEN_OFF_LOCK_ENABLED_KEY] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setEncryptionMode(mode: EncryptionMode) {
        dataStore.edit { preferences ->
            preferences[ENCRYPTION_MODE_KEY] = mode.name
        }
    }

    companion object {
        private val AUTO_LOCK_TIME_KEY = longPreferencesKey("auto_lock_time")
        private val SCREENSHOT_ENABLED_KEY = booleanPreferencesKey("screenshot_enabled")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val SCREEN_OFF_LOCK_ENABLED_KEY = booleanPreferencesKey("screen_off_lock_enabled")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ENCRYPTION_MODE_KEY = stringPreferencesKey("encryption_mode")
        
        const val DEFAULT_AUTO_LOCK_TIME = 5 * 60 * 1000L // 5分钟
        val AUTO_LOCK_OPTIONS = listOf(
            0L to "立即",
            60 * 1000L to "1分钟",
            5 * 60 * 1000L to "5分钟",
            15 * 60 * 1000L to "15分钟",
            30 * 60 * 1000L to "30分钟",
            60 * 60 * 1000L to "1小时"
        )
    }
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class EncryptionMode {
    HIDE,
    ENCRYPT
}
