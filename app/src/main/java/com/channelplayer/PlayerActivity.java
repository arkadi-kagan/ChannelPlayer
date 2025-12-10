package com.channelplayer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID";
    public static final String EXTRA_VIDEO_DESCRIPTION = "EXTRA_VIDEO_DESCRIPTION";
    public static final String EXTRA_ACCOUNT_NAME = "EXTRA_ACCOUNT_NAME";

    private ViewOnlyWebView youtubeWebView;
    private String videoId;
    private String videoDescription;
    private String accountName;
    private boolean isYouTubePageLoaded = false;

    // --- New UI elements and state ---
    private ImageButton playPauseButton;
    private SeekBar videoSeekBar;
    private Handler progressUpdateHandler;
    private boolean isPlaying = false;
    private boolean isSeeking = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        accountName = getIntent().getStringExtra(EXTRA_ACCOUNT_NAME);
        videoDescription = getIntent().getStringExtra(EXTRA_VIDEO_DESCRIPTION);

        TextView descriptionTextView = findViewById(R.id.video_description_text);
        descriptionTextView.setText(videoDescription);

        youtubeWebView = findViewById(R.id.youtube_webview);
        playPauseButton = findViewById(R.id.play_pause_button);
        videoSeekBar = findViewById(R.id.video_seekbar);

        setupWebView();
        setupPlayerControls();

        syncAndLoadVideo();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void setupPlayerControls() {
        // --- Setup Play/Pause Button ---
        playPauseButton.setOnClickListener(v -> {
            if (youtubeWebView == null || !isYouTubePageLoaded) return;
            youtubeWebView.evaluateJavascript("window.togglePlayPause();", null);
            youtubeWebView.evaluateJavascript("window.is_paused();", result -> {
                if (result.equals("true")) {
                    playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24);
                    isPlaying = false;
                } else {
                    playPauseButton.setImageResource(R.drawable.outline_autopause_24);
                    isPlaying = true;
                }
            });
        });

        // --- Setup SeekBar (Ruler) ---
        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // We only act when the user initiates the change
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true; // Pause progress updates while user is seeking
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // When user releases the thumb, seek the video
                youtubeWebView.evaluateJavascript("window.seekTo(" + seekBar.getProgress() + ");", null);
                isSeeking = false;
            }
        });

        // --- Handler to periodically update the SeekBar ---
        progressUpdateHandler = new Handler(Looper.getMainLooper());
    }

    // --- Android/JavaScript Bridge to receive updates from WebView ---
    private class JsBridge {
        @JavascriptInterface
        public void updateProgress(final double currentTime, final double duration, final boolean isPaused) {
            runOnUiThread(() -> {
                if (isSeeking) return; // Don't update UI if user is dragging the seekbar

                if (!Double.isNaN(duration) && duration > 0) {
                    videoSeekBar.setMax((int) duration);
                    videoSeekBar.setProgress((int) currentTime);
                }

                // Update play/pause button icon based on actual video state
                if (isPaused && isPlaying) {
                    playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24);
                    isPlaying = false;
                } else if (!isPaused && !isPlaying) {
                    playPauseButton.setImageResource(R.drawable.outline_autopause_24);
                    isPlaying = true;
                }
            });
        }
    }

    // This method is now obsolete, but we keep the new setupWebView
    private void sendPlayPauseClickToWebView() {}

    private void setupWebView() {
        youtubeWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        youtubeWebView.getSettings().setJavaScriptEnabled(true);
        youtubeWebView.getSettings().setDomStorageEnabled(true);
        youtubeWebView.addJavascriptInterface(new JsBridge(), "AndroidBridge"); // Add the bridge
        youtubeWebView.setWebChromeClient(new WebChromeClient());
    }

    private void syncAndLoadVideo() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        youtubeWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.startsWith("https://accounts.google.com")) {
                    loadYouTubeUrl();
                } else if (url.startsWith("https://m.youtube.com/watch")) {
                    if (isYouTubePageLoaded) return;
                    injectPlayerControlScript(view);
                    isYouTubePageLoaded = true;
                }
            }
        });
        if (accountName != null && !accountName.isEmpty()) {
            youtubeWebView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube");
        } else {
            loadYouTubeUrl();
        }
    }

    private void loadYouTubeUrl() {
        isYouTubePageLoaded = false;
        String youtubeWatchUrl = "https://m.youtube.com/watch?v=" + videoId;
        youtubeWebView.loadUrl(youtubeWatchUrl);
    }

    // --- Renamed from injectFullscreenCss for clarity ---
    private void injectPlayerControlScript(WebView view) {
        // Combined script for fullscreen CSS and JS player controls
        String script = """
        // --- Part 1: Fullscreen CSS ---
        var style = document.createElement('style');
        style.type = 'text/css';
        style.innerHTML = `
            html, body { background-color: black !important; overflow: hidden !important; height: 100% !important; }
            body > * { display: none !important; }
            ytm-watch, ytm-player, #player-container-id, #player, .html5-video-player {
                display: block !important;
                position: fixed !important; top: 0 !important; left: 0 !important;
                width: 100vw !important; height: 100vh !important;
                z-index: 9999 !important; margin: 0 !important; padding: 0 !important;
            }
        `;
        document.head.appendChild(style);

        // --- Part 2: JavaScript Player Controls ---
        (function() {
            function findVideoElement() {
                // Encapsulated search for the video element
                return document.querySelector('.html5-video-player video');
            }

            // Function to toggle play/pause
            window.togglePlayPause = function() {
                var video = findVideoElement();
                if (video.paused) {
                    video.play();
                } else {
                    video.pause();
                }
            };

            // Function to seek to a specific time
            window.seekTo = function(timeInSeconds) {
                var video = findVideoElement();
                video.currentTime = timeInSeconds;
            };

            // Function to check if paused
            window.is_paused = function() {
                var video = findVideoElement();
                return video.paused;
            };

            // Unmute on load
            var attemptCount = 0;
            var found = false;
            var debugInterval = setInterval(function() {
                // Find all elements with a class that contains 'unmute'. This is a broader search.
                const elements = document.getElementsByClassName("ytp-unmute");

                if (elements.length > 0) {
                    console.log('Found ' + elements.length + ' elements with "unmute" in their class:');

                    // Loop through all found elements and print their details
                    for (const el of elements) {
                        if (el.tagName === 'BUTTON') {
                            if (el.innerText.trim().toLowerCase().includes('unmute')) {
                                el.click();
                                found = true;
                                break;
                            }
                        }
                    }

                    if (found) {
                        clearInterval(debugInterval); // Stop after finding and logging.
                    }

                } else {
                    attemptCount++;
                    if (attemptCount > 20) { // Stop trying after 10 seconds (20 * 500ms)
                        console.log('Could not find any elements with "unmute" in their class after 10 seconds.');
                        clearInterval(debugInterval);
                    }
                }
            }, 500); // Check every 500 milliseconds.
        })();
        """;
        view.evaluateJavascript(script, null);
    }

    // --- WebView Lifecycle Management ---
    @Override
    protected void onPause() {
        super.onPause();
        if (youtubeWebView != null) {
            youtubeWebView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (youtubeWebView != null) {
            youtubeWebView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (progressUpdateHandler != null) {
            progressUpdateHandler.removeCallbacksAndMessages(null); // Clean up handler
        }
        if (youtubeWebView != null) {
            youtubeWebView.loadUrl("about:blank");
            youtubeWebView.stopLoading();
            youtubeWebView.removeJavascriptInterface("AndroidBridge");
            youtubeWebView.setWebChromeClient(null);
            youtubeWebView.setWebViewClient(null);
            youtubeWebView.destroy();
            youtubeWebView = null;
        }
        super.onDestroy();
    }
}
