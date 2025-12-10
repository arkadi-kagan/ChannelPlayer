package com.channelplayer.cache;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChannelRepository {
    private static final String TAG = "ChannelRepository";

    private final ChannelDao channelDao;
    private final YouTube youtubeService;
    private final Executor executor;
    private final Application application;

    public ChannelRepository(Application application, YouTube youtubeService) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.application = application;
        this.channelDao = db.channelDao();
        this.youtubeService = youtubeService;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Returns a LiveData list of all channels from the database.
     * It also triggers a background refresh to ensure data is up-to-date.
     */
    public LiveData<List<ChannelInfo>> getAllChannels() {
        // Immediately return the LiveData from the database.
        LiveData<List<ChannelInfo>> cachedChannels = channelDao.getAllChannels();

        // Start a background task to refresh the channel data from the network.
        executor.execute(this::refreshChannelsFromNetwork);

        return cachedChannels;
    }

    private void refreshChannelsFromNetwork() {
        List<String> handles = readChannelHandlesFromFile();
        List<ChannelInfo> channelsToInsert = new ArrayList<>();

        for (String handle : handles) {
            try {
                // Check if we already have this channel and its ID.
                ChannelInfo cached = channelDao.getChannelByHandleSync(handle);
                String channelId = (cached != null) ? cached.channelId : getChannelIdFromHandle(handle);

                if (channelId == null) {
                    Log.w(TAG, "Skipping handle with no discoverable channel ID: " + handle);
                    continue;
                }

                // Fetch full channel details using the ID
                YouTube.Channels.List request = youtubeService.channels().list(Collections.singletonList("snippet"));
                request.setId(Collections.singletonList(channelId));
                ChannelListResponse response = request.execute();

                if (response.getItems() != null && !response.getItems().isEmpty()) {
                    Channel channel = response.getItems().get(0);

                    // Create ChannelInfo object to cache
                    ChannelInfo info = new ChannelInfo();
                    info.channelId = channel.getId();
                    info.handle = handle; // Store the original handle
                    info.title = channel.getSnippet().getTitle();
                    info.thumbnailUrl = channel.getSnippet().getThumbnails().getDefault().getUrl();
                    info.fetchedAt = System.currentTimeMillis();

                    channelsToInsert.add(info);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to refresh channel data for handle: " + handle, e);
            }
        }

        // After fetching all, insert them into the database in one transaction.
        if (!channelsToInsert.isEmpty()) {
            channelDao.insertAll(channelsToInsert);
        }
    }

    // Helper methods moved from the Activity
    private String getChannelIdFromHandle(String handle) throws IOException {
        YouTube.Search.List request = youtubeService.search().list(Collections.singletonList("snippet"));
        request.setQ(handle).setType(Collections.singletonList("channel")).setMaxResults(1L);
        SearchListResponse response = request.execute();
        if (response.getItems() != null && !response.getItems().isEmpty()) {
            return response.getItems().get(0).getSnippet().getChannelId();
        }
        return null;
    }

    private List<String> readChannelHandlesFromFile() {
        List<String> channelHandles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(application.getResources().openRawResource(com.channelplayer.R.raw.channel_ids)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                channelHandles.add(line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading channel handles file", e);
        }
        return channelHandles;
    }
}
