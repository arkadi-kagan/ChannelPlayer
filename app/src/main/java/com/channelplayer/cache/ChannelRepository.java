package com.channelplayer.cache;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.channelplayer.R;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChannelRepository {
    private static final String TAG = "ChannelRepository";
    private static final String USER_CHANNELS_FILENAME = "channel_handles.json";

    private final ChannelDao channelDao;
    private final YouTube youtubeService;
    private final Executor executor;
    private final Application application;
    private final ConfigRepository configRepository;

    public ChannelRepository(Application application, YouTube youtubeService, ConfigRepository configRepository) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.application = application;
        this.channelDao = db.channelDao();
        this.youtubeService = youtubeService;
        this.configRepository = configRepository;
        this.executor = Executors.newSingleThreadExecutor();

        executor.execute(this::setupUserChannelsFile);
    }

    /**
     * Checks if the user-editable channel handles file exists. If not, it creates it
     * by copying the content from the bundled resource file. This ensures that on the
     * first run, the user has a file to edit with the default channels.
     */
    private void setupUserChannelsFile() {
        File userFile = new File(application.getExternalFilesDir(null), USER_CHANNELS_FILENAME);
        if (userFile.exists()) {
            Log.d(TAG, "User channels file already exists. No action needed.");
            return; // File already exists, do nothing.
        }

        Log.d(TAG, "User channels file not found. Creating from resource...");
        // Copy the resource file content to the new user-editable file.
        try (InputStream inputStream = application.getResources().openRawResource(R.raw.channel_handles);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             FileOutputStream fileOutputStream = new FileOutputStream(userFile);
             OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream)) {

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
            Log.i(TAG, "Successfully created user channels file at: " + userFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to create user channels file from resource.", e);
            // If creation fails, we might want to delete the partial file to ensure a retry on next launch.
            if (userFile.exists()) {
                userFile.delete();
            }
        }
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
        List<ChannelInfo> channelsToInsert = new ArrayList<>();

        List<ChannelInfo> allChannels = channelDao.getAllChannelsSync();
        Set<String> channelsToRemove = new HashSet<>();
        for (ChannelInfo channel : allChannels) {
            channelsToRemove.add(channel.channelId);
        }

        for (String handle : configRepository.getChannelHandles()) {
            try {
                // Check if we already have this channel and its ID.
                ChannelInfo cached = channelDao.getChannelByHandleSync(handle);
                String channelId = (cached != null) ? cached.channelId : getChannelIdFromHandle(handle);

                if (channelId == null) {
                    Log.w(TAG, "Skipping handle with no discoverable channel ID: " + handle);
                    continue;
                }

                if (cached != null &&
                        cached.title != null && cached.title.length() > 0 &&
                        cached.thumbnailUrl != null) {
                    channelsToRemove.remove(cached.channelId);
                    channelsToInsert.add(cached);
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

                    if (cached == null) {
                        channelsToRemove.remove(info.channelId);
                    }
                    channelsToInsert.add(info);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to refresh channel data for handle: " + handle, e);
            }
        }

        // After fetching all, insert them into the database in one transaction.
        if (!channelsToInsert.isEmpty()) {
            channelDao.deleteChannels(channelsToRemove);
            channelDao.insertAll(channelsToInsert);
        }
    }

    private String getChannelIdFromHandle(String handle) throws IOException {
        // 1. Ensure the handle starts with '@' as required by the forHandle parameter
        String formattedHandle = handle.startsWith("@") ? handle : "@" + handle;

        // 2. Use the channels().list() method with the forHandle filter
        // This is much more accurate than search().list()
        YouTube.Channels.List request = youtubeService.channels().list(Collections.singletonList("id"));
        request.setForHandle(formattedHandle);

        ChannelListResponse response = request.execute();

        // 3. The response will contain the exact channel if it exists
        if (response.getItems() != null && !response.getItems().isEmpty()) {
            return response.getItems().get(0).getId();
        }

        // Fallback: If forHandle fails (rarely, e.g., for very old legacy handles),
        // you could keep the search logic, but for @cosmosprosto, forHandle is the correct way.
        return null;
    }
}
