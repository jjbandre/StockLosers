package com.example.stocklosers

import android.content.Context
import android.util.Log // Added for logging, recommended
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Define the DataStore instance at the top level, specific to this context usage.
// The name "history" should be unique within your app for this DataStore.
private val Context.historyDataStore by preferencesDataStore(name = "alert_history")

class AlertHistory(private val context: Context) {

    // Companion object for constants or static utility functions if needed.
    companion object {
        // Using a simple date format for the preference key. ISO_LOCAL_DATE is good.
        // This is not strictly needed here if keyFor directly formats, but good for clarity.
        // private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

        // Generates a unique preference key for each date.
        // Ensure the key name is descriptive.
        private fun notifiedSymbolsKeyForDate(date: LocalDate): Preferences.Key<Set<String>> {
            // Using ISO_LOCAL_DATE format for consistency in keys, e.g., "notified_symbols_2023-10-27"
            return stringSetPreferencesKey("notified_symbols_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
        }
    }

    /**
     * Checks if a specific stock symbol has already been marked as notified for a given date.
     *
     * @param symbol The stock symbol to check.
     * @param date The date for which to check the notification status. Defaults to the current date.
     * @return True if the symbol has been notified for the given date, false otherwise.
     */
    suspend fun hasBeenNotified(symbol: String, date: LocalDate = LocalDate.now()): Boolean {
        val preferenceKey = notifiedSymbolsKeyForDate(date)
        return try {
            val notifiedSymbolsOnDate = context.historyDataStore.data
                .map { preferences ->
                    preferences[preferenceKey] ?: emptySet()
                }.first() // Gets the first emitted set (current state)
            val wasNotified = symbol in notifiedSymbolsOnDate
            Log.d("AlertHistory", "Checking if '$symbol' was notified on $date: $wasNotified. Set: $notifiedSymbolsOnDate")
            wasNotified
        } catch (e: Exception) {
            Log.e("AlertHistory", "Error reading notification status for '$symbol' on $date", e)
            false // Default to false in case of error to avoid missing notifications
        }
    }

    /**
     * Marks a collection of stock symbols as notified for a given date.
     * This will add the symbols to the set of notified symbols for that date.
     *
     * @param symbols The collection of stock symbols to mark as notified.
     * @param date The date for which these symbols are being marked. Defaults to the current date.
     */
    suspend fun markNotified(symbols: Collection<String>, date: LocalDate = LocalDate.now()) {
        if (symbols.isEmpty()) {
            Log.d("AlertHistory", "No symbols provided to mark as notified for $date.")
            return
        }
        val preferenceKey = notifiedSymbolsKeyForDate(date)
        try {
            context.historyDataStore.edit { preferences ->
                val currentNotifiedSymbols = preferences[preferenceKey] ?: emptySet()
                preferences[preferenceKey] = currentNotifiedSymbols + symbols // Add new symbols to existing set
            }
            Log.d("AlertHistory", "Marked symbols as notified for $date: $symbols")
        } catch (e: Exception) {
            Log.e("AlertHistory", "Error marking symbols as notified for $date", e)
        }
    }
}

