package com.programaorcamentos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun statusToString(status: BudgetStatus): String = status.name
    @TypeConverter fun stringToStatus(value: String): BudgetStatus = BudgetStatus.valueOf(value)
}

@Database(
    entities = [CompanyProfile::class, Client::class, Budget::class, BudgetLine::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BudgetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Budget ADD COLUMN exemptFromVat INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "budgets.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
