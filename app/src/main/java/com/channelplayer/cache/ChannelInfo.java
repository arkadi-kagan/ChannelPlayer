package com.channelplayer.cache;

import androidx.annotation.NonNull;import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Defines the schema for the "channels" table.
 * This entity will cache basic information about YouTube channels.
 */
@Entity(tableName = "channels")
public class ChannelInfo {

    /**
     * The unique YouTube Channel ID (e.g., "UC_x5XG1OV2P6uZZ5FSM9Ttw").
     * This is the primary key.
     */
    @PrimaryKey
    @NonNull
    public String channelId;

    /**
     * The channel's handle (e.g., "@MrBeast"). This can be used for fetching updates.
     */
    public String handle;

    /**
     * The display title of the channel.
     */
    public String title;

    /**
     * The URL for the channel's default thumbnail image.
     */
    public String thumbnailUrl;

    /**
     * Timestamp for when this data was fetched, for potential cache invalidation.
     */
    public long fetchedAt;

    // A no-argument constructor is required by Room
    public ChannelInfo() {}
}
