package com.attendance.rollcheck.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rollcheck_prefs", Context.MODE_PRIVATE)

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    fun hasPin(): Boolean = pinHash != null

    private val _ttsSpeedIndex = MutableStateFlow(prefs.getInt(KEY_TTS_SPEED, 0).coerceIn(0, TTS_DISABLED_INDEX))
    val ttsSpeedIndexFlow: StateFlow<Int> = _ttsSpeedIndex.asStateFlow()

    var ttsSpeedIndex: Int
        get() = prefs.getInt(KEY_TTS_SPEED, 0).coerceIn(0, TTS_DISABLED_INDEX)
        set(value) {
            val sanitized = value.coerceIn(0, TTS_DISABLED_INDEX)
            prefs.edit().putInt(KEY_TTS_SPEED, sanitized).apply()
            _ttsSpeedIndex.value = sanitized
        }

    val ttsSpeed: Float
        get() = TTS_SPEEDS[ttsSpeedIndex.coerceIn(0, TTS_SPEEDS.lastIndex)]

    val isTtsEnabled: Boolean
        get() = ttsSpeedIndex != TTS_DISABLED_INDEX

    val ttsSpeedLabel: String
        get() = TTS_SPEED_LABELS[ttsSpeedIndex.coerceIn(0, TTS_DISABLED_INDEX)]

    private val _ttsVoiceModel = MutableStateFlow(prefs.getString(KEY_TTS_VOICE_MODEL, null))
    val ttsVoiceModelFlow: StateFlow<String?> = _ttsVoiceModel.asStateFlow()

    var ttsVoiceModel: String?
        get() = prefs.getString(KEY_TTS_VOICE_MODEL, null)
        set(value) {
            prefs.edit().putString(KEY_TTS_VOICE_MODEL, value).apply()
            _ttsVoiceModel.value = value
        }

    private val _fontIndex = MutableStateFlow(prefs.getInt(KEY_FONT, 0))
    val fontIndexFlow: StateFlow<Int> = _fontIndex.asStateFlow()

    var fontIndex: Int
        get() = prefs.getInt(KEY_FONT, 0)
        set(value) {
            prefs.edit().putInt(KEY_FONT, value).apply()
            _fontIndex.value = value
        }

    private val _attendanceFolderUri = MutableStateFlow(prefs.getString(KEY_FOLDER_URI, null))
    val attendanceFolderUriFlow = _attendanceFolderUri.asStateFlow()

    var attendanceFolderUri: String?
        get() = prefs.getString(KEY_FOLDER_URI, null)
        set(value) {
            prefs.edit().putString(KEY_FOLDER_URI, value).apply()
            _attendanceFolderUri.value = value
        }

    private val _exportFolderUri = MutableStateFlow(prefs.getString(KEY_EXPORT_FOLDER_URI, null))
    val exportFolderUriFlow = _exportFolderUri.asStateFlow()

    var exportFolderUri: String?
        get() = prefs.getString(KEY_EXPORT_FOLDER_URI, null)
        set(value) {
            prefs.edit().putString(KEY_EXPORT_FOLDER_URI, value).apply()
            _exportFolderUri.value = value
        }

    var isFirstLaunchSetupDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, value).apply()

    var pinVerifiedThisSession: Boolean = false

    companion object {
        private const val KEY_PIN_HASH = "user_pin_hash"
        private const val KEY_TTS_SPEED = "tts_speed_index"
        private const val KEY_TTS_VOICE_MODEL = "tts_voice_model"
        private const val KEY_FONT = "font_index"
        private const val KEY_FOLDER_URI = "attendance_folder_uri"
        private const val KEY_EXPORT_FOLDER_URI = "export_folder_uri"
        private const val KEY_FIRST_LAUNCH_DONE = "first_launch_setup_done"

        const val SESSION_STAGE_ATTENDANCE = "attendance"
        const val SESSION_STAGE_MARK_PRESENT = "mark_present"
        const val SESSION_STAGE_MANUAL_ATTENDANCE = "manual_attendance"
        const val SESSION_STAGE_SUMMARY = "summary"
        const val SESSION_STAGE_NONE = "none"

        val TTS_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        const val TTS_DISABLED_INDEX = 5
        val TTS_SPEED_LABELS = listOf("1x", "1.25x", "1.5x", "1.75x", "2x", "No TTS")

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}