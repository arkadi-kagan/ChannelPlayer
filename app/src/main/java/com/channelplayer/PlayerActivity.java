package com.channelplayer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
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

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Retrieve data from the Intent
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        accountName = getIntent().getStringExtra(EXTRA_ACCOUNT_NAME);
        videoDescription = getIntent().getStringExtra(EXTRA_VIDEO_DESCRIPTION); // Retrieve the description

        TextView descriptionTextView = findViewById(R.id.video_description_text);
        if (videoDescription != null) {
            descriptionTextView.setText(videoDescription);
        }

        // Initialize WebView
        youtubeWebView = findViewById(R.id.youtube_webview);
        setupWebView();

        Button playPauseButton = findViewById(R.id.play_pause_button);
        playPauseButton.setOnClickListener(v -> {
            sendPlayPauseClickToWebView();
        });

        // Start the cookie syncing and loading process
        syncAndLoadVideo();

        // Handle back press to navigate within WebView or finish the activity
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void sendPlayPauseClickToWebView() {
        if (youtubeWebView == null || !isYouTubePageLoaded) return;

        // This JavaScript finds the play/pause button by its class and clicks it.
        String script = "window.togglePlayPause();";
        youtubeWebView.evaluateJavascript(script, null);
    }

    private void setupWebView() {
        youtubeWebView.getSettings().setJavaScriptEnabled(true);
        youtubeWebView.getSettings().setDomStorageEnabled(true); // Needed for modern websites
        youtubeWebView.setWebChromeClient(new WebChromeClient()); // Allows fullscreen etc.
    }

    private void syncAndLoadVideo() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        youtubeWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Step 1: After Google login page, cookies should be set. Now load YouTube.
                if (url.startsWith("https://accounts.google.com")) {
                    loadYouTubeUrl();
                }
                // Step 2: After YouTube page loads, inject CSS to make it fullscreen-like.
                else if (url.startsWith("https://m.youtube.com/watch")) {
                    // Prevent script from running multiple times on the same page
                    if (isYouTubePageLoaded) return;

                    injectFullscreenCss(view);
                    isYouTubePageLoaded = true;
                }
            }
        });

        // If an account name is provided, start by logging into Google to get cookies.
        if (accountName != null && !accountName.isEmpty()) {
            // This URL will use the device's logged-in Google account to create a session
            // and set the necessary cookies in the WebView's CookieManager.
            youtubeWebView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube");
        } else {
            // If there's no account, load the video directly. It may be unavailable if private.
            loadYouTubeUrl();
        }
    }

    private void loadYouTubeUrl() {
        isYouTubePageLoaded = false; // Reset flag for the new page
        // Use the mobile site URL
        String youtubeWatchUrl = "https://m.youtube.com/watch?v=" + videoId;
        youtubeWebView.loadUrl(youtubeWatchUrl);
    }

    private void injectFullscreenCss(WebView view) {
        // This JavaScript finds the video player and forces it to fill the WebView,
        // while hiding everything else. This is the "theater mode" effect.
        String script = """
        var style = document.createElement('style');
        style.type = 'text/css';
        style.innerHTML = `
            /* Make the body and html black, and hide overflow */
            html, body {
                background-color: black !important;
                overflow: hidden !important;
                height: 100% !important;
            }

            /* Hide every element on the page by default */
            body > * {
                display: none !important;
            }

            /* Specifically un-hide the player container and its parents */
            ytm-watch, ytm-player, #player-container-id, #player {
                display: block !important;
            }

            /* Force the player and its containers to fill the entire viewport */
            ytm-watch, ytm-player, #player-container-id, #player, .html5-video-player {
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                width: 100vw !important; /* Viewport Width */
                height: 100vh !important; /* Viewport Height */
                z-index: 9999 !important;
                margin: 0 !important;
                padding: 0 !important;
            }
        `;
        document.head.appendChild(style);

        // 2. Define a GLOBAL, reusable function to toggle playback.
        // This is the most reliable method as it interacts directly with the <video> element.
        window.togglePlayPause = function() {
            var video = document.querySelector('BUTTON.ytp-large-play-button');
            if (video) {
                console.log('Video is paused. Calling video.play()');
                video.click();
            } else {
                console.log('togglePlayPause Error: Could not find the <video> element.');
            }
        };

        function safeClick(element) {
            element.click();
            console.log("Safely clicked element. Tag: " + element.tagName + ", Class: " + element.className);
        }

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
                            safeClick(el);
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
        """;
        view.evaluateJavascript(script, null);
    }

    // --- WebView Lifecycle Management ---

    @Override
    protected void onPause() {
        super.onPause();
        // Pausing the WebView is important to stop video playback and JS execution
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
        // Always destroy the WebView to prevent memory leaks
        if (youtubeWebView != null) {
            youtubeWebView.loadUrl("about:blank"); // Clear the view
            youtubeWebView.stopLoading();
            youtubeWebView.setWebChromeClient(null);
            youtubeWebView.setWebViewClient(null);
            youtubeWebView.destroy();
            youtubeWebView = null;
        }
        super.onDestroy();
    }
}
