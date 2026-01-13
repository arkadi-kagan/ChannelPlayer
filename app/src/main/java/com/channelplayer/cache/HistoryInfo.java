package com.channelplayer.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "history",
        indices = {@Index(value = "viewTimestamp", unique = false)}
)
public class HistoryInfo {
    public long viewTimestamp;

    @PrimaryKey
    @NonNull
    public String videoId;

    public int position;

    public HistoryInfo(long viewTimestamp, String videoId, int position) {
        this.viewTimestamp = viewTimestamp;
        this.videoId = videoId;
        this.position = position;
    }
}
