package net.mustafaer.quickqr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Scan history store.
 *
 * The schema is exported to `app/schemas/` and committed, so any future version
 * bump can be written as a real [androidx.room.migration.Migration] and verified
 * against the shipped schema. There is deliberately no destructive fallback here:
 * a missing migration must fail loudly during development rather than silently
 * erase the history of everyone who already installed the app.
 */
@Database(entities = [ScanEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    companion object {
        const val DATABASE_NAME = "quickqr_database"

        /** Newest-first cap on stored scans. */
        const val HISTORY_LIMIT = 100

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
