package com.channelplayer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.channelplayer.cache.ChannelInfo;
import com.channelplayer.cache.ChannelViewModel;
import com.channelplayer.cache.ChannelViewModelFactory;
import com.channelplayer.cache.ConfigRepository;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;

import java.util.Collections;

public class ChannelListActivity extends AppCompatActivity implements ChannelAdapter.OnChannelClickListener {

    private static final String TAG = "ChannelListActivity";
    private ChannelAdapter channelAdapter;
    private ChannelViewModel channelViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_list);

        ConfigRepository configRepository = ConfigRepository.getInstance(this, () -> {
            Log.i(TAG, "Config loaded successfully.");
        });

        // 1. Setup RecyclerView and Adapter
        setupRecyclerView();
        setupSearchView();

        // 2. Authenticate and Initialize ViewModel
        GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (signedInAccount != null) {
            // This is the ONLY place we create the YouTube service instance.
            // It will be passed down to the repository via the ViewModel.
            YouTube youtubeService = createYouTubeService(signedInAccount);

            // 3. Use a ViewModelFactory to pass the youtubeService to the ViewModel
            ChannelViewModelFactory factory = new ChannelViewModelFactory(getApplication(), youtubeService, configRepository);
            channelViewModel = new ViewModelProvider(this, factory).get(ChannelViewModel.class);

            // 4. Observe the LiveData from the ViewModel
            observeChannelData();

        } else {
            Log.e(TAG, "No Google account signed in. Redirecting to login.");
            // Handle not being signed in (e.g., go back to MainActivity)
            finish();
        }
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.channel_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Initialize the adapter (it's empty at first) and set the click listener
        channelAdapter = new ChannelAdapter(this);
        recyclerView.setAdapter(channelAdapter);
    }

    private void setupSearchView() {
        SearchView searchView = findViewById(R.id.channel_search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                channelAdapter.getFilter().filter(newText);
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

    private void observeChannelData() {
        // The ViewModel gets data from the repository, which handles all caching and networking.
        channelViewModel.getAllChannels().observe(this, channelInfoList -> {
            // This block is executed automatically whenever the channel data in the
            // Room database changes (either from cache or a network refresh).
            if (channelInfoList != null) {
                Log.d(TAG, "UI updated with " + channelInfoList.size() + " channels from cache.");
                // The ListAdapter will efficiently handle displaying the new list.
                channelAdapter.submitList(channelInfoList);
            }
        });
    }

    @Override
    public void onChannelClick(ChannelInfo item) {
        // The adapter now provides the correct ChannelInfo object on click.
        Intent intent = new Intent(this, SelectVideoActivity.class);
        intent.putExtra("CHANNEL_ID", item.channelId);
        startActivity(intent);
    }
}
