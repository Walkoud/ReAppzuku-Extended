package com.gree1d.reappzuku.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Room DAO for resource snapshots used by CollectStatsManager.
 *
 * Key queries:
 *  - getLatestSnapshot()              → most recent snapshot (any package)
 *  - getClosestSnapshotBefore(time)   → snapshot closest to a target time (period boundary)
 *  - getSnapshotsBetween(t1, t2)      → all snapshots in a window (for period diffs)
 *  - getSnapshotsForPackageBetween    → per-app hourly graph data
 *  - updateRamForCycle(...)           → back-fill procstats RAM to all snapshots in a cycle
 *  - getAnySnapshotAfter(cutoff)      → slot guard: check if a recent snapshot already exists
 *  - deleteOlderThan(cutoff)          → prune old data (keep 24 hours)
 */
@Dao
public interface ResourceSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ResourceSnapshot snapshot);

    /** Returns the single most recent snapshot row (any package). */
    @Query("SELECT * FROM resource_snapshots ORDER BY timestamp DESC LIMIT 1")
    ResourceSnapshot getLatestSnapshot();

    /** Returns the oldest snapshot row (any package). Used as fallback when full history is unavailable. */
    @Query("SELECT * FROM resource_snapshots ORDER BY timestamp ASC LIMIT 1")
    ResourceSnapshot getOldestSnapshot();

    /**
     * Returns the snapshot whose timestamp is closest to but NOT AFTER the given time.
     * Used to find the "start boundary" of a period window.
     */
    @Query("SELECT * FROM resource_snapshots WHERE timestamp <= :beforeTime " +
           "ORDER BY timestamp DESC LIMIT 1")
    ResourceSnapshot getClosestSnapshotBefore(long beforeTime);

    /**
     * Returns all snapshots within a time window, ordered chronologically.
     * Used for per-period aggregation and per-hour graph data.
     */
    @Query("SELECT * FROM resource_snapshots " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime " +
           "ORDER BY packageName, timestamp ASC")
    List<ResourceSnapshot> getSnapshotsBetween(long startTime, long endTime);

    /**
     * Returns any snapshot collected after the given cutoff (any package).
     * Used as a slot guard: if a snapshot exists within the last SLOT_MS window,
     * the current snapshot is skipped to avoid duplicates.
     */
    @Query("SELECT * FROM resource_snapshots " +
           "WHERE timestamp >= :cutoff " +
           "ORDER BY timestamp DESC LIMIT 1")
    ResourceSnapshot getAnySnapshotAfter(long cutoff);

    /**
     * Back-fills ramMb from procstats to all snapshots in the given cycle window.
     * Called once per package after a successful procstats --hours 1 run on the cycle-end snapshot.
     *
     * @param ramMb      average PSS from procstats (MB)
     * @param pkg        package name
     * @param cycleStart timestamp of the first snapshot in this cycle (inclusive)
     * @param cycleEnd   timestamp of the cycle-end snapshot (inclusive)
     */
    @Query("UPDATE resource_snapshots " +
           "SET ramMb = :ramMb " +
           "WHERE packageName = :pkg " +
           "AND timestamp >= :cycleStart AND timestamp <= :cycleEnd")
    void updateRamForCycle(double ramMb, String pkg, long cycleStart, long cycleEnd);

    /** Returns the oldest snapshot for a specific package (any time). Used to determine
     *  how long data has been collected for this package, regardless of current window. */
    @Query("SELECT * FROM resource_snapshots WHERE packageName = :packageName " +
           "ORDER BY timestamp ASC LIMIT 1")
    ResourceSnapshot getOldestSnapshotForPackage(String packageName);

    /**
     * Returns all snapshots for a specific package within a time window.
     * Used for the per-app resource consumption graph.
     */
    @Query("SELECT * FROM resource_snapshots " +
           "WHERE packageName = :packageName " +
           "AND timestamp >= :startTime AND timestamp <= :endTime " +
           "ORDER BY timestamp ASC")
    List<ResourceSnapshot> getSnapshotsForPackageBetween(String packageName,
                                                          long startTime,
                                                          long endTime);

    /**
     * Returns distinct package names that appear in the given time window.
     * Used to build the spinner/selector list for per-app detail view.
     */
    @Query("SELECT DISTINCT packageName FROM resource_snapshots " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime " +
           "ORDER BY packageName ASC")
    List<String> getPackagesInWindow(long startTime, long endTime);

    /**
     * Deletes all snapshots older than the given cutoff timestamp.
     * Call periodically (e.g. after each snapshot) with cutoff = now - 24 hours.
     */
    @Query("DELETE FROM resource_snapshots WHERE timestamp < :cutoffTime")
    void deleteOlderThan(long cutoffTime);

    /** Returns the total number of stored snapshots (any package). Useful for diagnostics. */
    @Query("SELECT COUNT(*) FROM resource_snapshots")
    int countAll();
}
