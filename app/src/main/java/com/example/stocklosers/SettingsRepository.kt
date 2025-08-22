package com.example.stocklosers

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val ALERT_DROP = doublePreferencesKey("alert_drop_percent")

    val alertDropPercent: Flow<Double> =
        context.settingsDataStore.data.map { it[ALERT_DROP] ?: 35.0 }

    suspend fun setAlertDropPercent(value: Double) {
        val clamped = value.coerceIn(0.0, 95.0)
        context.settingsDataStore.edit { it[ALERT_DROP] = clamped }
    }
}
