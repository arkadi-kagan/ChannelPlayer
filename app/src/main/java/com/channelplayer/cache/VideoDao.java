package com.channelplayer.cache;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<VideoItem> videos);

    // Using LiveData makes the UI reactive. The Activity will observe this.
    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY fetchedAt ASC")
    LiveData<List<VideoItem>> getVideosForChannel(String channelId);

    // To clear old videos before inserting new ones
    @Query("DELETE FROM videos WHERE channelId = :channelId")
    void deleteVideosForChannel(String channelId);

    @Query("SELECT COUNT(*) FROM videos WHERE channelId = :channelId")
    int getVideoCountForChannel(String channelId);
}
