// In file: app/src/main/java/com/channelplayer/ViewOnlyWebView.java
package com.channelplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ViewOnlyWebView extends WebView {

    private JSONObject allowedRect = new JSONObject(new HashMap<String, Integer>() {{
        put("left", 0);
        put("top", 0);
        put("width", 0);
        put("height", 0);
    }});

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
        try {
            if (
                    allowedRect.getInt("width") > 0 && allowedRect.getInt("height") > 0 &&
                    allowedRect.getInt("left") <= event.getX() && event.getX() <= allowedRect.getInt("left") + allowedRect.getInt("width") &&
                    allowedRect.getInt("top") <= event.getY() && event.getY() <= allowedRect.getInt("top") + allowedRect.getInt("height")
            ) {
                return super.onTouchEvent(event);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        // We consume the event and do nothing with it.
        // This effectively makes the WebView "read-only" to user touch input.
        Log.i("ViewOnlyWebView", "onTouchEvent: Consumed touch event at " + event.getX() + ", " + event.getY() + ".");
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        Log.i("ViewOnlyWebView", "performClick: Consumed click event.");
        return true;
    }

    public void allowTouch(JSONObject rect) {
        allowedRect = rect;

        try {
            if (rect.getInt("width") > 0 && rect.getInt("height") > 0) {
                Log.i("ViewOnlyWebView", "allowTouch: Allowed touch at " + rect.toString() + ".");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
