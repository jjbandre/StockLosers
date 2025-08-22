package com.example.stocklosers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.historyDataStore by preferencesDataStore(name = "history")

class AlertHistory(private val context: Context) {
    private fun keyFor(date: LocalDate) = stringSetPreferencesKey("notified_$date")

    suspend fun alreadyNotified(symbol: String, date: LocalDate = LocalDate.now()): Boolean {
        val set = context.historyDataStore.data.first()[keyFor(date)] ?: emptySet()
        return symbol in set
    }

    suspend fun markNotified(symbols: Collection<String>, date: LocalDate = LocalDate.now()) {
        context.historyDataStore.edit { prefs ->
            val key = keyFor(date)
            val current = prefs[key] ?: emptySet()
            prefs[key] = current + symbols
        }
    }
}
