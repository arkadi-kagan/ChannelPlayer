package com.channelplayer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.channelplayer.cache.HistoryInfo;
import com.channelplayer.cache.HistoryViewModel;
import com.channelplayer.cache.HistoryViewModelFactory;
import com.channelplayer.cache.VideoItem;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class HistoryActivity extends AppCompatActivity implements VideoAdapter.OnVideoClickListener {

    private static final String TAG = "HistoryActivity";

    private VideoAdapter videoAdapter;
    private HistoryViewModel historyViewModel;
    private GoogleSignInAccount googleSignInAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reusing the existing layout as requested
        setContentView(R.layout.activity_select_video);

        // 1. Get the signed-in Google account, required for PlayerActivity
        googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (googleSignInAccount == null) {
            Log.e(TAG, "No Google account signed in. Cannot show history. Closing activity.");
            finish();
            return;
        }

        // 2. Setup UI components
        setupRecyclerView();

        // 3. Initialize ViewModel using a Factory
        HistoryViewModelFactory factory = new HistoryViewModelFactory(getApplication());
        historyViewModel = new ViewModelProvider(this, factory).get(HistoryViewModel.class);

        // 4. Observe LiveData for history changes and fetch the data
        observeHistoryList();
        historyViewModel.fetchHistory();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.video_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        // Reusing the existing VideoAdapter
        videoAdapter = new VideoAdapter(this);
        recyclerView.setAdapter(videoAdapter);
    }

    private void observeHistoryList() {
        historyViewModel.getHistoryVideos().observe(this, videoItems -> {
            if (videoItems != null) {
                Log.d(TAG, "Updating UI with " + videoItems.size() + " videos from history.");
                videoAdapter.submitList(videoItems);
            }
        });
    }

    /**
     * Handles clicks on items in the history list.
     * This implementation is copied from SelectVideoActivity and modified to include EXTRA_POSITION.
     * @param item The clicked VideoItem which represents a history entry.
     */
    @Override
    public void onVideoClick(VideoItem item) {
        // We need to find the original HistoryInfo to get the saved playback position.
        HistoryInfo historyInfo = historyViewModel.findHistoryInfoByVideoId(item.videoId);
        if (historyInfo == null) {
            Log.e(TAG, "Could not find HistoryInfo for videoId: " + item.videoId);
            // Fallback to 0 if not found, though this should not happen in a consistent DB.
            historyInfo = new HistoryInfo(0, item.videoId, 0);
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.videoId);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESCRIPTION, item.description);
        intent.putExtra(PlayerActivity.EXTRA_ACCOUNT_NAME, googleSignInAccount.getAccount().name);

        // 5. Add the new EXTRA_POSITION to the intent
        intent.putExtra(PlayerActivity.EXTRA_POSITION, historyInfo.position);

        // We don't need to listen for a result from PlayerActivity here.
        startActivity(intent);
    }
}
