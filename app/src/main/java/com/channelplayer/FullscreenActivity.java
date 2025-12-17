package com.channelplayer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.channelplayer.cache.ConfigRepository;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

public class FullscreenActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";
    private GoogleSignInClient mGoogleSignInClient;

    // This launcher handles the result from the Google Sign-In activity.
    // It replaces the old, deprecated onActivityResult method.
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                    Intent data = result.getData();
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    handleSignInResult(task);
                } else {
                    Log.w(TAG, "Sign-in flow was cancelled by user. Result code: " + result.getResultCode());
                    Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the content view to our new layout with the sign-in button
        setContentView(R.layout.activity_fullscreen);

        ConfigRepository.getInstance(this);

        // 1. Configure Google Sign-In
        // We request the user's basic profile and permission to read YouTube data.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope("https://www.googleapis.com/auth/youtube.readonly"))
                .requestEmail()
                .build();

        // 2. Build the client that will manage the entire sign-in process
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 3. Find the sign-in button in our layout and set its click listener
        SignInButton signInButton = findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(v -> signIn());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if a user is already signed in from a previous session
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // If they are, skip the login screen and go directly to the next activity.
            Log.d(TAG, "User already signed in. Navigating to channel list.");
            navigateToChannelList();
        }
    }

    // This method is called when the user clicks the sign-in button.
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        // This launches the Google-managed Sign-In UI
        signInLauncher.launch(signInIntent);
    }

    // This method is called after the user returns from the Google Sign-In UI.
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            // If sign-in was successful, we get the account object.
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "signInResult: success for " + account.getEmail());
            // Now that we are signed in, proceed to the main part of the app.
            navigateToChannelList();
        } catch (ApiException e) {
            // If sign-in failed, log the error and show a message to the user.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode(), e);
            Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void navigateToChannelList() {
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();

        // Create an Intent to start the ChannelListActivity
        Intent intent = new Intent(FullscreenActivity.this, ChannelListActivity.class);
        startActivity(intent);

        // Close the login activity so the user can't press "back" to return to it
        finish();
    }
}
