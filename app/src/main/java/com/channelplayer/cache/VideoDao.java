package com.channelplayer.cache;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for the VideoItem entity.
 * Defines the database interactions for the "videos" table.
 */
@Dao
public interface VideoDao {

    /**
     * Inserts a list of videos into the database. If a video already exists, it's replaced.
     * @param videos The list of videos to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<VideoItem> videos);

    /**
     * Gets all videos for a specific channel, ordered by when they were fetched.
     * Returns LiveData to allow for reactive UI updates.
     * @param channelId The ID of the channel.
     * @return A LiveData list of videos.
     */
    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY fetchedAt ASC")
    LiveData<List<VideoItem>> getVideosForChannel(String channelId);

    /**
     * Gets a single video by its ID.
     * @param videoId The unique ID of the video.
     * @return A LiveData-wrapped video item.
     */
    @Query("SELECT * FROM videos WHERE videoId = :videoId")
    LiveData<VideoItem> getVideoById(String videoId);

    /**
     * Synchronous version of getVideoById for use in background threads.
     * @param videoId The unique ID of the video.
     * @return The VideoItem object, or null if not found.
     */
    @Query("SELECT * FROM videos WHERE videoId = :videoId")
    VideoItem getVideoByIdSync(String videoId);

    /**
     * Deletes all videos belonging to a specific channel. Useful for a "refresh" action.
     * @param channelId The ID of the channel whose videos will be deleted.
     */
    @Query("DELETE FROM videos WHERE channelId = :channelId")
    void deleteVideosForChannel(String channelId);

    /**
     * Counts the number of videos cached for a specific channel.
     * @param channelId The ID of the channel.
     * @return The total count of videos.
     */
    @Query("SELECT COUNT(videoId) FROM videos WHERE channelId = :channelId")
    int getVideoCountForChannel(String channelId);

    /**
     * Updates the description of a specific video, identified by its ID.
     * @param videoId The ID of the video to update.
     * @param description The new description text.
     */
    @Query("UPDATE videos SET description = :description WHERE videoId = :videoId")
    void updateDescription(String videoId, String description);
}
