package in.atmarekha.mangainker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setBackgroundColor(0xff101216);
        TextView title = new TextView(this);
        title.setText("MangaInker Floating Tools");
        title.setTextColor(0xffffffff);
        title.setTextSize(23);
        title.setPadding(0, 0, 0, 18);
        root.addView(title);
        TextView info = new TextView(this);
        info.setText("Use the floating bubble over other apps. Tap automation is optional and only runs after you press Start. Android requires you to grant overlay and accessibility permissions manually.");
        info.setTextColor(0xffc6cbd3);
        info.setTextSize(15);
        info.setPadding(0, 0, 0, 22);
        root.addView(info);
        Button overlay = new Button(this);
        overlay.setText("1. Allow floating overlay");
        overlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                startOverlay();
            }
        });
        root.addView(overlay);
        Button accessibility = new Button(this);
        accessibility.setText("2. Enable tap accessibility");
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(accessibility);
        Button start = new Button(this);
        start.setText("Start floating tools");
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } else startOverlay();
        });
        root.addView(start);
        TextView note = new TextView(this);
        note.setText("To choose a tap location, open the bubble, select Set target, drag the target marker, and confirm. The clicker will tap only that point at the interval you choose.");
        note.setTextColor(0xffaeb5c0);
        note.setTextSize(13);
        note.setPadding(0, 22, 0, 0);
        root.addView(note);
        setContentView(root);
    }

    private void startOverlay() {
        Intent intent = new Intent(this, FloatingOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        finish();
    }
}
