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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PasswordManager(
    private val context: Context,
    private val cryptoManager: CryptoManager? = null
) {
    private val dataStore = context.dataStore

    val isPasswordSet: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PASSWORD_HASH_KEY] != null
    }

    val isBiometricEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRIC_ENABLED_KEY] ?: false
    }

    val lastBackupTime: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_BACKUP_KEY] ?: 0L
    }

    suspend fun setPassword(password: String) {
        val hashedPassword = hashPassword(password)
        val encryptedHash = if (cryptoManager != null) {
            cryptoManager.encryptBytes(hashedPassword.toByteArray())
        } else {
            hashedPassword.toByteArray()
        }
        dataStore.edit { preferences ->
            preferences[PASSWORD_HASH_KEY] = android.util.Base64.encodeToString(
                encryptedHash,
                android.util.Base64.NO_WRAP
            )
        }
    }

    suspend fun verifyPassword(password: String): Boolean {
        val storedEncryptedHash = dataStore.data.first()[PASSWORD_HASH_KEY] ?: return false
        val encryptedHash = android.util.Base64.decode(storedEncryptedHash, android.util.Base64.NO_WRAP)
        val storedHash = if (cryptoManager != null) {
            String(cryptoManager.decryptBytes(encryptedHash))
        } else {
            String(encryptedHash)
        }
        val inputHash = hashPassword(password)
        return storedHash == inputHash
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(oldPassword)) {
            return false
        }
        setPassword(newPassword)
        return true
    }

    suspend fun enableBiometric(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setLastBackupTime(time: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_BACKUP_KEY] = time
        }
    }

    suspend fun isFirstLaunch(): Boolean {
        return dataStore.data.first()[IS_FIRST_LAUNCH_KEY] ?: true
    }

    suspend fun setFirstLaunchComplete() {
        dataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] = false
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    companion object {
        private val PASSWORD_HASH_KEY = stringPreferencesKey("password_hash")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val LAST_BACKUP_KEY = longPreferencesKey("last_backup")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
    }
}
