package com.ba.terminal.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("ba_terminal_settings")

/** Minimal Glass settings: font size, transparency, extra-keys row. */
class SettingsStore(private val context: Context) {
    private val fontKey = floatPreferencesKey("font_size")
    private val alphaKey = floatPreferencesKey("transparency")
    private val keysKey = booleanPreferencesKey("extra_keys")

    val fontSize: Flow<Float> = context.dataStore.data.map { it[fontKey] ?: 14f }
    val transparency: Flow<Float> = context.dataStore.data.map { it[alphaKey] ?: 0.92f }
    val extraKeys: Flow<Boolean> = context.dataStore.data.map { it[keysKey] ?: true }

    suspend fun setFontSize(v: Float) { context.dataStore.edit { it[fontKey] = v } }
    suspend fun setTransparency(v: Float) { context.dataStore.edit { it[alphaKey] = v } }
    suspend fun setExtraKeys(v: Boolean) { context.dataStore.edit { it[keysKey] = v } }
}
