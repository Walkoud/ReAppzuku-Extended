package com.gree1d.reappzuku.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;

import java.util.List;

@Entity(
    tableName = "bg_restriction_log",
    indices = {
        @Index("packageName"),
        @Index("timestamp")
    }
)
public class BgRestrictionLog {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public String packageName;
    public String action;
    public String outcome;
    public String detail;

    @androidx.room.Dao
    public interface Dao {

        @Insert
        void insert(BgRestrictionLog entry);

        @Query("SELECT * FROM bg_restriction_log ORDER BY timestamp DESC LIMIT :limit")
        List<BgRestrictionLog> getRecent(int limit);

        @Query("SELECT * FROM bg_restriction_log WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT :limit")
        List<BgRestrictionLog> getByPackage(String packageName, int limit);

        @Query("SELECT COUNT(*) FROM bg_restriction_log")
        int getCount();

        @Query("DELETE FROM bg_restriction_log WHERE id IN (" +
               "SELECT id FROM bg_restriction_log ORDER BY timestamp ASC LIMIT :count)")
        void deleteOldest(int count);

        @Query("DELETE FROM bg_restriction_log")
        void clearAll();
    }
}
