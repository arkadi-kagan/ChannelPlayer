package com.channelplayer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.channelplayer.cache.ConfigRepository;
import com.channelplayer.cache.VideoItem;
import com.channelplayer.cache.VideoViewModel;
import com.channelplayer.cache.VideoViewModelFactory;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;

import java.util.Collections;

public class SelectVideoActivity extends AppCompatActivity implements VideoAdapter.OnVideoClickListener {

    private static final String TAG = "SelectVideoActivity";
    public static final String CHANNEL_ID = "CHANNEL_ID";

    private VideoAdapter videoAdapter;
    private VideoViewModel videoViewModel;
    private String channelId;
    private GoogleSignInAccount googleSignInAccount;
    public ConfigRepository configRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_video);

        configRepository = ConfigRepository.getInstance(this, () -> {
            Log.i(TAG, "Config loaded successfully.");
        });

        channelId = getIntent().getStringExtra(CHANNEL_ID);
        if (channelId == null || channelId.isEmpty()) {
            Log.e(TAG, "No channel ID found in intent. Closing activity.");
            finish();
            return;
        }

        googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (googleSignInAccount == null) {
            Log.e(TAG, "No Google account signed in. Closing activity.");
            finish();
            return;
        }

        // 1. Setup UI components (RecyclerView and SearchView)
        setupRecyclerView();
        setupSearchView();

        // 2. Create the YouTube service instance
        YouTube youtubeService = createYouTubeService(googleSignInAccount);

        // 3. Initialize ViewModel using the Factory to pass dependencies
        VideoViewModelFactory factory = new VideoViewModelFactory(getApplication(), youtubeService, configRepository);
        videoViewModel = new ViewModelProvider(this, factory).get(VideoViewModel.class);

        // 4. Observe LiveData for video list changes
        videoViewModel.fetchInitialVideos(channelId);
        observeVideoList();
    }

    private void observeVideoList() {
        videoViewModel.getFilteredVideoList(channelId).observe(this, videos -> {
            // This block now receives a List<Video> named 'videos'.
            if (videos != null) {
                Log.d(TAG, "Updating UI with " + videos.size() + " videos from cache.");
                videoAdapter.submitList(videos);
            }
        });
    }
    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.video_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        // The adapter should now extend ListAdapter
        videoAdapter = new VideoAdapter(this);
        recyclerView.setAdapter(videoAdapter);

        // Add scroll listener for pagination
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition();

                // Load next page when user is near the end of the list
                if (lastVisibleItemPosition >= totalItemCount - 5) {
                    videoViewModel.fetchNextPage(channelId);
                }
            }
        });
    }

    private void setupSearchView() {
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
    }

    private YouTube createYouTubeService(GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this,
                Collections.singleton("https://www.googleapis.com/auth/youtube.readonly"));
        credential.setSelectedAccount(account.getAccount());

        return new YouTube.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName(getString(R.string.app_name))
                .build();
    }

    // Define the launcher as a member variable
    private final ActivityResultLauncher<Intent> playerActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    boolean isBanned = result.getData().getBooleanExtra("VIDEO_BANNED", false);
                    String videoId = result.getData().getStringExtra("VIDEO_ID");

                    if (isBanned && videoId != null) {
                        Log.d(TAG, "Video with ID " + videoId + " is banned.");
                        videoViewModel.banVideo(videoId);
                    }
                }
            }
    );

    @Override
    public void onVideoClick(VideoItem item) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.videoId);
        // Pass the description from the clicked item.
        // It will be fetched by the player if it's not already in the cache.
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESCRIPTION, item.description);
        intent.putExtra(PlayerActivity.EXTRA_ACCOUNT_NAME, googleSignInAccount.getAccount().name);
        playerActivityResultLauncher.launch(intent);
    }
}
