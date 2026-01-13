package com.channelplayer.cache;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class HistoryViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;

    public HistoryViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HistoryViewModel.class)) {
            return (T) new HistoryViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
