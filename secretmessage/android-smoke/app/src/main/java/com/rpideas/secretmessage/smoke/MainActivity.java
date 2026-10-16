package com.rpideas.secretmessage.smoke;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone MLS and direct-LAN feasibility harness. Not the Secret Message product app. */
public final class MainActivity extends Activity {
    private static final String TAG_MLS = "MLS_SMOKE";
    private static final String TAG_LAN = "LAN_SMOKE";
    private static final String NATIVE_LIBRARY = "secretmessage_mls_spike";
    private static final long SAFETY_POLL_MS = 400;

    private final AtomicBoolean lanTaskRunning = new AtomicBoolean(false);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable safetyCodePoller = this::pollSafetyCode;
    private ScrollView scrollView;
    private LinearLayout safetyPanel;
    private TextView safetyCodeView;
    private Button codesMatchButton;
    private Button codesDifferButton;
    private String shownSafetyCode = "";
    private String safetyCheckResult = "Not reached";
    private TextView deviceInfo;
    private TextView localStatusView;
    private TextView lanStatusView;
    private TextView invitePreview;
    private EditText inviteInput;
    private Button shareInviteButton;
    private String localResult = "Not run";
    private String lanResult = "Not run";
    private String inviteUri;

    private native String runNativeSmoke();
    private native String runNativeLanHost(String hostIp, int port, String token);
    private native String runNativeLanJoin(String hostIp, int port, String token);
    private native String nativeLanSafetyCode();
    private native String nativeSubmitLanSafetyDecision(int codesMatch);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        acceptInviteFromIntent(getIntent());

        // The hosted emulator exercises the original local-only MLS smoke path.
        if (getIntent().getBooleanExtra("run_mls_smoke", false)) {
            localStatusView.post(this::runLocalSmoke);
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(safetyCodePoller);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptInviteFromIntent(intent);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Secret Message — MLS LAN test");
        title.setTextSize(22);
        root.addView(title, matchWrap());

        TextView notice = new TextView(this);
        notice.setText(
                "TEST ONLY — NOT A SECURE CHAT APP. The local MLS button runs synthetic members "
                        + "on this device. The LAN test sends only fixed synthetic MLS messages "
                        + "between devices on the same Wi-Fi, and only after both people confirm that "
                        + "their phones show the same MLS safety code. Identities are temporary test "
                        + "keys, nothing is saved, and it must not be used for private or real "
                        + "messages. SMS shares only a short-lived local invite; the invite is not "
                        + "identity verification, the safety-code comparison is.");
        notice.setTextSize(15);
        addWithTopMargin(root, notice, 12);

        deviceInfo = new TextView(this);
        deviceInfo.setText(deviceReport());
        deviceInfo.setTextSize(14);
        addWithTopMargin(root, deviceInfo, 12);

        Button localButton = new Button(this);
        localButton.setText("Run local MLS smoke test");
        addWithTopMargin(root, localButton, 18);
        localStatusView = new TextView(this);
        localStatusView.setText("Local MLS: " + localResult);
        localStatusView.setTextSize(15);
        addWithTopMargin(root, localStatusView, 8);
        localButton.setOnClickListener(view -> runLocalSmoke());

        if (BuildConfig.LAN_TEST_ENABLED) {
            TextView lanTitle = new TextView(this);
            lanTitle.setText("Same-Wi-Fi MLS connection test");
            lanTitle.setTextSize(18);
            addWithTopMargin(root, lanTitle, 24);

            TextView lanNotice = new TextView(this);
            lanNotice.setText(
                    "Put both devices on the same non-guest Wi-Fi. One device creates and shares an "
                            + "invite; the other opens or pastes it and taps Join. Both phones then "
                            + "show a 20-digit safety code: compare them side by side and tap Codes "
                            + "match on both phones only if every digit is the same. The invite "
                            + "expires after about 3 minutes. Router client isolation can block this "
                            + "test.");
            lanNotice.setTextSize(14);
            addWithTopMargin(root, lanNotice, 8);

            Button hostButton = new Button(this);
            hostButton.setText("Create local invite / start host");
            addWithTopMargin(root, hostButton, 12);
            hostButton.setOnClickListener(view -> startLanHost());

            shareInviteButton = new Button(this);
            shareInviteButton.setText("Share invite (SMS or another app)");
            shareInviteButton.setEnabled(false);
            addWithTopMargin(root, shareInviteButton, 8);
            shareInviteButton.setOnClickListener(view -> shareInvite());

            invitePreview = new TextView(this);
            invitePreview.setText("No active invite");
            invitePreview.setTextSize(13);
            addWithTopMargin(root, invitePreview, 8);

            inviteInput = new EditText(this);
            inviteInput.setSingleLine(false);
            inviteInput.setMinLines(2);
            inviteInput.setTextSize(14);
            inviteInput.setHint("Open or paste secretmessage://join?... invite here");
            addWithTopMargin(root, inviteInput, 12);

            Button joinButton = new Button(this);
            joinButton.setText("Join local invite");
            addWithTopMargin(root, joinButton, 8);
            joinButton.setOnClickListener(view -> joinInvite());

            lanStatusView = new TextView(this);
            lanStatusView.setText("LAN MLS: " + lanResult);
            lanStatusView.setTextSize(15);
            addWithTopMargin(root, lanStatusView, 8);

            safetyPanel = new LinearLayout(this);
            safetyPanel.setOrientation(LinearLayout.VERTICAL);
            safetyPanel.setVisibility(View.GONE);
            addWithTopMargin(root, safetyPanel, 12);

            TextView safetyTitle = new TextView(this);
            safetyTitle.setText("Compare the safety code");
            safetyTitle.setTextSize(18);
            safetyPanel.addView(safetyTitle, matchWrap());

            safetyCodeView = new TextView(this);
            safetyCodeView.setTextSize(30);
            safetyCodeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            safetyCodeView.setGravity(Gravity.CENTER);
            addWithTopMargin(safetyPanel, safetyCodeView, 8);

            TextView safetyHint = new TextView(this);
            safetyHint.setText(
                    "Both phones must show exactly the same 20 digits. Hold them side by side and "
                            + "check every group. Tap Codes match only if all digits are the same. "
                            + "If any digit differs, tap Codes differ: another device may be "
                            + "intercepting the connection.");
            safetyHint.setTextSize(14);
            addWithTopMargin(safetyPanel, safetyHint, 8);

            codesMatchButton = new Button(this);
            codesMatchButton.setText("Codes match");
            addWithTopMargin(safetyPanel, codesMatchButton, 8);
            codesMatchButton.setOnClickListener(view -> answerSafetyCode(true));

            codesDifferButton = new Button(this);
            codesDifferButton.setText("Codes differ");
            addWithTopMargin(safetyPanel, codesDifferButton, 8);
            codesDifferButton.setOnClickListener(view -> answerSafetyCode(false));
        }

        Button copyButton = new Button(this);
        copyButton.setText("Copy device and test results");
        addWithTopMargin(root, copyButton, 18);
        copyButton.setOnClickListener(view -> copyDiagnostics());

        setContentView(scroll);
    }

    private void runLocalSmoke() {
        localStatusView.setText("Local MLS: Running the in-memory test…");
        new Thread(() -> {
            String result;
            try {
                System.loadLibrary(NATIVE_LIBRARY);
                result = runNativeSmoke();
                if (result == null) result = "FAIL: native test returned no status.";
            } catch (Throwable failure) {
                result = "FAIL: " + failure.getClass().getSimpleName()
                        + " while loading or calling the test-only native bridge.";
            }
            final String displayResult = result;
            runOnUiThread(() -> {
                localResult = displayResult;
                localStatusView.setText("Local MLS: " + displayResult);
                Log.i(TAG_MLS, displayResult);
            });
        }, "secretmessage-mls-local-smoke").start();
    }

    private void startLanHost() {
        if (!lanTaskRunning.compareAndSet(false, true)) {
            toast("A LAN test is already running on this device.");
            return;
        }
        final String hostIp;
        try {
            hostIp = findPrivateIpv4();
        } catch (Exception error) {
            lanTaskRunning.set(false);
            lanResult = "FAIL: " + error.getMessage();
            lanStatusView.setText("LAN MLS: " + lanResult);
            Log.e(TAG_LAN, lanResult, error);
            return;
        }
        resetSafetyCheck();
        final int port = 40000 + new SecureRandom().nextInt(20000);
        final String token = newInviteToken();
        inviteUri = new Uri.Builder()
                .scheme("secretmessage")
                .authority("join")
                .appendQueryParameter("host", hostIp)
                .appendQueryParameter("port", Integer.toString(port))
                .appendQueryParameter("token", token)
                .build()
                .toString();
        invitePreview.setText("Local invite (keep it private; valid only for this test):\n" + inviteUri);
        shareInviteButton.setEnabled(true);
        lanResult = "Host starting on a private LAN interface; share the invite now.";
        lanStatusView.setText("LAN MLS: " + lanResult);
        Log.i(TAG_LAN, "Host starting on private LAN address; invite token is not logged.");

        new Thread(() -> {
            String result;
            try {
                System.loadLibrary(NATIVE_LIBRARY);
                result = runNativeLanHost(hostIp, port, token);
                if (result == null) result = "FAIL: native host returned no status.";
            } catch (Throwable failure) {
                result = "FAIL: " + failure.getClass().getSimpleName()
                        + " while starting or running the LAN host.";
            }
            finishLanTask(result);
        }, "secretmessage-mls-lan-host").start();
        startSafetyCodePolling();
    }

    private void shareInvite() {
        if (inviteUri == null) {
            toast("Create a local invite first.");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,
                "Secret Message LOCAL MLS TEST invite (same Wi-Fi only):\n" + inviteUri);
        startActivity(Intent.createChooser(send, "Share the local test invite"));
    }

    private void joinInvite() {
        if (!lanTaskRunning.compareAndSet(false, true)) {
            toast("A LAN test is already running on this device.");
            return;
        }
        final Invite invite;
        try {
            invite = parseInvite(inviteInput.getText().toString());
        } catch (Exception error) {
            lanTaskRunning.set(false);
            lanResult = "FAIL: " + error.getMessage();
            lanStatusView.setText("LAN MLS: " + lanResult);
            return;
        }
        resetSafetyCheck();
        lanResult = "Connecting directly to the private LAN host; no relay is used.";
        lanStatusView.setText("LAN MLS: " + lanResult);
        new Thread(() -> {
            String result;
            try {
                System.loadLibrary(NATIVE_LIBRARY);
                result = runNativeLanJoin(invite.host, invite.port, invite.token);
                if (result == null) result = "FAIL: native guest returned no status.";
            } catch (Throwable failure) {
                result = "FAIL: " + failure.getClass().getSimpleName()
                        + " while joining the local MLS test.";
            }
            finishLanTask(result);
        }, "secretmessage-mls-lan-join").start();
        startSafetyCodePolling();
    }

    private void finishLanTask(String result) {
        runOnUiThread(() -> {
            lanResult = result;
            lanStatusView.setText("LAN MLS: " + result);
            Log.i(TAG_LAN, result);
            lanTaskRunning.set(false);
            uiHandler.removeCallbacks(safetyCodePoller);
            if (safetyPanel != null) safetyPanel.setVisibility(View.GONE);
            shownSafetyCode = "";
            inviteUri = null;
            if (shareInviteButton != null) shareInviteButton.setEnabled(false);
            if (invitePreview != null) invitePreview.setText("No active invite");
            updateDiagnostics();
        });
    }

    private void resetSafetyCheck() {
        shownSafetyCode = "";
        safetyCheckResult = "Not reached";
        if (safetyPanel != null) safetyPanel.setVisibility(View.GONE);
    }

    private void startSafetyCodePolling() {
        try {
            // Idempotent; the worker thread reports any load failure as the test result.
            System.loadLibrary(NATIVE_LIBRARY);
        } catch (Throwable reportedByWorker) {
            // Polling keeps retrying until the worker finishes.
        }
        uiHandler.removeCallbacks(safetyCodePoller);
        uiHandler.postDelayed(safetyCodePoller, SAFETY_POLL_MS);
    }

    private void pollSafetyCode() {
        if (!lanTaskRunning.get()) return;
        String code = "";
        try {
            code = nativeLanSafetyCode();
        } catch (Throwable notLoadedYet) {
            // The worker thread may still be loading the test-only native library.
        }
        if (code != null && !code.isEmpty() && !code.equals(shownSafetyCode)) {
            showSafetyCode(code);
        }
        uiHandler.postDelayed(safetyCodePoller, SAFETY_POLL_MS);
    }

    private void showSafetyCode(String code) {
        shownSafetyCode = code;
        safetyCheckResult = "Shown on this phone: " + code + " (not answered)";
        safetyCodeView.setText(twoLineCode(code));
        codesMatchButton.setEnabled(true);
        codesDifferButton.setEnabled(true);
        safetyPanel.setVisibility(View.VISIBLE);
        lanResult = "Compare the safety code with the other phone, then answer below.";
        lanStatusView.setText("LAN MLS: " + lanResult);
        scrollView.post(() -> scrollView.smoothScrollTo(0, safetyPanel.getTop()));
    }

    private void answerSafetyCode(boolean codesMatch) {
        codesMatchButton.setEnabled(false);
        codesDifferButton.setEnabled(false);
        String reply;
        try {
            reply = nativeSubmitLanSafetyDecision(codesMatch ? 1 : 0);
        } catch (Throwable failure) {
            reply = "Could not pass the answer: " + failure.getClass().getSimpleName();
        }
        safetyCheckResult = (codesMatch ? "Confirmed on this phone: " : "Reported different on this phone: ")
                + shownSafetyCode;
        lanResult = codesMatch
                ? "You confirmed the code; waiting for the other phone's answer."
                : "You reported different codes; telling the other phone and stopping.";
        lanStatusView.setText("LAN MLS: " + lanResult + " (" + reply + ")");
        Log.i(TAG_LAN, codesMatch
                ? "This phone's user confirmed the safety code."
                : "This phone's user reported a different safety code.");
    }

    /** Shows the four groups on two lines so the code fits a narrow phone at a large size. */
    private static String twoLineCode(String code) {
        String[] groups = code.trim().split(" ");
        if (groups.length != 4) return code;
        return groups[0] + " " + groups[1] + "\n" + groups[2] + " " + groups[3];
    }

    private void acceptInviteFromIntent(Intent intent) {
        if (inviteInput == null || intent == null) return;
        Uri data = intent.getData();
        if (data != null && "secretmessage".equals(data.getScheme())
                && "join".equals(data.getHost())) {
            inviteInput.setText(data.toString());
            lanResult = "Invite opened; tap Join local invite to connect.";
            if (lanStatusView != null) lanStatusView.setText("LAN MLS: " + lanResult);
        } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && sharedText.toString().contains("secretmessage://join")) {
                inviteInput.setText(extractInviteUri(sharedText.toString()));
            }
        }
    }

    private void copyDiagnostics() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Secret Message test diagnostics", buildDiagnostics()));
        toast("Device and test results copied. Paste them into the chat.");
    }

    private String buildDiagnostics() {
        return deviceReport()
                + "\nLocal MLS smoke: " + localResult
                + "\nLAN MLS smoke: " + lanResult
                + "\nSafety code check: " + safetyCheckResult
                + "\nScope: fixed synthetic MLS messages only; no chat history or user text.";
    }

    private void updateDiagnostics() {
        if (deviceInfo != null) deviceInfo.setText(deviceReport());
    }

    private String deviceReport() {
        String abi = Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
        return "Test APK: " + BuildConfig.VERSION_NAME
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nABI: " + abi;
    }

    private Invite parseInvite(String rawText) throws IllegalArgumentException {
        String link = extractInviteUri(rawText == null ? "" : rawText.trim());
        Uri uri = Uri.parse(link);
        if (!"secretmessage".equals(uri.getScheme()) || !"join".equals(uri.getHost())) {
            throw new IllegalArgumentException("Paste or open a secretmessage://join invite.");
        }
        String host = uri.getQueryParameter("host");
        String portString = uri.getQueryParameter("port");
        String token = uri.getQueryParameter("token");
        if (host == null || !isPrivateIpv4(host) || portString == null || token == null) {
            throw new IllegalArgumentException("Invite is missing a private LAN address, port, or token.");
        }
        final int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invite port is invalid.");
        }
        if (port < 1 || port > 65535 || token.length() != 32) {
            throw new IllegalArgumentException("Invite port or token is invalid.");
        }
        for (int i = 0; i < token.length(); i++) {
            if (Character.digit(token.charAt(i), 16) < 0) {
                throw new IllegalArgumentException("Invite token is invalid.");
            }
        }
        return new Invite(host, port, token);
    }

    private static String extractInviteUri(String rawText) {
        int start = rawText.indexOf("secretmessage://join");
        if (start < 0) return rawText.trim();
        int end = rawText.indexOf('\n', start);
        String result = end < 0 ? rawText.substring(start) : rawText.substring(start, end);
        return result.trim();
    }

    private static String findPrivateIpv4() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) throw new IllegalStateException("No active network interface was found.");
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) continue;
            String name = network.getName().toLowerCase(Locale.ROOT);
            boolean localNetworkInterface = name.startsWith("wlan") || name.startsWith("wifi")
                    || name.startsWith("eth") || name.startsWith("en") || name.startsWith("ap")
                    || name.startsWith("swlan");
            if (!localNetworkInterface) continue;
            Enumeration<InetAddress> addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && isPrivateIpv4(address.getHostAddress())) {
                    return address.getHostAddress();
                }
            }
        }
        throw new IllegalStateException(
                "No private Wi-Fi/Ethernet IPv4 address was found. Join the same Wi-Fi and disable any VPN.");
    }

    private static boolean isPrivateIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
        } catch (NumberFormatException error) {
            return false;
        }
        return octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    private static String newInviteToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder token = new StringBuilder(32);
        for (byte value : bytes) token.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return token.toString();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addWithTopMargin(LinearLayout root, android.view.View view, int marginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(marginDp);
        root.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Invite {
        final String host;
        final int port;
        final String token;

        Invite(String host, int port, String token) {
            this.host = host;
            this.port = port;
            this.token = token;
        }
    }
}
