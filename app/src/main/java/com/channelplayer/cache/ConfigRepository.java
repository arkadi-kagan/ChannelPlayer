package com.channelplayer.cache;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.channelplayer.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConfigRepository {
    private static final String TAG = "ConfigRepository";
    private static final String USER_CHANNELS_FILENAME = "channel_handles.json";
    private static final String PREFS_NAME = "ChannelPlayerPrefs";
    private static final String KEY_CONFIG_FILE_URI = "configFileUri";

    private final AppCompatActivity activity;

    public List<String> channel_handles;
    public Map<String, String> banned_video_ids;    // Video ID to description string

    private static ConfigRepository instance;

    public static ConfigRepository getInstance(AppCompatActivity activity) {
        if (instance == null) {
            instance = new ConfigRepository(activity);
        }
        return instance;
    }

    private ConfigRepository(AppCompatActivity activity) {
        this.activity = activity;
        channel_handles = new ArrayList<>();
        banned_video_ids = new HashMap<>();
        loadConfig();
    }

    public List<String> getChannelHandles() {
        return channel_handles;
    }

    /**
     * Returns a map of banned video IDs to their descriptions.
     */
    public Map<String, String> getBannedVideos() {
        return banned_video_ids;
    }

    public void banVideo(String videoId, String description) {
        banned_video_ids.put(videoId, description);
        saveConfig();
    }

    private void saveConfig() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_CONFIG_FILE_URI, null);

        if (uriString == null) {
            Log.d(TAG, "No config file URI found in SharedPreferences. Starting copyConfig().");
            copyConfig(); // Ask user to create/select the config file
            saveConfig();
            return;
        }

        Uri configUri = Uri.parse(uriString);

        try {
            // Persist permission to access the URI across device reboots
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            activity.getContentResolver().takePersistableUriPermission(configUri, takeFlags);

            // Parse the JSON using org.json
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("channel_handles", new JSONArray(channel_handles));
            jsonObject.put("banned_video_ids", new JSONObject(banned_video_ids));

            OutputStream stream = activity.getContentResolver().openOutputStream(configUri, "w");
            stream.write(jsonObject.toString(4).getBytes(StandardCharsets.UTF_8));
            stream.close();

            Log.i(TAG, "Successfully loaded " + channel_handles.size() + " channel handles from config.");

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for URI. The user may have revoked access. Resetting.", e);
            // Clear the invalid URI and ask the user to select the file again.
            clearUriFromPreferences();
            copyConfig();
            saveConfig();
            return;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Config file not found at URI. It may have been moved or deleted. Resetting.", e);
            clearUriFromPreferences();
            copyConfig();
            saveConfig();
            return;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write config file.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to generate JSON from config file.", e);
            Toast.makeText(activity, "Error: Invalid JSON format in config file.", Toast.LENGTH_LONG).show();
        }
    }


    private void copyConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, USER_CHANNELS_FILENAME);

        final ActivityResultLauncher<Intent> createDocumentLauncher =
                activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleFileSelection(uri);
                        }
                    }
                });

        Log.d(TAG, "Launching file picker to select destination for config file.");
        createDocumentLauncher.launch(intent);
    }

    /**
     * Handles the URI returned from the file picker. It checks if the file exists
     * and prompts the user for action if it does.
     *
     * @param uri The URI of the file selected by the user.
     */
    private void handleFileSelection(Uri uri) {
        // Check if the file already contains data. ACTION_CREATE_DOCUMENT can return a URI to an existing file.
        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri)) {
            if (inputStream != null && inputStream.available() > 0) {
                // File is not empty, it might be the one we want to overwrite.
                new AlertDialog.Builder(activity)
                        .setTitle("File Exists")
                        .setMessage("The file '" + USER_CHANNELS_FILENAME + "' already exists. Do you want to overwrite it with the default configuration?")
                        .setPositiveButton("Overwrite", (dialog, which) -> {
                            saveDefaultConfigToUri(uri);
                            saveUriToPreferences(uri);
                        })
                        .setNegativeButton("Keep Existing", (dialog, which) -> {
                            // User wants to keep the existing file, just save its URI
                            saveUriToPreferences(uri);
                            Toast.makeText(activity, "Using existing config file.", Toast.LENGTH_SHORT).show();
                        })
                        .setIcon(R.drawable.ban_video)
                        .show();
                return; // Wait for user decision
            }
        } catch (IOException e) {
            Log.e(TAG, "Error checking existing file. Assuming it's new.", e);
        }

        // If the file is new or empty, write the default config directly.
        saveDefaultConfigToUri(uri);
        saveUriToPreferences(uri);

        loadConfig();
    }

    /**
     * Copies the default 'channel_handles.json' from raw resources to the user-selected URI.
     *
     * @param targetUri The destination URI for the config file.
     */
    private void saveDefaultConfigToUri(Uri targetUri) {
        // The file com.channelplayer.R.raw.channel_handles is mentioned in your provided context
        try {
            InputStream inputStream = activity.getResources().openRawResource(R.raw.channel_handles);
            OutputStream outputStream = activity.getContentResolver().openOutputStream(targetUri);

            if (outputStream == null) throw new IOException("Failed to open output stream for URI: " + targetUri);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.i(TAG, "Successfully copied default config to " + targetUri);
            Toast.makeText(activity, "Configuration file saved.", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "Failed to save default config file.", e);
            Toast.makeText(activity, "Error saving configuration.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Saves the URI of the configuration file to SharedPreferences.
     */
    private void saveUriToPreferences(Uri uri) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CONFIG_FILE_URI, uri.toString());
        editor.apply();
        Log.i(TAG, "Saved config file URI to SharedPreferences: " + uri);
    }

    /**
     * Clears the URI of the configuration file from SharedPreferences.
     */
    private void clearUriFromPreferences() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CONFIG_FILE_URI).apply();
    }

    /**
     * Reads the entire content of a file from a given URI into a single String.
     */
    private String readTextFromUri(Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Loads the channel handles from the user-defined configuration file.
     * If no file has been configured, it initiates the copyConfig() flow.
     *
     * @return A list of channel handles, or an empty list if loading fails.
     */
    private void loadConfig() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_CONFIG_FILE_URI, null);

        if (uriString == null) {
            Log.d(TAG, "No config file URI found in SharedPreferences. Starting copyConfig().");
            copyConfig(); // Ask user to create/select the config file
            return;
        }

        Uri configUri = Uri.parse(uriString);

        try {
            // Persist permission to access the URI across device reboots
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            activity.getContentResolver().takePersistableUriPermission(configUri, takeFlags);

            // Read the file content
            String jsonContent = readTextFromUri(configUri);

            // Parse the JSON using org.json
            JSONObject jsonObject = new JSONObject(jsonContent);

            JSONArray handlesArray = jsonObject.getJSONArray("channel_handles");
            for (int i = 0; i < handlesArray.length(); i++) {
                channel_handles.add(handlesArray.getString(i));
            }

            JSONObject bannedObject = jsonObject.getJSONObject("banned_video_ids");
            Iterator<String> keys = bannedObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                banned_video_ids.put(key, bannedObject.getString(key));
            }
            Log.i(TAG, "Successfully loaded " + channel_handles.size() + " channel handles from config.");

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for URI. The user may have revoked access. Resetting.", e);
            // Clear the invalid URI and ask the user to select the file again.
            clearUriFromPreferences();
            copyConfig();
            return;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Config file not found at URI. It may have been moved or deleted. Resetting.", e);
            clearUriFromPreferences();
            copyConfig();
            return;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read config file.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON from config file. Ensure it is well-formed.", e);
            Toast.makeText(activity, "Error: Invalid JSON format in config file.", Toast.LENGTH_LONG).show();
        }
    }
}
