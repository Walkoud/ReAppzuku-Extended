package com.gree1d.reappzuku.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;

import java.util.List;

@Entity(
    tableName = "sleep_mode_log",
    indices = {
        @Index("packageName"),
        @Index("timestamp")
    }
)
public class SleepModeLog {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public String packageName;
    /** "freeze" / "unfreeze" */
    public String action;
    /** "ok" / "error" */
    public String outcome;

    @androidx.room.Dao
    public interface Dao {

        @Insert
        void insert(SleepModeLog entry);

        @Query("SELECT * FROM sleep_mode_log ORDER BY timestamp DESC LIMIT :limit")
        List<SleepModeLog> getRecent(int limit);

        @Query("SELECT COUNT(*) FROM sleep_mode_log")
        int getCount();

        @Query("DELETE FROM sleep_mode_log WHERE id IN (" +
               "SELECT id FROM sleep_mode_log ORDER BY timestamp ASC LIMIT :count)")
        void deleteOldest(int count);

        @Query("DELETE FROM sleep_mode_log")
        void clearAll();
    }
}
