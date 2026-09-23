package com.example.poultryscanfinal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Version history:
 *   1 - users only
 *   2 - adds the scan_history table (see MIGRATION_1_2)
 *
 * The migration is additive, so existing registered users survive the upgrade.
 * fallbackToDestructiveMigration() is deliberately NOT used.
 */
@Database(entities = {User.class, ScanHistory.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public abstract ScanHistoryDao scanHistoryDao();

    /**
     * Adds scan_history. The column definitions must match exactly what Room
     * generates for the entity, otherwise Room throws an identity-hash /
     * schema-mismatch error on first open.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_history` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`user_id` INTEGER NOT NULL, "
                            + "`disease_key` TEXT, "
                            + "`disease_name` TEXT, "
                            + "`confidence` REAL NOT NULL, "
                            + "`image_path` TEXT, "
                            + "`timestamp` INTEGER NOT NULL)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scan_history_user_id` "
                            + "ON `scan_history` (`user_id`)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "poultryscan_local.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
