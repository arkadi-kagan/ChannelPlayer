package com.channelplayer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import com.channelplayer.cache.AppDatabase;
import com.channelplayer.cache.VideoItem;
import androidx.lifecycle.Observer;

import com.google.api.client.http.javanet.NetHttpTransport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class SelectVideoActivity extends AppCompatActivity implements VideoAdapter.OnVideoClickListener {

    private static final String TAG = "SelectVideoActivity";
    public static final String CHANNEL_ID = "CHANNEL_ID";

    private AppDatabase db;
    private RecyclerView recyclerView;
    private VideoAdapter videoAdapter;
    private YouTube youtube;
    private String channelId;
    private GoogleSignInAccount googleSignInAccount; // Declare the variable here

    private boolean isLoading = false;
    private String nextPageToken = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_video);

        channelId = getIntent().getStringExtra(CHANNEL_ID);
        if (channelId == null) {
            Log.e(TAG, "No channel ID found in intent");
            finish();
            return;
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView = findViewById(R.id.video_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);

        videoAdapter = new VideoAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(videoAdapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && nextPageToken != null) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0) {
                        loadVideos(nextPageToken);
                    }
                }
            }
        });

        // Initialize the database
        db = AppDatabase.getDatabase(this);

        // --- Observe the database for changes ---
        // This replaces the manual `videoAdapter.addItems()` calls.
        db.videoDao().getVideosForChannel(channelId).observe(this, videoItems -> {
            List<VideoAdapter.VideoItem> adapterItems = new ArrayList<>();
            for (VideoItem item : videoItems) {
                adapterItems.add(new VideoAdapter.VideoItem(item.videoId, item.title, item.thumbnailUrl));
            }
            videoAdapter.updateList(adapterItems); // Use a new method in adapter to replace the list
            isLoading = false;
        });

        SearchView searchView = findViewById(R.id.video_search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                videoAdapter.getFilter().filter(newText);
                return false;
            }
        });

        // Initialize the variable here
        googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (googleSignInAccount != null) {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this,
                    Collections.singleton("https://www.googleapis.com/auth/youtube.readonly"));
            credential.setSelectedAccount(googleSignInAccount.getAccount());

            youtube = new YouTube.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName(getString(R.string.app_name))
                    .build();

            loadVideos(null); // Load the first page
        } else {
            Log.e(TAG, "No Google account signed in.");
        }
    }

    private void loadVideos(final String pageToken) {
        isLoading = true;
        // Use the executor from AppDatabase to run operations off the main thread
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // If it's the first page, check if we should even fetch
                if (pageToken == null) {
                    int count = db.videoDao().getVideoCountForChannel(channelId);
                    if (count > 0) {
                        // Data exists. We are already observing it via LiveData.
                        // You could add logic here to fetch only if data is old.
                        Log.d(TAG, "Data already in DB. Skipping initial fetch.");
                        // To force a refresh anyway, just remove this if-block.
                        // The UI is already populated by the observer, so we can just return.
                        return;
                    }
                }

                YouTube.Search.List searchRequest = youtube.search()
                        .list(Collections.singletonList("snippet"));

                searchRequest.setChannelId(channelId);
                searchRequest.setType(Collections.singletonList("video"));
                searchRequest.setOrder("date"); // Newest first
                searchRequest.setMaxResults(20L); // Load 20 videos per page
                searchRequest.setPageToken(pageToken);

                SearchListResponse searchResponse = searchRequest.execute();
                nextPageToken = searchResponse.getNextPageToken();

                final List<VideoItem> fetchedItems = new ArrayList<>();
                for (SearchResult item : searchResponse.getItems()) {
                    fetchedItems.add(new VideoItem(
                            item.getId().getVideoId(),
                            channelId, // Store channelId with the item
                            item.getSnippet().getTitle(),
                            item.getSnippet().getThumbnails().getDefault().getUrl()
                    ));
                }

                // If first page, clear old data. Otherwise, append.
                if (pageToken == null) {
                    db.videoDao().deleteVideosForChannel(channelId);
                }
                db.videoDao().insertAll(fetchedItems);
                // The LiveData observer in onCreate will automatically update the UI

            } catch (IOException e) {
                Log.e(TAG, "Error loading videos: ", e);
                runOnUiThread(() -> isLoading = false);
            } catch (Exception e) { // Catch other potential exceptions
                Log.e(TAG, "An unexpected error occurred while loading videos: ", e);
                runOnUiThread(() -> isLoading = false);
            }
        });
    }

    @Override
    public void onVideoClick(VideoAdapter.VideoItem item) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.getVideoId());
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESCRIPTION, item.getTitle());
        // Now this line will work correctly
        intent.putExtra(PlayerActivity.EXTRA_ACCOUNT_NAME, googleSignInAccount.getAccount().name);
        startActivity(intent);
    }
}
