package com.channelplayer.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.api.client.util.DateTime;

/**
 * Defines the schema for the "videos" table in the Room database.
 */
@Entity(tableName = "videos")
public class VideoItem {

    /**
     * The unique YouTube ID for the video. This is the primary key for the table.
     */
    @PrimaryKey
    @NonNull
    public String videoId;

    /**
     * The ID of the YouTube channel this video belongs to.
     */
    @NonNull
    public String channelId;

    /**
     * The description (or title) of the video.
     */
    public String description;

    /**
     * The URL for the video's thumbnail image.
     */
    public String thumbnailUrl;

    /**
     * A timestamp indicating when this item was added to the cache, used for ordering.
     */
    public long fetchedAt;

    /**
     * Default constructor required for Room.
     */
    public VideoItem() {
    }


    /**
     * Constructor to create a new VideoItem.
     * @param videoId The video's unique ID.
     * @param channelId The ID of the video's channel.
     * @param description The video's title/description.
     * @param thumbnailUrl The URL of the video's thumbnail.
     */
    public VideoItem(
            @NonNull String videoId,
            @NonNull String channelId,
            String description,
            String thumbnailUrl,
            long publishedAt
    ) {
        this.videoId = videoId;
        this.channelId = channelId;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.fetchedAt = publishedAt;
    }
}
