package com.channelplayer.cache;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import java.util.Set;

/**
 * Data Access Object for the ChannelInfo entity.
 */
@Dao
public interface ChannelDao {

    /**
     * Inserts a list of channels into the database, replacing any conflicts.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChannelInfo> channels);

    /**
     * Fetches all cached channels, ordered by title.
     * Returns LiveData to the UI for automatic updates.
     */
    @Query("SELECT * FROM channels ORDER BY title ASC")
    LiveData<List<ChannelInfo>> getAllChannels();

    @Query("SELECT * FROM channels ORDER BY title ASC")
    List<ChannelInfo> getAllChannelsSync();

    /**
     * Gets a single channel by its handle. This is a synchronous call for background use.
     * @param handle The channel handle (e.g., "@MrBeast").
     * @return The ChannelInfo object, or null if not found.
     */
    @Query("SELECT * FROM channels WHERE handle = :handle")
    ChannelInfo getChannelByHandleSync(String handle);

    @Query("DELETE FROM channels WHERE channelId IN (:channelsToRemove)")
    void deleteChannels(Set<String> channelsToRemove);
}
