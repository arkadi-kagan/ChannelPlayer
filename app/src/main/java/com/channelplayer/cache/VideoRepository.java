package com.channelplayer.cache;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class VideoRepository {
    private final YouTube youtubeService;
    private final VideoDao videoDao;
    private final Executor executor;
    private final SharedPreferences sharedPreferences;
    private final ConfigRepository configRepository;


    // Preference key for storing the next page token
    private static final String PREF_NEXT_PAGE_TOKEN = "next_page_token_";

    public VideoRepository(Application application, YouTube youtubeService, ConfigRepository configRepository) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.videoDao = db.videoDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.youtubeService = youtubeService;
        this.configRepository = configRepository;

        // Initialize SharedPreferences to store page tokens
        this.sharedPreferences = application.getSharedPreferences("VideoRepositoryPrefs", Context.MODE_PRIVATE);
    }

    /**
     * Gets the video list from the database.
     * If the database is empty for this channel, it fetches the FIRST page.
     */
    public LiveData<List<VideoItem>> getVideoList(String channelId) {
        // 1. Immediately return LiveData from the database.
        LiveData<List<VideoItem>> databaseData = videoDao.getVideosForChannel(channelId);

        // 2. Trigger a background task to refresh data only if the cache is empty.
        // Subsequent pages will be loaded explicitly by calling fetchNextVideoPage.
        executor.execute(() -> {
            int videoCount = videoDao.getVideoCountForChannel(channelId); // Add this method to your DAO
            if (videoCount == 0) {
                // Fetch the first page if the cache is empty
                fetchAndCacheVideos(channelId, null);
            }
        });

        return databaseData;
    }

    /**
     * Fetches the next page of videos for a given channel and adds them to the database.
     * This should be called when the user scrolls to the end of the list.
     */
    public void fetchNextVideoPage(String channelId) {
        executor.execute(() -> {
            // Get the token for the next page from SharedPreferences
            String nextPageToken = sharedPreferences.getString(PREF_NEXT_PAGE_TOKEN + channelId, null);

            // If the token is null, it means we've either not loaded any pages yet or we've reached the end.
            // If it's an empty string, the API has told us there are no more pages. Don't proceed.
            if (nextPageToken == null || nextPageToken.isEmpty()) {
                // If the token is null and the DB is empty, maybe fetch the first page.
                int videoCount = videoDao.getVideoCountForChannel(channelId);
                if (videoCount == 0 && nextPageToken == null) {
                    fetchAndCacheVideos(channelId, null);
                }
                return; // Otherwise, do nothing
            }

            fetchAndCacheVideos(channelId, nextPageToken);
        });
    }

    /**
     * Private helper method to perform the network request and cache the results.
     */
    private void fetchAndCacheVideos(String channelId, String pageToken) {
        try {
            YouTube.Search.List request = youtubeService.search()
                    .list(Collections.singletonList("snippet"))
                    .setChannelId(channelId)
                    .setType(Collections.singletonList("video"))
                    .setOrder("date")
                    .setMaxResults(50L); // Number of videos per page

            // If we have a page token, use it to get the next page
            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            SearchListResponse response = request.execute();

            if (response != null) {
                List<VideoItem> freshVideos = new ArrayList<>();
                Map<String, String> banned_video = configRepository.getBannedVideos();
                for (SearchResult item : response.getItems()) {
                    if (banned_video.containsKey(item.getId().getVideoId()))
                        continue;
                    freshVideos.add(new VideoItem(
                            item.getId().getVideoId(),
                            channelId, // Store channelId with the item
                            item.getSnippet().getTitle(),
                            item.getSnippet().getThumbnails().getDefault().getUrl(),
                            item.getSnippet().getPublishedAt().getValue()
                    ));
                }

                // Insert the new videos. This will automatically update the LiveData.
                videoDao.insertAll(freshVideos);

                // Get the token for the *next* page and save it.
                // It will be null if this is the last page.
                String nextToken = response.getNextPageToken();
                sharedPreferences.edit()
                        .putString(PREF_NEXT_PAGE_TOKEN + channelId, nextToken)
                        .apply();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void fetchInitialVideos(String channelId) {
        // You can reuse the existing fetch logic or create a specific one
        // that checks if the cache is empty or stale before fetching.
        // For simplicity, we can just call the next page fetch logic.
        fetchNextVideoPage(channelId);
    }

    public void banVideo(String videoId) {
        executor.execute(() -> {
            VideoItem item = videoDao.getVideoByIdSync(videoId);
            if (item == null)
                return;
            configRepository.banVideo(videoId, item.description);
            videoDao.deleteSingleVideo(videoId);
        });
    }
}
