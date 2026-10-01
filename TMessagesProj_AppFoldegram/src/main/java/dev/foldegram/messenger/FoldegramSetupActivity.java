package dev.foldegram.messenger;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Offline packaging check. This deliberately contains no Telegram initialization or login. */
public final class FoldegramSetupActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(21, 39, 51));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        int padding = dp(32);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.foldegram_launcher);
        icon.setContentDescription(getString(R.string.foldegram_app_name));
        content.addView(icon, new LinearLayout.LayoutParams(dp(120), dp(120)));
        addText(content, getString(R.string.foldegram_app_name), 32);
        addText(content, getString(R.string.foldegram_setup_title), 21);
        addText(content, getString(R.string.foldegram_setup_body), 16);
        setContentView(scroll);
    }

    private void addText(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(243, 239, 231));
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(12), 0, dp(12));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
