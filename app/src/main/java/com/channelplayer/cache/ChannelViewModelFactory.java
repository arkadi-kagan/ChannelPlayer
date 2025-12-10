package com.channelplayer.cache;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.api.services.youtube.YouTube;

public class ChannelViewModelFactory implements ViewModelProvider.Factory {
    private final Application mApplication;
    private final YouTube mYoutubeService;

    public ChannelViewModelFactory(Application application, YouTube youtubeService) {
        mApplication = application;
        mYoutubeService = youtubeService;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ChannelViewModel.class)) {
            return (T) new ChannelViewModel(mApplication, mYoutubeService);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
