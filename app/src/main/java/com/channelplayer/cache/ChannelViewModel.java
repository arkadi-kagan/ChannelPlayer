package com.channelplayer.cache;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;import androidx.lifecycle.LiveData;
import com.google.api.services.youtube.YouTube;
import java.util.List;
import java.util.Set;

public class ChannelViewModel extends AndroidViewModel {
    private final ChannelRepository mRepository;
    private final ConfigRepository mConfigRepository;

    public ChannelViewModel(@NonNull Application application, @NonNull YouTube youtubeService, @NonNull ConfigRepository configRepository) {
        super(application);
        mRepository = new ChannelRepository(application, youtubeService, configRepository);
        mConfigRepository = configRepository;
    }

    public LiveData<List<ChannelInfo>> getAllChannels() {
        return mRepository.getAllChannels();
    }

    public Set<String> getInitialBannedVideos() {
        return mConfigRepository.getBannedVideos().keySet();
    }
}
