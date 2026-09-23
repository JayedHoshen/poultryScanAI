package com.example.poultryscanfinal;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Every read is scoped to a user id. There is deliberately no
 * "SELECT * FROM scan_history" method, so one user's history can never leak
 * into another user's screen.
 */
@Dao
public interface ScanHistoryDao {

    @Insert
    long insertScan(ScanHistory scan);

    @Query("SELECT * FROM scan_history WHERE user_id = :userId ORDER BY timestamp DESC")
    List<ScanHistory> getUserHistory(long userId);

    @Query("SELECT * FROM scan_history WHERE id = :id AND user_id = :userId LIMIT 1")
    ScanHistory getScan(long id, long userId);

    @Query("SELECT COUNT(*) FROM scan_history WHERE user_id = :userId")
    int countForUser(long userId);

    @Delete
    void deleteScan(ScanHistory scan);
}
