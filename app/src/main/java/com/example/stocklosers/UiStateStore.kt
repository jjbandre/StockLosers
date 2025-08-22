package com.example.stocklosers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.uiDataStore by preferencesDataStore(name = "ui_state")

class UiStateStore(private val context: Context) {
    private val pinnedKey = stringSetPreferencesKey("pinned_symbols")
    private fun dismissedKeyFor(date: LocalDate) =
        stringSetPreferencesKey("dismissed_${'$'}date")

    val pinned: Flow<Set<String>> =
        context.uiDataStore.data.map { it[pinnedKey] ?: emptySet() }

    val dismissedToday: Flow<Set<String>> =
        context.uiDataStore.data.map { it[dismissedKeyFor(LocalDate.now())] ?: emptySet() }

    suspend fun togglePin(symbol: String) {
        context.uiDataStore.edit { prefs ->
            val cur = prefs[pinnedKey] ?: emptySet()
            prefs[pinnedKey] = if (symbol in cur) cur - symbol else cur + symbol
        }
    }

    suspend fun dismiss(symbol: String) {
        val key = dismissedKeyFor(LocalDate.now())
        context.uiDataStore.edit { prefs ->
            val cur = prefs[key] ?: emptySet()
            prefs[key] = cur + symbol
        }
    }

    suspend fun undismissAllToday() {
        context.uiDataStore.edit { prefs ->
            prefs.remove(dismissedKeyFor(LocalDate.now()))
        }
    }

    suspend fun isDismissed(symbol: String): Boolean =
        symbol in dismissedToday.first()
}
