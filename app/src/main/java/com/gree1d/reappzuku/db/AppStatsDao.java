package com.gree1d.reappzuku.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppStats stats);

    @Query("SELECT * FROM app_stats WHERE packageName = :packageName ORDER BY lastKillTime DESC LIMIT 1")
    AppStats getLatestByPackage(String packageName);

    @Query("SELECT packageName, appName, " +
           "COUNT(*) AS killCount, " +
           "SUM(relaunchCount) AS relaunchCount, " +
           "SUM(totalRecoveredKb) AS totalRecoveredKb, " +
           "MAX(lastKillTime) AS lastKillTime, " +
           "MAX(lastRelaunchTime) AS lastRelaunchTime, " +
           "MAX(lastKillSource) AS lastKillSource " +
           "FROM app_stats " +
           "WHERE lastKillTime > :sinceTime " +
           "GROUP BY packageName " +
           "ORDER BY killCount DESC")
    List<AppStatsAggregate> getAllStatsSince(long sinceTime);

    @Query("SELECT packageName, appName, " +
           "COUNT(*) AS killCount, " +
           "SUM(relaunchCount) AS relaunchCount, " +
           "SUM(totalRecoveredKb) AS totalRecoveredKb, " +
           "MAX(lastKillTime) AS lastKillTime, " +
           "MAX(lastRelaunchTime) AS lastRelaunchTime, " +
           "MAX(lastKillSource) AS lastKillSource " +
           "FROM app_stats " +
           "GROUP BY packageName " +
           "ORDER BY killCount DESC")
    List<AppStatsAggregate> getAllStats();

    @Query("UPDATE app_stats SET " +
           "relaunchCount = relaunchCount + 1, " +
           "lastRelaunchTime = :time " +
           "WHERE id = (" +
               "SELECT id FROM app_stats " +
               "WHERE packageName = :packageName " +
               "ORDER BY lastKillTime DESC LIMIT 1" +
           ")")
    void incrementRelaunch(String packageName, long time);

    @Query("UPDATE app_stats SET totalRecoveredKb = totalRecoveredKb + :recoveredKb " +
           "WHERE id = (" +
               "SELECT id FROM app_stats " +
               "WHERE packageName = :packageName " +
               "ORDER BY lastKillTime DESC LIMIT 1" +
           ")")
    void addRecoveredKb(String packageName, long recoveredKb);

    @Query("UPDATE app_stats SET appName = :appName " +
           "WHERE packageName = :packageName AND (appName IS NULL OR appName = '')")
    void updateAppName(String packageName, String appName);

    @Query("SELECT COUNT(*) FROM app_stats")
    int getCount();


    @Query("SELECT * FROM app_stats " +
           "WHERE packageName = :packageName AND lastKillTime > :sinceTime " +
           "ORDER BY lastKillTime DESC")
    List<AppStats> getKillsSince(String packageName, long sinceTime);

    @Query("DELETE FROM app_stats WHERE id IN (" +
           "SELECT id FROM app_stats ORDER BY lastKillTime ASC LIMIT :deleteCount)")
    void deleteOldestStats(int deleteCount);

    @Query("DELETE FROM app_stats WHERE lastKillTime >= :sinceTime")
    void deleteStatsSince(long sinceTime);

    @Query("DELETE FROM app_stats")
    void deleteAll();
}
