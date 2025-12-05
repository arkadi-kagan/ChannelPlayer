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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class SelectVideoActivity extends AppCompatActivity implements VideoAdapter.OnVideoClickListener {

    private static final String TAG = "SelectVideoActivity";
    public static final String CHANNEL_ID = "CHANNEL_ID";

    private RecyclerView recyclerView;
    private VideoAdapter videoAdapter;
    private YouTube youtube;
    private String channelId;

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

        GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (signedInAccount != null) {
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

            loadVideos(null); // Load the first page
        } else {
            Log.e(TAG, "No Google account signed in.");
        }
    }

    private void loadVideos(final String pageToken) {
        isLoading = true;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                YouTube.Search.List searchRequest = youtube.search()
                        .list(Collections.singletonList("snippet"));

                searchRequest.setChannelId(channelId);
                searchRequest.setType(Collections.singletonList("video"));
                searchRequest.setOrder("date"); // Newest first
                searchRequest.setMaxResults(20L); // Load 20 videos per page
                searchRequest.setPageToken(pageToken);

                SearchListResponse searchResponse = searchRequest.execute();
                nextPageToken = searchResponse.getNextPageToken();

                final List<VideoAdapter.VideoItem> fetchedItems = new ArrayList<>();
                for (SearchResult item : searchResponse.getItems()) {
                    String videoId = item.getId().getVideoId();
                    String title = item.getSnippet().getTitle();
                    String thumbnailUrl = item.getSnippet().getThumbnails().getDefault().getUrl();
                    fetchedItems.add(new VideoAdapter.VideoItem(videoId, title, thumbnailUrl));
                }

                runOnUiThread(() -> {
                    videoAdapter.addItems(fetchedItems);
                    isLoading = false;
                });

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
        startActivity(intent);
    }
}
