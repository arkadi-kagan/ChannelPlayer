package com.channelplayer;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID";
    public static final String EXTRA_VIDEO_DESCRIPTION = "EXTRA_VIDEO_DESCRIPTION";

    private YouTubePlayerView youTubePlayerView;
    private YouTubePlayer youTubePlayer;
    private TextView videoDescriptionText;
    private Button playPauseButton;
    private Button stopButton;
    private SeekBar videoSeekBar;

    private String videoId;
    private String videoDescription;
    private boolean isPlaying = false;
    private boolean isPlayerInFullScreen = false;
    private FullScreenHelper fullScreenHelper = new FullScreenHelper(this);
    private Function0<Unit> exitFullscreenAction = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        videoDescription = getIntent().getStringExtra(EXTRA_VIDEO_DESCRIPTION);

        youTubePlayerView = findViewById(R.id.youtube_player_view);

        // This handles portrait mode UI
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            videoDescriptionText = findViewById(R.id.video_description_text);
            playPauseButton = findViewById(R.id.play_pause_button);
            stopButton = findViewById(R.id.stop_button);
            videoSeekBar = findViewById(R.id.video_seek_bar);

            videoDescriptionText.setText(videoDescription);

            playPauseButton.setOnClickListener(v -> {
                if (youTubePlayer != null) {
                    if (isPlaying) {
                        youTubePlayer.pause();
                    } else {
                        youTubePlayer.play();
                    }
                    isPlaying = !isPlaying;
                }
            });

            stopButton.setOnClickListener(v -> {
                if (youTubePlayer != null) {
                    youTubePlayer.pause();
                    isPlaying = false;
                }
            });

            videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && youTubePlayer != null) {
                        youTubePlayer.seekTo(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { /* Do nothing */ }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { /* Do nothing */ }
            });
        }

        IFramePlayerOptions iFramePlayerOptions = new IFramePlayerOptions.Builder()
                .controls(1)
                .origin("https://www.youtube.com")
                .build();

        youTubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer player) {
                youTubePlayer = player;
                youTubePlayer.loadVideo(videoId, 0);
            }

            @Override
            public void onCurrentSecond(@NonNull YouTubePlayer youTubePlayer, float second) {
                if (videoSeekBar != null) videoSeekBar.setProgress((int) second);
            }

            @Override
            public void onVideoDuration(@NonNull YouTubePlayer youTubePlayer, float duration) {
                if (videoSeekBar != null) videoSeekBar.setMax((int) duration);
            }

            @Override
            public void onError(@NonNull YouTubePlayer youTubePlayer, @NonNull PlayerConstants.PlayerError error) {
                Log.e("PlayerActivity", "YouTube Player Error: " + error.toString());
            }
        }, true, iFramePlayerOptions);


        addFullScreenListenerToPlayer();

        // Handle back button presses using the new OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isPlayerInFullScreen) {
                    if (exitFullscreenAction != null) {
                        exitFullscreenAction.invoke();
                    }
                } else {
                    if (youTubePlayer != null) {
                        youTubePlayer.pause();
                    }
                    // Finish the activity to go back to the previous screen
                    finish();
                }
            }
        });
    }

    private void addFullScreenListenerToPlayer() {
        youTubePlayerView.addFullscreenListener(new FullscreenListener() {
            @Override
            public void onEnterFullscreen(@NonNull View fullscreenView, @NonNull Function0<Unit> exitFullscreen) {
                exitFullscreenAction = exitFullscreen;
                isPlayerInFullScreen = true;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                fullScreenHelper.enterFullScreen(fullscreenView);

                // Hide other views when entering fullscreen
                if (videoDescriptionText != null) videoDescriptionText.setVisibility(View.GONE);
                if (playPauseButton != null) playPauseButton.setVisibility(View.GONE);
                if (stopButton != null) stopButton.setVisibility(View.GONE);
                if (videoSeekBar != null) videoSeekBar.setVisibility(View.GONE);
            }

            @Override
            public void onExitFullscreen() {
                exitFullscreenAction = null;
                isPlayerInFullScreen = false;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                fullScreenHelper.exitFullScreen(youTubePlayerView);

                // Show other views when exiting fullscreen
                if (videoDescriptionText != null) videoDescriptionText.setVisibility(View.VISIBLE);
                if (playPauseButton != null) playPauseButton.setVisibility(View.VISIBLE);
                if (stopButton != null) stopButton.setVisibility(View.VISIBLE);
                if (videoSeekBar != null) videoSeekBar.setVisibility(View.VISIBLE);
            }
        });
    }


    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // The library's default UI will handle the fullscreen button clicks,
        // which will trigger the listener we added.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        youTubePlayerView.release();
    }
}
