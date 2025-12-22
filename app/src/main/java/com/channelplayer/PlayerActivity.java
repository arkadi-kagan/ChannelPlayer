package com.channelplayer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.channelplayer.cache.ConfigRepository;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoGetRatingResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID";
    public static final String EXTRA_VIDEO_DESCRIPTION = "EXTRA_VIDEO_DESCRIPTION";
    public static final String EXTRA_ACCOUNT_NAME = "EXTRA_ACCOUNT_NAME";

    private ViewOnlyWebView youtubeWebView;
    private String videoId;
    private String videoDescription;
    private String accountName;
    private boolean isYouTubePageLoaded = false;
    private boolean isScriptInjected = false;

    private ImageButton playPauseButton;
    private ImageButton reloadButton;
    private ImageButton banVideoButton;
    private ImageButton likeButton;
    private ImageButton dislikeButton;
    private ImageButton skipAd;
    private SeekBar videoSeekBar;
    private Handler progressUpdateHandler;
    private boolean isPlaying = false;
    private boolean isSeeking = false;
    private String rating = "none"; // "like", "dislike", "none"

    private YouTube youtube;
    private GoogleAccountCredential credential;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> requestAuthorizationLauncher;

    // --- Timer for unmute logic ---
    private Timer unmuteTimer;
    private final AtomicInteger unmuteAttempts = new AtomicInteger(0);
    private static final int MAX_UNMUTE_ATTEMPTS = 20;
    private TextView descriptionTextView;
    private View controlBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int progress = 0;
    private boolean progressAltered = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        requestAuthorizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        checkVideoRating();
                    }
                });

        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        accountName = getIntent().getStringExtra(EXTRA_ACCOUNT_NAME);
        videoDescription = getIntent().getStringExtra(EXTRA_VIDEO_DESCRIPTION);

        descriptionTextView = findViewById(R.id.video_description_text);
        descriptionTextView.setText(videoDescription);

        youtubeWebView = findViewById(R.id.youtube_webview);
        playPauseButton = findViewById(R.id.play_pause_button);
        reloadButton = findViewById(R.id.reload);
        banVideoButton = findViewById(R.id.ban_video);
        likeButton = findViewById(R.id.like_button);
        dislikeButton = findViewById(R.id.dislike_button);
        skipAd = findViewById(R.id.skip_ad);
        videoSeekBar = findViewById(R.id.video_seekbar);
        controlBar = findViewById(R.id.control_bar);


        setupWebView();
        setupPlayerControls();
        setupYoutubeApi();

        syncAndLoadVideo();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        checkVideoRating();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUnmuteTimer(); // Ensure timer is stopped when activity is destroyed
    }

    private void setupYoutubeApi() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton("https://www.googleapis.com/auth/youtube.force-ssl"));
            credential.setSelectedAccount(account.getAccount());

            youtube = new YouTube.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    credential
            ).setApplicationName(getString(R.string.app_name)).build();
        }
    }

    private void togglePlayPause() {
        if (youtubeWebView == null || !isYouTubePageLoaded) return;
        safeInvoke("togglePlayPause()", () -> {
            youtubeWebView.evaluateJavascript("window.is_paused();", result -> {
                if ("true".equals(result)) {
                    playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24);
                    isPlaying = false;
                } else {
                    playPauseButton.setImageResource(R.drawable.outline_autopause_24);
                    isPlaying = true;
                }
            });
        });
    }

    private void setupPlayerControls() {
        playPauseButton.setOnClickListener(v -> {
            togglePlayPause();
        });

        reloadButton.setOnClickListener(v -> {
            progress = videoSeekBar.getProgress();
            progressAltered = true;
            syncAndLoadVideo();
        });

        banVideoButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Ban Video")
                    .setMessage("Are you sure you want to ban this video?")
                    .setIcon(R.drawable.ban_video) // Or your specific "ban_video" drawable
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Intent data = new Intent();
                        data.putExtra("VIDEO_BANNED", true);
                        data.putExtra("VIDEO_ID", videoId);
                        setResult(RESULT_OK, data);

                        // Show a brief toast and exit the activity
                        Toast.makeText(this, "Video banned.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setCancelable(true)
                    .show();
        });


        likeButton.setOnClickListener(v -> {
            if ("like".equals(rating)) {
                rateVideo("none");
            } else {
                rateVideo("like");
            }
        });

        dislikeButton.setOnClickListener(v -> {
            if ("dislike".equals(rating)) {
                rateVideo("none");
            } else {
                rateVideo("dislike");
            }
        });

        skipAd.setOnClickListener(v -> {
            safeInvoke("skipAd()", null);
            Log.d("PlayerActivity", "Function skipAd called.");
        });

        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                safeInvoke("seekTo(" + seekBar.getProgress() + ")", null);
                progressAltered = false;
                isSeeking = false;
            }
        });

        progressUpdateHandler = new Handler(Looper.getMainLooper());
    }

    private void dumpDOM() {
        youtubeWebView.evaluateJavascript("window.dumpDOM();", null);
    }

    private void safeInvoke(String functionCall, Runnable onComplete) {
        if (youtubeWebView == null || !isYouTubePageLoaded) return;

        String functionName = functionCall.substring(0, functionCall.indexOf('('));

        String script = "if (typeof window." + functionName + " === 'function') { " +
                "    window." + functionCall + "; " + // Returns true or false from JS
                "} else { " +
                "    'undefined'; " +
                "}";

        youtubeWebView.evaluateJavascript(script, result -> {
            if ("'undefined'".equals(result)) {
                injectPlayerControlScript(youtubeWebView);
                isScriptInjected = true;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    youtubeWebView.evaluateJavascript("window." + functionCall, value -> {
                        if (onComplete != null) {
                            runOnUiThread(onComplete);
                        }
                    });
                }, 100);
            } else {
                if (onComplete != null) {
                    runOnUiThread(onComplete);
                }
            }
        });
    }

    private void checkVideoRating() {
        if (youtube == null) return;
        executorService.submit(() -> {
            try {
                VideoGetRatingResponse response = youtube.videos().getRating(Collections.singletonList(videoId)).execute();
                if (response.getItems() != null && !response.getItems().isEmpty()) {
                    rating = response.getItems().get(0).getRating();
                } else {
                    rating = "none";
                }
                runOnUiThread(this::updateRatingButtons);
            } catch (UserRecoverableAuthIOException e) {
                requestAuthorizationLauncher.launch(e.getIntent());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void rateVideo(String newRating) {
        if (youtube == null) return;
        executorService.submit(() -> {
            try {
                youtube.videos().rate(videoId, newRating).execute();
                rating = newRating;
                runOnUiThread(this::updateRatingButtons);
            } catch (UserRecoverableAuthIOException e) {
                requestAuthorizationLauncher.launch(e.getIntent());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateRatingButtons() {
        if ("like".equals(rating)) {
            likeButton.setImageResource(R.drawable.like_blue);
            dislikeButton.setImageResource(R.drawable.dislike_gray);
        } else if ("dislike".equals(rating)) {
            likeButton.setImageResource(R.drawable.like_gray);
            dislikeButton.setImageResource(R.drawable.dislike_blue);
        } else {
            likeButton.setImageResource(R.drawable.like_gray);
            dislikeButton.setImageResource(R.drawable.dislike_gray);
        }
    }

    private void ensureVideoMaximized() {
        if (youtubeWebView != null && isYouTubePageLoaded) {
            safeInvoke("ensureVideoMaximized()", null);
            Log.d("PlayerActivity", "Function ensureVideoMaximized called.");
        }
    }

    private class JsBridge {
        @JavascriptInterface
        public void updateProgress(final double currentTime, final double duration, final boolean isPaused) {
            runOnUiThread(() -> {
                if (isSeeking) return;

                if (!Double.isNaN(duration) && duration > 0) {
                    videoSeekBar.setMax((int) duration);
                    videoSeekBar.setProgress((int) currentTime);
                }

                if (isPaused && isPlaying) {
                    playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24);
                    isPlaying = false;
                } else if (!isPaused && !isPlaying) {
                    playPauseButton.setImageResource(R.drawable.outline_autopause_24);
                    isPlaying = true;
                }
            });
        }

        @JavascriptInterface
        public void onDomDump(String domString) {
            final int maxLogSize = 4000;
            if (domString == null || domString.isEmpty()) {
                Log.d("DOM_DUMP", "Received empty or null DOM string.");
                return;
            }
            for(int i = 0; i <= domString.length() / maxLogSize; i++) {
                int start = i * maxLogSize;
                int end = Math.min((i + 1) * maxLogSize, domString.length());
                Log.d("DOM_DUMP", domString.substring(start, end));
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        youtubeWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                callback.onCustomViewHidden();
            }
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100 && !isScriptInjected) {
                    injectPlayerControlScript(view);
                    isScriptInjected = true;
                }
            }
        });
        youtubeWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        youtubeWebView.getSettings().setJavaScriptEnabled(true);
        youtubeWebView.getSettings().setDomStorageEnabled(true);
        youtubeWebView.addJavascriptInterface(new JsBridge(), "AndroidBridge");
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
                    isYouTubePageLoaded = true;
                    doChangeConfiguration(getResources().getConfiguration().orientation);
                    startUnmuteTimer();
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (url.startsWith("https://m.youtube.com/watch")) {
                    isScriptInjected = false;
                    isYouTubePageLoaded = false;
                    stopUnmuteTimer();
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
        isScriptInjected = false;
        String youtubeWatchUrl = "https://m.youtube.com/watch?v=" + videoId;
        youtubeWebView.loadUrl(youtubeWatchUrl);
    }

    private void startUnmuteTimer() {
        stopUnmuteTimer();
        unmuteAttempts.set(0);
        unmuteTimer = new Timer();

        unmuteTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (!isYouTubePageLoaded || unmuteAttempts.get() >= MAX_UNMUTE_ATTEMPTS) {
                        if (unmuteAttempts.get() >= MAX_UNMUTE_ATTEMPTS) {
                            Log.w("UnmuteTimer", "Max unmute attempts reached, stopping timer.");
                        }
                        stopUnmuteTimer();
                        return;
                    }

                    int attempt = unmuteAttempts.incrementAndGet();
                    Log.d("UnmuteTimer", "Attempting to unmute, attempt #" + attempt);

                    String script = "if (typeof window.unmute === 'function') { window.unmute(); } else { 'false'; }";
                    youtubeWebView.evaluateJavascript(script, result -> {
                        if ("true".equals(result)) {
                            Log.d("UnmuteTimer", "Unmute successful, stopping timer.");
                            stopUnmuteTimer();
                        }
                    });
                });
            }
        }, 1000, 1000); // Start after 1s, repeat every 1s
    }

    private void stopUnmuteTimer() {
        if (unmuteTimer != null) {
            unmuteTimer.cancel();
            unmuteTimer = null;
            if (progressAltered) {
                videoSeekBar.setProgress(progress);
                safeInvoke("seekTo(" + progress + ")", null);
                progressAltered = false;
            }
        }
    }

    private void injectPlayerControlScript(WebView view) {
        // This script now re-queries for the 'video' element in each function
        // to avoid issues with injection timing.
        String script = """
            // Helper to bypass TrustedHTML restrictions
            function getTrustedHTML(html) {
                if (window.trustedTypes && window.trustedTypes.createPolicy) {
                    const policy = window.trustedTypes.defaultPolicy ||\s
                                   window.trustedTypes.createPolicy('web-view-injection', {
                                       createHTML: (s) => s
                                   });
                    return policy.createHTML(html);
                }
                return html;
            }

            window.ensureVideoMaximized = function() {
                if (document.querySelector('#full-screen-player-style'))
                    return;

                var style = document.createElement('style');
                style.id = 'full-screen-player-style';
                style.type = 'text/css';
                const css = `
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
                    #header-bar {
                        display: none !important;
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
                style.innerHTML = getTrustedHTML(css);
                document.head.appendChild(style);
            }

            // --- Player Control Functions ---
            window.togglePlayPause = function() {
                const video = document.querySelector('video');
                if (video) {
                    if (video.paused) { video.play(); } else { video.pause(); }
                    return true;
                }
                return false;
            };

            window.seekTo = function(seconds) {
                const video = document.querySelector('video');
                if (video) {
                    video.currentTime = seconds;
                    return true;
                }
                return false;
            };

            window.is_paused = function() {
                const video = document.querySelector('video');
                return video ? video.paused : true;
            };

            window.unmute = function() {
                const video = document.querySelector('video');
                if (video) {
                    if (video.muted) {
                        video.muted = false;
                    }
                    // Return true if the video is now unmuted.
                    return !video.muted;
                }
                return false; // Video not found
            };

            window.skipAd = function() {
                var skipAd = document.querySelector('BUTTON[id^="skip-ad"]');
                if (skipAd) {
                    skipAd.click();
                    return true;
                }
                return false;
            }

            // --- Event Listeners for Progress Reporting ---
            // This part still needs to find the video initially, but it's less critical if it fails.
            // The core controls will still work.
            window.setupProgressUpdater = function() {
                const video = document.querySelector('video');
                if (video) {
                    const progressUpdater = () => {
                        if (typeof AndroidBridge !== 'undefined') {
                            AndroidBridge.updateProgress(video.currentTime, video.duration, video.paused);
                        }
                    };
                    video.addEventListener('timeupdate', progressUpdater);
                    video.addEventListener('pause', progressUpdater);
                    video.addEventListener('play', progressUpdater);
                }
            }

            // --- DOM Dump Function ---
            window.dumpDOM = function() {
                function traverse(element, depth, dump) {
                    if (!element) return;

                    const indent = '  '.repeat(depth);
                    const tagName = element.tagName.toLowerCase();
                    const id = element.id ? `#${element.id}` : '';
                    const classes = Array.from(element.classList);
                    const class1 = classes.length > 0 ? `.${classes[0]}` : '.';
                    const class2 = classes.length > 1 ? `.${classes[1]}` : '.';
                    const classStr = `${class1}, ${class2}`;
                    const text = element.textContent ? `: ${element.textContent.trim().substring(0, 20)}` : '';

                    dump.push(`${indent}${tagName}, ${classStr}, ${id}, ${text}`);

                    Array.from(element.children).forEach(child => traverse(child, depth + 1, dump));
                }

                const dumpResult = [];
                traverse(document.documentElement, 0, dumpResult);
                const resultString = dumpResult.join('\\n');

                if (typeof AndroidBridge !== 'undefined') {
                    AndroidBridge.onDomDump(resultString);
                } else {
                    console.log(resultString);
                }
                return true;
            };
        """;
        view.evaluateJavascript(script, null);
        view.evaluateJavascript("window.setupProgressUpdater();", null);
    }

    public void doChangeConfiguration(int orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("PlayerActivity", "Switched to Landscape");

            controlBar.setVisibility(android.view.View.GONE);
            videoSeekBar.setVisibility(android.view.View.GONE);
            descriptionTextView.setVisibility(android.view.View.GONE);

            ensureVideoMaximized();

            android.view.ViewGroup.LayoutParams layoutParams = youtubeWebView.getLayoutParams();
            layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            youtubeWebView.setLayoutParams(layoutParams);

        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("PlayerActivity", "Switched to Portrait");

            controlBar.setVisibility(android.view.View.VISIBLE);
            videoSeekBar.setVisibility(android.view.View.VISIBLE);
            descriptionTextView.setVisibility(android.view.View.VISIBLE);

            ensureVideoMaximized();

            android.view.ViewGroup.LayoutParams layoutParams = youtubeWebView.getLayoutParams();
            layoutParams.height = 0;
            youtubeWebView.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        doChangeConfiguration(newConfig.orientation);
    }
}
