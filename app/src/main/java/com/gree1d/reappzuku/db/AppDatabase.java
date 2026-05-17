package com.gree1d.reappzuku.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

@Database(
    entities = {
        AppStats.class,
        ResourceSnapshot.class,
        BgRestrictionLog.class,
        SchedulerLog.class,
        SleepModeLog.class
    },
    version = 8,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_stats ADD COLUMN totalRecoveredKb INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `resource_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `packageName` TEXT, `batteryMah` REAL NOT NULL, `ramMb` REAL NOT NULL, `cpuTimeMs` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_resource_snapshots_packageName_timestamp` ON `resource_snapshots` (`packageName`, `timestamp`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_resource_snapshots_timestamp` ON `resource_snapshots` (`timestamp`)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN totalCpuJiffies INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN activeCpuJiffies INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN batteryLevelPct INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN totalRawPwiBatch REAL NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `bg_restriction_log` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`timestamp` INTEGER NOT NULL, " +
                       "`packageName` TEXT, " +
                       "`action` TEXT, " +
                       "`outcome` TEXT, " +
                       "`detail` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bg_restriction_log_packageName` ON `bg_restriction_log` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bg_restriction_log_timestamp` ON `bg_restriction_log` (`timestamp`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `scheduler_log` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`timestamp` INTEGER NOT NULL, " +
                       "`packageName` TEXT, " +
                       "`action` TEXT, " +
                       "`outcome` TEXT, " +
                       "`detail` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduler_log_packageName` ON `scheduler_log` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduler_log_timestamp` ON `scheduler_log` (`timestamp`)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_mode_log` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`timestamp` INTEGER NOT NULL, " +
                       "`packageName` TEXT, " +
                       "`action` TEXT, " +
                       "`outcome` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_mode_log_packageName` ON `sleep_mode_log` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_mode_log_timestamp` ON `sleep_mode_log` (`timestamp`)");
        }
    };

    public abstract AppStatsDao appStatsDao();
    public abstract ResourceSnapshotDao resourceSnapshotDao();
    public abstract BgRestrictionLog.Dao bgRestrictionLogDao();
    public abstract SchedulerLog.Dao schedulerLogDao();
    public abstract SleepModeLog.Dao sleepModeLogDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "appzuku_db")
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
