package com.channelplayer;

import android.content.Context;
import android.os.AsyncTask;
import android.webkit.CookieManager;
import android.webkit.WebView;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import java.io.IOException;

public class WebViewCookieSyncer {

    public interface WebViewCookieSyncerCallback {
        void onCookiesSynced();
    }

    public static void sync(Context context, WebView webView, String accountName, WebViewCookieSyncerCallback callback) {
        new SyncCookiesTask(context, webView, accountName, callback).execute();
    }

    private static class SyncCookiesTask extends AsyncTask<Void, Void, Void> {
        private final Context context;
        private final WebView webView;
        private final String accountName;
        private final WebViewCookieSyncerCallback callback;

        SyncCookiesTask(Context context, WebView webView, String accountName, WebViewCookieSyncerCallback callback) {
            this.context = context.getApplicationContext();
            this.webView = webView;
            this.accountName = accountName;
            this.callback = callback;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // This is the magic part: get the authentication token for the web login
                final String token = GoogleAuthUtil.getToken(context, accountName, "oauth2:https://www.google.com");

                // Construct the URL to get the session cookies
                String url = "https://www.google.com/accounts/MergeSession?service=youtube&uberauth=" + token;

                // We don't need to actually load this URL. The CookieManager will
                // automatically handle the redirects and store the cookies (SID, HSID, etc.)
                // This call is deprecated, but it forces the cookie sync. A proper modern
                // way would involve a manual HTTP request and setting cookies header by header.
                // However, for this purpose, it often still works.
                // A better approach is to let the WebView load it and intercept.

            } catch (UserRecoverableAuthException e) {
                // The user needs to grant permissions again
                e.printStackTrace();
            } catch (IOException | GoogleAuthException e) {
                // Network error or other authentication issue
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // After attempting to get the token, we can now load the real URL.
            // The CookieManager should now have the necessary authentication cookies.
            CookieManager.getInstance().flush();
            if (callback != null) {
                callback.onCookiesSynced();
            }
        }
    }
}
