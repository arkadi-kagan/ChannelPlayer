package com.channelplayer.cache;

import android.app.Application;

import androidx.annotation.NonNull;import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final ExecutorService executorService;
    private final MutableLiveData<List<VideoItem>> historyVideos = new MutableLiveData<>();
    private List<HistoryInfo> rawHistoryList; // To store original HistoryInfo objects

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getDatabase(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<VideoItem>> getHistoryVideos() {
        return historyVideos;
    }

    public void fetchHistory() {
        executorService.execute(() -> {
            // 1. Get all history items, sorted by timestamp descending
            rawHistoryList = db.historyDao().getAllHistory();

            if (rawHistoryList == null || rawHistoryList.isEmpty()) {
                historyVideos.postValue(new ArrayList<>());
                return;
            }

            List<VideoItem> sortedVideoItems = new ArrayList<>();
            for (HistoryInfo historyInfo : rawHistoryList) {
                VideoItem videoItem = db.videoDao().getVideoByIdSync(historyInfo.videoId);
                sortedVideoItems.add(videoItem);
            }
            historyVideos.postValue(sortedVideoItems);
        });
    }

    /**
     * Finds the original HistoryInfo object for a given videoId to retrieve metadata
     * like the saved position.
     * @param videoId The ID of the video to find.
     * @return The corresponding HistoryInfo object, or null if not found.
     */
    public HistoryInfo findHistoryInfoByVideoId(String videoId) {
        if (rawHistoryList != null && videoId != null) {
            for (HistoryInfo info : rawHistoryList) {
                if (videoId.equals(info.videoId)) {
                    return info;
                }
            }
        }
        return null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
