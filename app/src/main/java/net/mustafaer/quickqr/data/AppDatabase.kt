package net.mustafaer.quickqr.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Scan history store.
 *
 * The schema is exported to `app/schemas/` and committed, so every version bump
 * is a real, verified migration. There is deliberately no destructive fallback
 * here: a missing migration must fail loudly during development rather than
 * silently erase the history of everyone who already installed the app.
 *
 * Version 2 adds the `pinned` column. It only adds a column with a default, so
 * Room generates the migration from the two committed schemas.
 */
@Database(
    entities = [ScanEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    companion object {
        const val DATABASE_NAME = "quickqr_database"

        /** Newest-first cap on stored scans that are not pinned. */
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
