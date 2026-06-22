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
 *  - getSnapshotsBetween(t1, t2)      → all snapshots in a window (for hourly diffs)
 *  - getSnapshotsForPackageBetween    → per-app hourly graph data
 *  - getSnapshotsInHour(t1, t2)       → all snapshots within a completed hour (for procstats update)
 *  - updateRamFields(...)             → update RAM fields after procstats run
 *  - getSlotSnapshot(pkg, t1, t2)     → check whether a 15-min slot already has a snapshot
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
     * Returns all snapshots within a completed hour window (for procstats RAM update).
     * Ordered by timestamp ascending for correct diff calculation.
     */
    @Query("SELECT * FROM resource_snapshots " +
           "WHERE timestamp >= :hourStart AND timestamp < :hourEnd " +
           "ORDER BY timestamp ASC")
    List<ResourceSnapshot> getSnapshotsInHour(long hourStart, long hourEnd);

    /**
     * Returns the distinct package names that have at least one snapshot in the given hour.
     * Used to iterate packages when applying procstats RAM values.
     */
    @Query("SELECT DISTINCT packageName FROM resource_snapshots " +
           "WHERE timestamp >= :hourStart AND timestamp < :hourEnd")
    List<String> getPackagesInHour(long hourStart, long hourEnd);

    /**
     * Updates only the RAM fields (ramMb, minRamMb, maxRamMb) and clears the isTemporary flag
     * for all snapshots of the given package within the given hour.
     * CPU, battery, and all other fields are left untouched.
     * Called once per package after a successful procstats --hours 1 run.
     *
     * @param avgRamMb  average PSS from procstats (replaces temporary meminfo value in ramMb)
     * @param minRamMb  minimum PSS from procstats
     * @param maxRamMb  peak PSS from procstats
     * @param pkg       package name
     * @param hourStart start of the completed hour (inclusive)
     * @param hourEnd   end of the completed hour (exclusive, == hourStart + 3600_000)
     */
    @Query("UPDATE resource_snapshots " +
           "SET ramMb = :avgRamMb, minRamMb = :minRamMb, maxRamMb = :maxRamMb, isTemporary = 0 " +
           "WHERE packageName = :pkg " +
           "AND timestamp >= :hourStart AND timestamp < :hourEnd")
    void updateRamFields(double avgRamMb, double minRamMb, double maxRamMb,
                         String pkg, long hourStart, long hourEnd);

    /**
     * Checks whether a snapshot for the given package already exists within a 15-minute slot.
     * The slot is defined by [slotStart, slotEnd). Returns the row or null.
     * Used to avoid duplicate entries for the same 15-min slot.
     */
    @Query("SELECT * FROM resource_snapshots " +
           "WHERE packageName = :packageName " +
           "AND timestamp >= :slotStart AND timestamp < :slotEnd " +
           "ORDER BY timestamp DESC LIMIT 1")
    ResourceSnapshot getSlotSnapshot(String packageName, long slotStart, long slotEnd);

    /**
     * Returns the most recent snapshot for any package within the given slot window.
     * Used to check whether the current 15-min slot has already been collected (slot guard).
     */
    @Query("SELECT * FROM resource_snapshots " +
           "WHERE timestamp >= :slotStart AND timestamp < :slotEnd " +
           "ORDER BY timestamp DESC LIMIT 1")
    ResourceSnapshot getAnySnapshotInSlot(long slotStart, long slotEnd);

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
