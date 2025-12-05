package com.channelplayer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
import java.util.concurrent.Executors;

public class ChannelListActivity extends AppCompatActivity implements ChannelAdapter.OnChannelClickListener {

    private static final String TAG = "ChannelListActivity";
    private RecyclerView recyclerView;
    private ChannelAdapter channelAdapter;
    private List<ChannelAdapter.ChannelItem> channelItems;
    private YouTube youtube;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_list);

        recyclerView = findViewById(R.id.channel_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        channelItems = new ArrayList<>();
        channelAdapter = new ChannelAdapter(channelItems, this);
        recyclerView.setAdapter(channelAdapter);

        // Get the last signed-in account.
        GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (signedInAccount != null) {
            // Use the account to initialize the YouTube API client.
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this,
                    Collections.singleton("https://www.googleapis.com/auth/youtube.readonly"));
            credential.setSelectedAccount(signedInAccount.getAccount());

            youtube = new YouTube.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName(getString(R.string.app_name))
                    .build();

            // Load the channel data in a background thread.
            loadChannelData();
        } else {
            Log.e(TAG, "No Google account signed in.");
            // Here you might want to redirect back to the login screen.
        }
    }

    private String getChannelIdFromHandle(String handle) throws IOException {
        YouTube.Search.List request = youtube.search()
                .list(Collections.singletonList("snippet"));
        request.setQ(handle);
        request.setType(Collections.singletonList("channel"));
        request.setMaxResults(1L);

        SearchListResponse response = request.execute();
        List<SearchResult> items = response.getItems();

        if (items != null && !items.isEmpty()) {
            return items.get(0).getSnippet().getChannelId();
        }

        Log.w(TAG, "Could not find channel ID for handle: " + handle);
        return null;
    }

    private void loadChannelData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<String> channelHandles = readChannelHandlesFromFile();
            final List<String> channelIds = new ArrayList<>();

            // First, convert all handles to IDs.
            for (String handle : channelHandles) {
                try {
                    String channelId = getChannelIdFromHandle(handle);
                    if (channelId != null) {
                        channelIds.add(channelId);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error fetching channel ID for handle: " + handle, e);
                }
            }

            final List<ChannelAdapter.ChannelItem> fetchedItems = new ArrayList<>();
            // Now, use the fetched IDs to get channel details.
            for (String channelId : channelIds) {
                try {
                    // Create an API request to fetch channel details.
                    YouTube.Channels.List request = youtube.channels()
                            .list(java.util.Collections.singletonList("snippet"));
                    request.setId(java.util.Collections.singletonList(channelId));
                    ChannelListResponse response = request.execute();

                    if (response.getItems() != null && !response.getItems().isEmpty()) {
                        Channel channel = response.getItems().get(0);
                        String title = channel.getSnippet().getTitle();
                        String iconUrl = channel.getSnippet().getThumbnails().getDefault().getUrl();
                        fetchedItems.add(new ChannelAdapter.ChannelItem(channel.getId(), iconUrl, title));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error fetching channel data for ID: " + channelId, e);
                }
            }

            // Update the UI on the main thread with the fetched data.
            runOnUiThread(() -> {
                channelItems.clear();
                channelItems.addAll(fetchedItems);
                channelAdapter.notifyDataSetChanged();
            });
        });
    }

    private List<String> readChannelHandlesFromFile() {
        List<String> channelHandles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getResources().openRawResource(R.raw.channel_ids)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                channelHandles.add(line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading channel handles file", e);
        }
        return channelHandles;
    }

    @Override
    public void onChannelClick(ChannelAdapter.ChannelItem item) {
        Intent intent = new Intent(this, SelectVideoActivity.class);
        intent.putExtra("CHANNEL_ID", item.getChannelId());
        startActivity(intent);
    }
}
