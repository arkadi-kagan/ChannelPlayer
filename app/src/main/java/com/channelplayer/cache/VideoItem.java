package com.channelplayer.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "videos")public class VideoItem {

    @PrimaryKey
    @NonNull
    public final String videoId;

    @NonNull
    public final String channelId;

    public final String title;
    public final String thumbnailUrl;
    public long fetchedAt; // To track when the data was fetched

    public VideoItem(@NonNull String videoId, @NonNull String channelId, String title, String thumbnailUrl) {
        this.videoId = videoId;
        this.channelId = channelId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.fetchedAt = System.currentTimeMillis();
    }
}
