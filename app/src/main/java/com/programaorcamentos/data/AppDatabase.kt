package com.programaorcamentos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun statusToString(status: BudgetStatus): String = status.name
    @TypeConverter fun stringToStatus(value: String): BudgetStatus = BudgetStatus.valueOf(value)
}

@Database(
    entities = [CompanyProfile::class, Client::class, Budget::class, BudgetLine::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BudgetDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "budgets.db").build()
    }
}
