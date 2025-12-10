package com.channelplayer.cache;

import android.app.Application;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.google.api.services.youtube.YouTube;

import java.util.List;

// VideoViewModel.java
public class VideoViewModel extends AndroidViewModel {
    private final VideoRepository repository;

    public VideoViewModel(@NonNull Application application, @NonNull YouTube youtubeService) {
        super(application);
        repository = new VideoRepository(application, youtubeService);
    }

    public LiveData<List<VideoItem>> getVideoListForChannel(String channelId) {
        return repository.getVideoList(channelId);
    }

    public LiveData<VideoItem> getVideoDetails(String videoId) {
        return repository.getVideoDetails(videoId);
    }

    public void fetchNextPage(String channelId) {
        repository.fetchNextVideoPage(channelId);
    }
}
