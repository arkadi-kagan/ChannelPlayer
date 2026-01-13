package com.channelplayer.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HistoryInfo> allHistory);

    @Query("INSERT INTO history (viewTimestamp, videoId, position) VALUES (:viewTimestamp, :videoId, :position)")
    void insert(long viewTimestamp, String videoId, int position);

    @Query("SELECT * FROM history ORDER BY viewTimestamp DESC")
    List<HistoryInfo> getAllHistory();

    @Query("DELETE FROM history WHERE viewTimestamp < (SELECT MIN(viewTimestamp) FROM history ORDER BY viewTimestamp DESC LIMIT :limit)")
    void deleteOldHistory(int limit);

    @Query("SELECT * FROM history WHERE videoId = :videoId")
    HistoryInfo getVideoById(String videoId);

    @Query("UPDATE history SET viewTimestamp = :timestamp, position = :newProgress WHERE videoId = :videoId")
    void update(String videoId, long timestamp, int newProgress);
}
