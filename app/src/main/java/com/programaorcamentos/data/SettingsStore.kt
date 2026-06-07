package com.programaorcamentos.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val nextBudgetNumber = intPreferencesKey("next_budget_number")
    private val lastBackupEpochDay = longPreferencesKey("last_backup_epoch_day")
    private val backupReminderDays = intPreferencesKey("backup_reminder_days")

    suspend fun reserveBudgetNumber(): Int {
        var value = 1
        context.dataStore.edit { prefs ->
            value = prefs[nextBudgetNumber] ?: 1
            prefs[nextBudgetNumber] = value + 1
        }
        return value
    }

    suspend fun peekNextBudgetNumber(): Int = context.dataStore.data.first()[nextBudgetNumber] ?: 1

    suspend fun setNextBudgetNumber(value: Int) {
        context.dataStore.edit { it[nextBudgetNumber] = value.coerceAtLeast(1) }
    }

    suspend fun markBackupToday() {
        context.dataStore.edit { it[lastBackupEpochDay] = LocalDate.now().toEpochDay() }
    }

    suspend fun getBackupReminderDays(): Int = context.dataStore.data.first()[backupReminderDays] ?: 30
}
