package com.rpideas.secretmessage.smoke;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Standalone JNI feasibility harness. This is not the Secret Message product app. */
public final class MainActivity extends Activity {
    private static final String NATIVE_LIBRARY = "secretmessage_mls_spike";

    private native String runNativeSmoke();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Secret Message — MLS JNI test");
        title.setTextSize(22);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView notice = new TextView(this);
        notice.setText(
                "TEST ONLY. This runs two synthetic MLS members in memory on this phone. "
                        + "It uses no network, verifies no real identity, stores no group or "
                        + "message state, and is not a secure messaging app.");
        notice.setTextSize(16);
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeParams.topMargin = dp(16);
        root.addView(notice, noticeParams);

        Button run = new Button(this);
        run.setText("Run local MLS smoke test");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(24);
        root.addView(run, buttonParams);

        TextView status = new TextView(this);
        status.setText("Not run");
        status.setTextSize(16);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        root.addView(status, statusParams);

        setContentView(root);

        run.setOnClickListener(view -> {
            run.setEnabled(false);
            status.setText("Running the in-memory native test…");
            Thread worker = new Thread(() -> {
                String result;
                try {
                    System.loadLibrary(NATIVE_LIBRARY);
                    result = runNativeSmoke();
                    if (result == null) {
                        result = "FAIL: native test returned no status.";
                    }
                } catch (Throwable failure) {
                    // Keep the UI useful if the ABI/library/JNI symbol is missing.
                    result = "FAIL: " + failure.getClass().getSimpleName()
                            + " while loading or calling the test-only native bridge.";
                }

                final String displayResult = result;
                runOnUiThread(() -> {
                    status.setText(displayResult);
                    run.setEnabled(true);
                });
            }, "secretmessage-mls-smoke");
            worker.start();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
