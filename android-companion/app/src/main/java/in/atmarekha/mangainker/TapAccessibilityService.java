package in.atmarekha.mangainker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class TapAccessibilityService extends AccessibilityService {
    public static final String ACTION_START = "in.atmarekha.mangainker.START_TAPS";
    public static final String ACTION_STOP = "in.atmarekha.mangainker.STOP_TAPS";
    public static final String ACTION_TARGET = "in.atmarekha.mangainker.SET_TAP_TARGET";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private float x = 540, y = 960;
    private long interval = 700;
    private BroadcastReceiver receiver;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_START.equals(action)) {
                    x = intent.getFloatExtra("x", x);
                    y = intent.getFloatExtra("y", y);
                    interval = Math.max(120, Math.min(60000, intent.getLongExtra("interval", 700)));
                    startTaps();
                } else if (ACTION_STOP.equals(action)) stopTaps();
                else if (ACTION_TARGET.equals(action)) {
                    x = intent.getFloatExtra("x", x);
                    y = intent.getFloatExtra("y", y);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_TARGET);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    private final Runnable tapLoop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 65);
            dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
            handler.postDelayed(this, interval);
        }
    };

    private void startTaps() {
        stopTaps();
        running = true;
        handler.post(tapLoop);
    }

    private void stopTaps() {
        running = false;
        handler.removeCallbacks(tapLoop);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { stopTaps(); }
    @Override public void onDestroy() {
        stopTaps();
        if (receiver != null) unregisterReceiver(receiver);
        super.onDestroy();
    }
}
