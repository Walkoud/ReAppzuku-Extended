package com.gree1d.reappzuku.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;

import java.util.List;

@Entity(
    tableName = "scheduler_log",
    indices = {
        @Index("packageName"),
        @Index("timestamp")
    }
)
public class SchedulerLog {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public String packageName;
    /** "lift" — снятие ограничений, "restore" — восстановление. */
    public String action;
    /** "ok" / "error" / "skipped" / "denied" */
    public String outcome;
    /** "lift" → "action=launch"/"action=none"; "restore" → "stop=force-stop"/"stop=am-kill" */
    public String detail;

    @androidx.room.Dao
    public interface Dao {

        @Insert
        void insert(SchedulerLog entry);

        @Query("SELECT * FROM scheduler_log ORDER BY timestamp DESC LIMIT :limit")
        List<SchedulerLog> getRecent(int limit);

        @Query("SELECT * FROM scheduler_log WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT :limit")
        List<SchedulerLog> getByPackage(String packageName, int limit);

        @Query("SELECT COUNT(*) FROM scheduler_log")
        int getCount();

        @Query("DELETE FROM scheduler_log WHERE id IN (" +
               "SELECT id FROM scheduler_log ORDER BY timestamp ASC LIMIT :count)")
        void deleteOldest(int count);

        @Query("DELETE FROM scheduler_log")
        void clearAll();
    }
}
