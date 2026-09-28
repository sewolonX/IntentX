// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors
package io.github.wxxsfxyzm.intentx.data.settings.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

class AppDataStore(private val dataStore: DataStore<Preferences>) {
    val data: Flow<Preferences> = dataStore.data

    enum class PreferenceValueType { STRING, INT, BOOLEAN }

    data class SupportedPreferenceKey<T>(val key: Preferences.Key<T>, val type: PreferenceValueType)

    companion object {
        private val mutableSupportedKeys = linkedMapOf<String, SupportedPreferenceKey<*>>()
        val supportedKeys: Map<String, SupportedPreferenceKey<*>>
            get() = mutableSupportedKeys.toMap()

        private fun <T> register(key: Preferences.Key<T>, type: PreferenceValueType): Preferences.Key<T> {
            mutableSupportedKeys[key.name] = SupportedPreferenceKey(key, type)
            return key
        }

        val AUTHORIZER = register(stringPreferencesKey("authorizer"), PreferenceValueType.STRING)
        val SHOW_SYSTEM_APPS = register(booleanPreferencesKey("show_system_apps"), PreferenceValueType.BOOLEAN)
        val CATALOG_SORT_ORDER = register(stringPreferencesKey("catalog_sort_order"), PreferenceValueType.STRING)
        val CATALOG_REVERSE_ORDER = register(booleanPreferencesKey("catalog_reverse_order"), PreferenceValueType.BOOLEAN)
        val CATALOG_SHOW_PACKAGE_NAME = register(booleanPreferencesKey("catalog_show_package_name"), PreferenceValueType.BOOLEAN)
        val CATALOG_SEARCH_ACTIVITIES = register(booleanPreferencesKey("catalog_search_activities"), PreferenceValueType.BOOLEAN)
        val CATALOG_HIDE_OVERLAYS = register(booleanPreferencesKey("catalog_hide_overlays"), PreferenceValueType.BOOLEAN)
        val ACTIVITY_SORT_ORDER = register(stringPreferencesKey("activity_sort_order"), PreferenceValueType.STRING)
        val ACTIVITY_EXPORTED_FILTER = register(stringPreferencesKey("activity_exported_filter"), PreferenceValueType.STRING)
        val ACTIVITY_ENABLED_FILTER = register(stringPreferencesKey("activity_enabled_filter"), PreferenceValueType.STRING)
        val UI_USE_BLUR = register(booleanPreferencesKey("ui_use_blur"), PreferenceValueType.BOOLEAN)
        val THEME_MODE = register(stringPreferencesKey("theme_mode"), PreferenceValueType.STRING)
        val THEME_USE_PURE_BLACK = register(booleanPreferencesKey("theme_use_pure_black"), PreferenceValueType.BOOLEAN)
        val THEME_PALETTE_STYLE = register(stringPreferencesKey("theme_palette_style"), PreferenceValueType.STRING)
        val THEME_COLOR_SPEC = register(stringPreferencesKey("theme_color_spec"), PreferenceValueType.STRING)
        val THEME_USE_DYNAMIC_COLOR = register(booleanPreferencesKey("theme_use_dynamic_color"), PreferenceValueType.BOOLEAN)
        val THEME_SEED_COLOR = register(intPreferencesKey("theme_seed_color"), PreferenceValueType.INT)
        val UI_USE_APPLE_FLOATING_BAR = register(booleanPreferencesKey("ui_use_apple_floating_bar"), PreferenceValueType.BOOLEAN)
        val PREDICTIVE_BACK_ANIMATION = register(stringPreferencesKey("predictive_back_animation"), PreferenceValueType.STRING)
        val PREDICTIVE_BACK_EXIT_DIRECTION = register(stringPreferencesKey("predictive_back_exit_direction"), PreferenceValueType.STRING)
    }

    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        dataStore.edit { transform(it) }
    }

    suspend fun putString(key: Preferences.Key<String>, value: String) {
        edit { it[key] = value }
        Timber.d("String preference saved: key=%s", key.name)
    }

    suspend fun putInt(key: Preferences.Key<Int>, value: Int) {
        edit { it[key] = value }
        Timber.d("Integer preference saved: key=%s", key.name)
    }

    suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        edit { it[key] = value }
        Timber.d("Boolean preference saved: key=%s", key.name)
    }

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> = data.map { it[key] ?: default }.distinctUntilChanged()

    fun getInt(key: Preferences.Key<Int>, default: Int = 0): Flow<Int> = data.map { it[key] ?: default }.distinctUntilChanged()

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> = data.map { it[key] ?: default }.distinctUntilChanged()
}
