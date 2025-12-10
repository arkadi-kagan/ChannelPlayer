package com.channelplayer.cache;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;import androidx.lifecycle.LiveData;
import com.google.api.services.youtube.YouTube;
import java.util.List;

public class ChannelViewModel extends AndroidViewModel {
    private final ChannelRepository mRepository;
    private final LiveData<List<ChannelInfo>> mAllChannels;

    public ChannelViewModel(@NonNull Application application, @NonNull YouTube youtubeService) {
        super(application);
        mRepository = new ChannelRepository(application, youtubeService);
        mAllChannels = mRepository.getAllChannels();
    }

    public LiveData<List<ChannelInfo>> getAllChannels() {
        return mAllChannels;
    }
}
