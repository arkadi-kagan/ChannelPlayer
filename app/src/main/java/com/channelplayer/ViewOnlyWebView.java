// In file: app/src/main/java/com/channelplayer/ViewOnlyWebView.java
package com.channelplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ViewOnlyWebView extends WebView {

    // These constructors are needed to use the view in XML layouts
    public ViewOnlyWebView(@NonNull Context context) {
        super(context);
    }

    public ViewOnlyWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewOnlyWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * This is the magic method. It gets called for every touch event.
     * By returning 'true', we are telling the system "I have handled this touch event,"
     * which prevents the event from being passed down to the WebView's own internal
     * touch handling logic (like clicking links or scrolling).
     * The 'SuppressLint' is to suppress a lint warning about not calling super.onTouchEvent,
     * which is intentional here.
     */
    @SuppressLint({"ClickableInScrollableWidget", "ClickableViewAccessibility"})
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We consume the event and do nothing with it.
        // This effectively makes the WebView "read-only" to user touch input.
        Log.i("ViewOnlyWebView", "onTouchEvent: Consumed touch event.");
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        Log.i("ViewOnlyWebView", "performClick: Consumed click event.");
        return true;
    }
}
