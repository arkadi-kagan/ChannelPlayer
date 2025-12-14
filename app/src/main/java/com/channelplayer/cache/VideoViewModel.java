package com.channelplayer.cache;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.api.services.youtube.YouTube;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// VideoViewModel.java
public class VideoViewModel extends AndroidViewModel {
    private final VideoRepository repository;
    private final MediatorLiveData<List<VideoItem>> filteredVideos = new MediatorLiveData<>();
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private LiveData<List<VideoItem>> sourceVideos = null;


    public VideoViewModel(@NonNull Application application, @NonNull YouTube youtubeService) {
        super(application);
        repository = new VideoRepository(application, youtubeService);
    }

    public LiveData<List<VideoItem>> getFilteredVideoList(String channelId) {
        if (sourceVideos == null) {
            sourceVideos = repository.getVideoList(channelId);

            filteredVideos.addSource(sourceVideos, videos -> {
                filter(videos, searchQuery.getValue());
            });

            filteredVideos.addSource(searchQuery, query -> {
                filter(sourceVideos.getValue(), query);
            });
        }
        return filteredVideos;
    }

    private void filter(List<VideoItem> videos, String query) {
        if (videos == null) {
            filteredVideos.setValue(new ArrayList<>());
            return;
        }

        if (TextUtils.isEmpty(query)) {
            filteredVideos.setValue(videos);
        } else {
            List<VideoItem> filtered = videos.stream()
                    .filter(video -> video.description.toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList());
            filteredVideos.setValue(filtered);
        }
    }

    public void fetchNextPage(String channelId) {
        repository.fetchNextVideoPage(channelId);
    }

    public void fetchInitialVideos(String channelId) {
        repository.fetchInitialVideos(channelId);
    }
}
