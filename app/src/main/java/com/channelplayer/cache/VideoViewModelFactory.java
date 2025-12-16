package com.channelplayer.cache;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.api.services.youtube.YouTube;

/**
 * A ViewModelProvider.Factory that is responsible for creating instances of VideoViewModel.
 * This is necessary because VideoViewModel has a non-empty constructor.
 */
public class VideoViewModelFactory implements ViewModelProvider.Factory {
    private final Application mApplication;
    private final YouTube mYoutubeService;
    private final ConfigRepository mConfigRepository;


    /**
     * Constructor for the factory.
     * @param application The application context.
     * @param youtubeService An initialized YouTube service instance.
     */
    public VideoViewModelFactory(Application application, YouTube youtubeService, ConfigRepository configRepository) {
        mApplication = application;
        mYoutubeService = youtubeService;
        mConfigRepository = configRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        // Check if the requested ViewModel is of type VideoViewModel
        if (modelClass.isAssignableFrom(VideoViewModel.class)) {
            // If it is, create and return a new instance, passing the required dependencies.
            return (T) new VideoViewModel(mApplication, mYoutubeService, mConfigRepository);
        }
        // If the ViewModel class is unknown, throw an exception.
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
