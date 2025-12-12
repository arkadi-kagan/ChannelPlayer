package com.channelplayer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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
    private ImageButton likeButton;
    private ImageButton dislikeButton;
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

        TextView descriptionTextView = findViewById(R.id.video_description_text);
        descriptionTextView.setText(videoDescription);

        descriptionTextView.setOnLongClickListener(v -> {
            if (youtubeWebView != null && isYouTubePageLoaded) {
                safeInvoke("dumpDOM()", null);
                Log.d("DOM_DUMP", "Requested DOM dump via long press.");
            }
            return true;
        });

        youtubeWebView = findViewById(R.id.youtube_webview);
        playPauseButton = findViewById(R.id.play_pause_button);
        likeButton = findViewById(R.id.like_button);
        dislikeButton = findViewById(R.id.dislike_button);
        videoSeekBar = findViewById(R.id.video_seekbar);

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


    private void setupPlayerControls() {
        playPauseButton.setOnClickListener(v -> {
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
                isSeeking = false;
            }
        });

        progressUpdateHandler = new Handler(Looper.getMainLooper());
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
        }
    }

    private void injectPlayerControlScript(WebView view) {
        // This script now re-queries for the 'video' element in each function
        // to avoid issues with injection timing.
        String script = """
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
            
            // --- Event Listeners for Progress Reporting ---
            // This part still needs to find the video initially, but it's less critical if it fails.
            // The core controls will still work.
            (function() {
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
            })();

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

                    dump.push(`${indent}${tagName}, ${classStr}, ${id}`);

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
    }
}
