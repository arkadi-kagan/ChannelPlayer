package com.channelplayer.cache;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;import androidx.lifecycle.LiveData;
import com.google.api.services.youtube.YouTube;
import java.util.List;

public class ChannelViewModel extends AndroidViewModel {
    private final ChannelRepository mRepository;

    public ChannelViewModel(@NonNull Application application, @NonNull YouTube youtubeService, @NonNull ConfigRepository configRepository) {
        super(application);
        mRepository = new ChannelRepository(application, youtubeService, configRepository);
    }

    public LiveData<List<ChannelInfo>> getAllChannels() {
        return mRepository.getAllChannels();
    }
}
