package com.example.todo.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException
import com.example.todo.ai.*
import kotlinx.coroutines.*

private val Context.settingsDataStore by preferencesDataStore("settings")
enum class ReminderMode { STANDARD, ALARM_CLOCK }
data class UserSettings(
    val theme: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean? = null,
    val allDayMinute: Int = 540,
    val saveSource: Boolean = false,
    val reminderMode: ReminderMode = ReminderMode.STANDARD,
)

class SettingsStore(context: Context) {
    private val store = context.settingsDataStore
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        UserSettings(it[Theme] ?: "SYSTEM", it[Dynamic] ?: true, it[Motion], it[AllDay] ?: 540, it[SaveSource] ?: false,
            ReminderMode.entries.firstOrNull { mode -> mode.name == it[Reminder] } ?: ReminderMode.STANDARD)
    }
    val ai = store.data.map {
        AiConfiguration(AiProvider.entries.firstOrNull { provider -> provider.name == it[Provider] } ?: AiProvider.OPENAI_COMPAT,
            it[BaseUrl].orEmpty(), it[Model].orEmpty(), it[Timeout] ?: 20, !it[EncryptedKey].isNullOrBlank(),
            it[KeyHint].orEmpty(), it[Consent] ?: false)
    }
    suspend fun saveAi(input: AiConfiguration, newKey: String?, secrets: SecretStore) {
        val config = input.validated()
        val trimmed = newKey?.trim()?.takeIf { it.isNotEmpty() }
        require(trimmed == null || trimmed.none { it.isISOControl() }) { "密钥不能包含换行或控制字符" }
        val encrypted = withContext(Dispatchers.IO) { trimmed?.let { secrets.seal(it, config.keyBinding()) } }
        store.edit {
            if (encrypted == null) require(it[Provider] == config.provider.name && it[BaseUrl] == config.baseUrl && !it[EncryptedKey].isNullOrBlank()) {
                "首次配置或更换服务地址后，请填写 API Key"
            }
            it[Provider] = config.provider.name; it[BaseUrl] = config.baseUrl; it[Model] = config.model; it[Timeout] = config.timeoutSeconds
            if (encrypted != null) { it[EncryptedKey] = encrypted; it[KeyHint] = trimmed!!.takeLast(4) }
        }
    }
    suspend fun aiKey(config: AiConfiguration, secrets: SecretStore): String {
        val data = store.data.first()
        if (data[Provider] != config.provider.name || data[BaseUrl] != config.baseUrl) throw AiException(AiFailure.CONFIG)
        return try { withContext(Dispatchers.IO) { secrets.open(requireNotNull(data[EncryptedKey]), config.keyBinding()) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw AiException(AiFailure.CONFIG) }
    }
    suspend fun aiConsent(granted: Boolean) { store.edit { it[Consent] = granted } }
    suspend fun clearAiKey() { store.edit { it.remove(EncryptedKey); it.remove(KeyHint) } }
    suspend fun theme(value: String) { require(value in listOf("SYSTEM", "LIGHT", "DARK")); store.edit { it[Theme] = value } }
    suspend fun dynamic(value: Boolean) { store.edit { it[Dynamic] = value } }
    suspend fun motion(value: Boolean?) { store.edit { if (value == null) it.remove(Motion) else it[Motion] = value } }
    suspend fun allDay(minute: Int) { require(minute in 0..1439); store.edit { it[AllDay] = minute } }
    suspend fun saveSource(value: Boolean) { store.edit { it[SaveSource] = value } }
    suspend fun reminderMode(value: ReminderMode) { store.edit { it[Reminder] = value.name } }
    private companion object {
        val Theme = stringPreferencesKey("theme")
        val Dynamic = booleanPreferencesKey("dynamic_color")
        val Motion = booleanPreferencesKey("reduce_motion")
        val AllDay = intPreferencesKey("all_day_minute")
        val SaveSource = booleanPreferencesKey("save_source")
        val Reminder = stringPreferencesKey("reminder_mode")
        val Provider = stringPreferencesKey("ai_provider")
        val BaseUrl = stringPreferencesKey("ai_base_url")
        val Model = stringPreferencesKey("ai_model")
        val Timeout = intPreferencesKey("ai_timeout")
        val EncryptedKey = stringPreferencesKey("ai_key_encrypted")
        val KeyHint = stringPreferencesKey("ai_key_hint")
        val Consent = booleanPreferencesKey("ai_consent_granted")
    }
}
