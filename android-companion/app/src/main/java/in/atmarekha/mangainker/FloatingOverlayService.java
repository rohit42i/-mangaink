package in.atmarekha.mangainker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;

public class FloatingOverlayService extends Service {
    private WindowManager windowManager;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams targetParams;
    private View bubble, panel, target;
    private float tapX = 540, tapY = 960;
    private boolean panelOpen = false;
    private long intervalMs = 700;
    private static final String CHANNEL = "mangainker_overlay";

    @Override public void onCreate() {
        super.onCreate();
        createNotification();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    private void createNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Floating tools", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setContentTitle("MangaInker floating tools active")
            .setContentText("Your floating controls are available over other apps.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build();
        startForeground(41, notification);
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(12, 10, 12, 10);
        view.setBackgroundColor(0xee171a20);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void showBubble() {
        TextView icon = new TextView(this);
        icon.setText("MI");
        icon.setTextColor(Color.BLACK);
        icon.setTextSize(16);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundColor(0xffd4f36a);
        bubble = icon;
        bubbleParams = new WindowManager.LayoutParams(58, 58, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 24; bubbleParams.y = 260;
        icon.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX=e.getRawX(); downY=e.getRawY(); startX=bubbleParams.x; startY=bubbleParams.y; moved=false; return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    int dx=(int)(e.getRawX()-downX), dy=(int)(e.getRawY()-downY);
                    if (Math.abs(dx)+Math.abs(dy)>6) moved=true;
                    if (moved) { bubbleParams.x=startX+dx; bubbleParams.y=startY+dy; windowManager.updateViewLayout(bubble,bubbleParams); }
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) { if (!moved) togglePanel(); return true; }
                return true;
            }
        });
        windowManager.addView(bubble, bubbleParams);
    }

    private void togglePanel() {
        if (panelOpen) { removePanel(); return; }
        panelOpen = true;
        LinearLayout box = column();
        TextView heading = new TextView(this);
        heading.setText("MangaInker controls");
        heading.setTextColor(Color.WHITE); heading.setTextSize(15);
        box.addView(heading);
        box.addView(button("Set tap target", this::showTarget));
        TextView speed = new TextView(this);
        speed.setTextColor(Color.WHITE); speed.setTextSize(13);
        speed.setText("Tap interval: " + intervalMs + " ms");
        box.addView(speed);
        box.addView(button("Slower  +100 ms", () -> { intervalMs = Math.min(60000, intervalMs + 100); speed.setText("Tap interval: " + intervalMs + " ms"); }));
        box.addView(button("Faster  -100 ms", () -> { intervalMs = Math.max(120, intervalMs - 100); speed.setText("Tap interval: " + intervalMs + " ms"); }));
        box.addView(button("Start auto tap", () -> {
            if (!isAccessibilityEnabled()) {
                Intent settings = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settings);
                return;
            }
            Intent start = new Intent(TapAccessibilityService.ACTION_START);
            start.setPackage(getPackageName());
            start.putExtra("x", tapX); start.putExtra("y", tapY);
            start.putExtra("interval", intervalMs);
            sendBroadcast(start);
        }));
        box.addView(button("Stop auto tap", () -> sendBroadcast(new Intent(TapAccessibilityService.ACTION_STOP).setPackage(getPackageName()))));
        box.addView(button("Close panel", this::removePanel));
        panel = box;
        panelParams = new WindowManager.LayoutParams(250, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = Math.max(8, bubbleParams.x - 190);
        panelParams.y = bubbleParams.y + 66;
        windowManager.addView(panel, panelParams);
    }

    private boolean isAccessibilityEnabled() {
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.toLowerCase().contains(getPackageName().toLowerCase());
    }

    private void removePanel() {
        if (panel != null) { windowManager.removeView(panel); panel = null; }
        panelOpen = false;
    }

    private void showTarget() {
        if (target != null) windowManager.removeView(target);
        LinearLayout marker = new LinearLayout(this);
        marker.setOrientation(LinearLayout.HORIZONTAL);
        marker.setPadding(4,4,4,4);
        marker.setBackgroundColor(0xdd15191f);
        TextView handle = new TextView(this);
        handle.setText("⊙");
        handle.setTextSize(30); handle.setTextColor(0xffd4f36a); handle.setPadding(8,2,8,2);
        marker.addView(handle);
        marker.addView(button("Use point", () -> {
            tapX = targetParams.x + 18;
            tapY = targetParams.y + 18;
            Intent chosen = new Intent(TapAccessibilityService.ACTION_TARGET).setPackage(getPackageName());
            chosen.putExtra("x", tapX); chosen.putExtra("y", tapY); sendBroadcast(chosen);
            windowManager.removeView(target); target = null;
        }));
        target = marker;
        targetParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        targetParams.gravity = Gravity.TOP | Gravity.START;
        targetParams.x = Math.max(0, bubbleParams.x - 80); targetParams.y = Math.max(80, bubbleParams.y);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction()==MotionEvent.ACTION_DOWN) { downX=e.getRawX(); downY=e.getRawY(); startX=targetParams.x; startY=targetParams.y; return true; }
                if (e.getAction()==MotionEvent.ACTION_MOVE) { targetParams.x=startX+(int)(e.getRawX()-downX); targetParams.y=startY+(int)(e.getRawY()-downY); windowManager.updateViewLayout(target,targetParams); return true; }
                return true;
            }
        });
        windowManager.addView(target, targetParams);
        removePanel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() {
        removePanel();
        if (target != null) windowManager.removeView(target);
        if (bubble != null) windowManager.removeView(bubble);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
