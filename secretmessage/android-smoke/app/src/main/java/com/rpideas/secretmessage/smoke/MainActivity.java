package com.rpideas.secretmessage.smoke;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputFilter;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Standalone MLS and direct-LAN feasibility harness. Not the Secret Message product app. */
public final class MainActivity extends Activity {
    private static final String TAG_MLS = "MLS_SMOKE";
    private static final String TAG_LAN = "LAN_SMOKE";
    private static final String NATIVE_LIBRARY = "secretmessage_mls_spike";
    private static final long LAN_POLL_MS = 250;
    /** Must match the protocol version in the native LAN test; older invites are refused. */
    private static final String INVITE_VERSION = "6";
    /** The native bridge's reply when this phone's answer reached the waiting comparison. */
    private static final String ANSWER_ACCEPTED = "Answer passed to the LAN test.";
    private static final int MAX_TYPED_CHARS = 300;
    private static final int MAX_CHAT_LINES = 200;
    private static final String PREFS = "secretmessage_lan_test";
    private static final String PREF_DEVICE_ID = "device_id";
    private static final String PREF_IDENTITY = "identity_v1";
    private static final String PREF_PEER_PINS = "peer_pins_v1";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String WRAP_KEY_ALIAS = "secretmessage_lan_test_identity_wrap";
    private static final int GCM_TAG_BITS = 128;

    private final AtomicBoolean lanTaskRunning = new AtomicBoolean(false);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable lanPoller = this::pollLanTest;
    private ScrollView scrollView;
    private LinearLayout safetyPanel;
    private TextView safetyCodeView;
    private Button codesMatchButton;
    private Button codesDifferButton;
    private String shownSafetyCode = "";
    private String safetyCheckResult = "Not reached";
    private TextView identityView;
    private TextView safetyPeerView;
    private int defaultTextColor = Color.DKGRAY;
    private String lastPeerStatus = "Not reached";
    private TextView deviceInfo;
    private TextView localStatusView;
    private TextView lanStatusView;
    private TextView invitePreview;
    private EditText inviteInput;
    private Button shareInviteButton;
    private String localResult = "Not run";
    private String lanResult = "Not run";
    private String inviteUri;
    private LinearLayout chatPanel;
    private TextView chatLogView;
    private TextView chatStatusView;
    private EditText chatInput;
    private Button chatSendButton;
    private Button chatEndButton;
    /** Typed test messages live only here, in memory, until the next test or app exit. */
    private final List<ChatLine> chatLines = new ArrayList<>();
    private boolean chatOpen;
    private int chatSent;
    private int chatDelivered;
    private int chatReceived;
    private String typedTestResult = "Not reached";

    private native String runNativeSmoke();
    private native String runNativeLanHost(String hostIp, int port, String token, String identity, String pins);
    private native String runNativeLanJoin(String hostIp, int port, String token, String identity, String pins);
    private native String nativeLanSafetyPrompt();
    private native String nativeSubmitLanSafetyDecision(int codesMatch);
    private native String nativeGenerateLanIdentity(String deviceId);
    private native String nativeTakeLanVerifiedPeer();
    private native String nativeLanChatSend(String text);
    private native String nativeLanChatPoll();
    private native String nativeLanChatEnd();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildUi();
        acceptInviteFromIntent(getIntent());

        // The hosted emulator exercises the original local-only MLS smoke path.
        if (getIntent().getBooleanExtra("run_mls_smoke", false)) {
            localStatusView.post(this::runLocalSmoke);
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(lanPoller);
        chatLines.clear();
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
                        + "on this device. The LAN test connects two phones on the same Wi-Fi. Only "
                        + "after both people confirm that their phones show the same MLS safety code "
                        + "does it exchange fixed MLS test messages and then let the two people type "
                        + "short test messages, which are encrypted with MLS, shown only on the two "
                        + "screens and never saved. Each phone keeps one test key, stored encrypted "
                        + "with an Android Keystore key, remembers the phones it has verified, and "
                        + "warns if a remembered phone's key changes. Do not type anything private. "
                        + "SMS shares only a short-lived local invite; the invite is not identity "
                        + "verification, the safety-code comparison is.");
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
                            + "show a 20-digit safety code and say whether the other phone is new, "
                            + "remembered, or has a changed key. Compare the codes side by side and "
                            + "tap Codes match on both phones only if every digit is the same. The invite "
                            + "expires after about 3 minutes. Router client isolation can block this "
                            + "test. Leaving this app to share or paste the invite is fine, but the "
                            + "host must be back on screen when the other phone joins. From Join until "
                            + "the result appears, keep this app on screen on both phones (the screen "
                            + "stays on by itself): on Android 15 and later, leaving it for a few "
                            + "seconds (screen lock or another app) closes the connection.");
            lanNotice.setTextSize(14);
            addWithTopMargin(root, lanNotice, 8);

            identityView = new TextView(this);
            identityView.setTextSize(14);
            addWithTopMargin(root, identityView, 8);

            Button resetKeyButton = new Button(this);
            resetKeyButton.setText("Reset this phone's test key");
            addWithTopMargin(root, resetKeyButton, 8);
            resetKeyButton.setOnClickListener(view -> resetTestKey());

            Button forgetPeersButton = new Button(this);
            forgetPeersButton.setText("Forget remembered phones");
            addWithTopMargin(root, forgetPeersButton, 8);
            forgetPeersButton.setOnClickListener(view -> forgetPeers());
            refreshIdentityView();

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

            safetyPeerView = new TextView(this);
            safetyPeerView.setTextSize(15);
            addWithTopMargin(safetyPanel, safetyPeerView, 6);
            defaultTextColor = safetyPeerView.getCurrentTextColor();

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
                            + "intercepting the connection. Codes differ on either phone stops both "
                            + "phones at once.");
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

            chatPanel = new LinearLayout(this);
            chatPanel.setOrientation(LinearLayout.VERTICAL);
            chatPanel.setVisibility(View.GONE);
            addWithTopMargin(root, chatPanel, 12);

            TextView chatTitle = new TextView(this);
            chatTitle.setText("Typed test messages");
            chatTitle.setTextSize(18);
            chatPanel.addView(chatTitle, matchWrap());

            TextView chatNotice = new TextView(this);
            chatNotice.setText(
                    "Both people confirmed the safety code. What you type here is encrypted with MLS "
                            + "and sent directly to the other phone over this Wi-Fi. Messages appear "
                            + "only on these two screens and are not saved. The typed test ends after "
                            + "10 minutes or when either person taps End typed test. TEST ONLY: do not "
                            + "type passwords, bank details or anything private.");
            chatNotice.setTextSize(14);
            addWithTopMargin(chatPanel, chatNotice, 6);

            chatLogView = new TextView(this);
            chatLogView.setTextSize(16);
            addWithTopMargin(chatPanel, chatLogView, 8);

            chatInput = new EditText(this);
            chatInput.setSingleLine(false);
            chatInput.setMinLines(1);
            chatInput.setMaxLines(4);
            chatInput.setTextSize(16);
            chatInput.setHint("Type a short test message");
            chatInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(MAX_TYPED_CHARS)});
            // Ask the keyboard not to learn from this test text (incognito where supported).
            chatInput.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            addWithTopMargin(chatPanel, chatInput, 8);

            chatSendButton = new Button(this);
            chatSendButton.setText("Send");
            addWithTopMargin(chatPanel, chatSendButton, 8);
            chatSendButton.setOnClickListener(view -> sendTypedMessage());

            chatEndButton = new Button(this);
            chatEndButton.setText("End typed test");
            addWithTopMargin(chatPanel, chatEndButton, 8);
            chatEndButton.setOnClickListener(view -> endTypedTest());

            chatStatusView = new TextView(this);
            chatStatusView.setTextSize(14);
            addWithTopMargin(chatPanel, chatStatusView, 8);
        }

        Button copyButton = new Button(this);
        copyButton.setText("Copy device and test results");
        addWithTopMargin(root, copyButton, 18);
        copyButton.setOnClickListener(view -> copyDiagnostics());

        FrameLayout container = new FrameLayout(this);
        container.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 30) {
            // With targetSdk 36, Android 15+ draws this app edge to edge: keep the content clear
            // of the system bars and the keyboard so the typed-test box stays visible.
            container.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(container);
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
        keepScreenOn(true);
        final int port = 40000 + new SecureRandom().nextInt(20000);
        final String token = newInviteToken();
        inviteUri = new Uri.Builder()
                .scheme("secretmessage")
                .authority("join")
                .appendQueryParameter("host", hostIp)
                .appendQueryParameter("port", Integer.toString(port))
                .appendQueryParameter("token", token)
                .appendQueryParameter("v", INVITE_VERSION)
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
                String identity = loadOrCreateIdentity(false);
                result = runNativeLanHost(hostIp, port, token, identity, loadPins());
                if (result == null) result = "FAIL: native host returned no status.";
            } catch (Throwable failure) {
                result = "FAIL: " + failure.getClass().getSimpleName()
                        + " while starting or running the LAN host" + detail(failure);
            }
            finishLanTask(result);
        }, "secretmessage-mls-lan-host").start();
        startLanPolling();
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
        keepScreenOn(true);
        lanResult = "Connecting directly to the private LAN host; no relay is used.";
        lanStatusView.setText("LAN MLS: " + lanResult);
        new Thread(() -> {
            String result;
            try {
                System.loadLibrary(NATIVE_LIBRARY);
                String identity = loadOrCreateIdentity(false);
                result = runNativeLanJoin(invite.host, invite.port, invite.token, identity, loadPins());
                if (result == null) result = "FAIL: native guest returned no status.";
            } catch (Throwable failure) {
                result = "FAIL: " + failure.getClass().getSimpleName()
                        + " while joining the local MLS test" + detail(failure);
            }
            finishLanTask(result);
        }, "secretmessage-mls-lan-join").start();
        startLanPolling();
    }

    private void finishLanTask(String result) {
        runOnUiThread(() -> {
            // Show the last typed-test events (such as its end summary) before closing the test.
            drainChatEvents();
            if (chatOpen) closeTypedTest("Stopped. The typed test ended without a summary.");
            lanResult = result;
            lanStatusView.setText("LAN MLS: " + result);
            Log.i(TAG_LAN, result);
            lanTaskRunning.set(false);
            if (result.startsWith("PASS")) rememberVerifiedPeer();
            refreshIdentityView();
            uiHandler.removeCallbacks(lanPoller);
            keepScreenOn(false);
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
        lastPeerStatus = "Not reached";
        if (safetyPanel != null) safetyPanel.setVisibility(View.GONE);
        resetTypedTest();
    }

    private void startLanPolling() {
        try {
            // Idempotent; the worker thread reports any load failure as the test result.
            System.loadLibrary(NATIVE_LIBRARY);
        } catch (Throwable reportedByWorker) {
            // Polling keeps retrying until the worker finishes.
        }
        uiHandler.removeCallbacks(lanPoller);
        uiHandler.postDelayed(lanPoller, LAN_POLL_MS);
    }

    private void pollLanTest() {
        if (!lanTaskRunning.get()) return;
        String prompt = "";
        try {
            prompt = nativeLanSafetyPrompt();
        } catch (Throwable notLoadedYet) {
            // The worker thread may still be loading the test-only native library.
        }
        if (prompt != null && !prompt.isEmpty()) {
            String[] parts = prompt.split("\\|", -1);
            if (parts.length == 3 && !parts[0].equals(shownSafetyCode)) {
                showSafetyCode(parts[0], parts[1], parts[2]);
            }
        }
        drainChatEvents();
        uiHandler.postDelayed(lanPoller, LAN_POLL_MS);
    }

    private void showSafetyCode(String code, String peerStatus, String peerDevice) {
        shownSafetyCode = code;
        String device = formatDeviceId(peerDevice);
        lastPeerStatus = peerStatus + " (device " + device + ")";
        if ("CHANGED".equals(peerStatus)) {
            safetyPeerView.setText("WARNING: the test key for device " + device + " has changed since "
                    + "you verified it. Resetting or reinstalling that phone causes this, but so could "
                    + "another device pretending to be it. Compare every digit, and tap Codes differ "
                    + "if you are unsure.");
            safetyPeerView.setTextColor(Color.rgb(176, 0, 32));
        } else if ("KNOWN".equals(peerStatus)) {
            safetyPeerView.setText("Remembered phone (device " + device + "): it is using the same "
                    + "test key you verified before. Still compare the code.");
            safetyPeerView.setTextColor(defaultTextColor);
        } else {
            safetyPeerView.setText("New phone (device " + device + "). If the codes match, this phone "
                    + "will remember its test key.");
            safetyPeerView.setTextColor(defaultTextColor);
        }
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
        if (!ANSWER_ACCEPTED.equals(reply)) {
            // The comparison had already ended (for example, the other phone reported different
            // codes first), so this answer was not used; the result appears when the test ends.
            lanStatusView.setText("LAN MLS: " + lanResult + " (" + reply + ")");
            return;
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

    /** Android 15+ cuts an app's connections soon after it leaves the screen, so stay visible. */
    private void keepScreenOn(boolean on) {
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void rememberVerifiedPeer() {
        String peer = "";
        try {
            peer = nativeTakeLanVerifiedPeer();
        } catch (Throwable notAvailable) {
            // Without the verified peer there is nothing to remember.
        }
        if (peer != null && !peer.isEmpty()) rememberPeer(peer);
    }

    private void drainChatEvents() {
        for (int handled = 0; handled < 100; handled++) {
            String event;
            try {
                event = nativeLanChatPoll();
            } catch (Throwable notLoadedYet) {
                return;
            }
            if (event == null || event.isEmpty()) return;
            handleChatEvent(event);
        }
    }

    /** Events are OPEN|status, DELIVERED|number, RECEIVED|number|text or ENDED|summary. */
    private void handleChatEvent(String event) {
        int bar = event.indexOf('|');
        String kind = bar < 0 ? event : event.substring(0, bar);
        String rest = bar < 0 ? "" : event.substring(bar + 1);
        switch (kind) {
            case "OPEN":
                openTypedTest(rest);
                break;
            case "DELIVERED":
                markDelivered(parseNumber(rest));
                break;
            case "RECEIVED": {
                int split = rest.indexOf('|');
                if (split > 0) {
                    chatReceived++;
                    addChatLine(new ChatLine(false, parseNumber(rest.substring(0, split)),
                            rest.substring(split + 1)));
                }
                break;
            }
            case "ENDED":
                closeTypedTest(rest);
                break;
            default:
                break;
        }
    }

    private void openTypedTest(String status) {
        chatOpen = true;
        chatLines.clear();
        chatSent = 0;
        chatDelivered = 0;
        chatReceived = 0;
        typedTestResult = "Open";
        if (!status.isEmpty()) lanResult = status;
        lanStatusView.setText("LAN MLS: " + lanResult + " Typed test messages are open below.");
        rememberVerifiedPeer();
        refreshIdentityView();
        safetyPanel.setVisibility(View.GONE);
        chatInput.setEnabled(true);
        chatSendButton.setEnabled(true);
        chatEndButton.setEnabled(true);
        chatPanel.setVisibility(View.VISIBLE);
        renderChat();
        scrollView.post(() -> scrollView.smoothScrollTo(0, chatPanel.getTop()));
    }

    private void closeTypedTest(String summary) {
        chatOpen = false;
        typedTestResult = summary.isEmpty() ? "Ended." : summary;
        if (chatPanel == null) return;
        chatInput.setEnabled(false);
        chatSendButton.setEnabled(false);
        chatEndButton.setEnabled(false);
        renderChat();
    }

    private void resetTypedTest() {
        chatOpen = false;
        chatLines.clear();
        chatSent = 0;
        chatDelivered = 0;
        chatReceived = 0;
        typedTestResult = "Not reached";
        if (chatPanel == null) return;
        chatPanel.setVisibility(View.GONE);
        chatInput.setText("");
        renderChat();
    }

    private void sendTypedMessage() {
        if (!chatOpen) {
            toast("The typed test is not open.");
            return;
        }
        String text = chatInput.getText().toString();
        if (text.trim().isEmpty()) {
            toast("Type a message first.");
            return;
        }
        String reply;
        try {
            reply = nativeLanChatSend(text);
        } catch (Throwable failure) {
            reply = "FAIL: " + failure.getClass().getSimpleName();
        }
        if (reply != null && reply.startsWith("OK|")) {
            chatSent++;
            addChatLine(new ChatLine(true, parseNumber(reply.substring(3)), text));
            chatInput.setText("");
        } else {
            toast(reply == null ? "Could not send the message." : reply.replaceFirst("^FAIL: ", ""));
        }
    }

    private void endTypedTest() {
        String reply;
        try {
            reply = nativeLanChatEnd();
        } catch (Throwable failure) {
            reply = "Could not end the typed test: " + failure.getClass().getSimpleName();
        }
        chatInput.setEnabled(false);
        chatSendButton.setEnabled(false);
        chatEndButton.setEnabled(false);
        chatStatusView.setText(reply);
    }

    private void markDelivered(int number) {
        for (ChatLine line : chatLines) {
            if (line.mine && line.number == number && !line.delivered) {
                line.delivered = true;
                chatDelivered++;
                break;
            }
        }
        renderChat();
    }

    private void addChatLine(ChatLine line) {
        chatLines.add(line);
        while (chatLines.size() > MAX_CHAT_LINES) chatLines.remove(0);
        renderChat();
        chatInput.post(() -> chatInput.requestRectangleOnScreen(
                new Rect(0, 0, chatInput.getWidth(), chatInput.getHeight()), false));
    }

    private void renderChat() {
        if (chatLogView == null) return;
        StringBuilder text = new StringBuilder();
        for (ChatLine line : chatLines) {
            if (text.length() > 0) text.append("\n\n");
            if (line.mine) {
                text.append("You: ").append(line.text);
                if (line.delivered) {
                    text.append("  ✓ delivered");
                } else {
                    text.append(chatOpen ? "  … sending" : "  (not confirmed)");
                }
            } else {
                text.append("Other phone: ").append(line.text);
            }
        }
        chatLogView.setText(text.length() == 0 ? "No typed messages yet." : text.toString());
        String counts = "Sent " + chatSent + " (" + chatDelivered + " delivered), received "
                + chatReceived + ".";
        chatStatusView.setText(chatOpen
                ? counts + " Messages are shown only on these two screens and are not saved."
                : typedTestResult + " These messages stay on this screen until you start another "
                        + "test or close the app; they are not saved.");
    }

    private String typedTestReport() {
        if (!chatOpen) return typedTestResult;
        return "Open: sent " + chatSent + " (" + chatDelivered + " delivered), received "
                + chatReceived + " so far.";
    }

    private static int parseNumber(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /** This phone's test identity text, created or given a new key when needed. Worker thread only. */
    private synchronized String loadOrCreateIdentity(boolean newKey) throws Exception {
        SharedPreferences prefs = prefs();
        String stored = prefs.getString(PREF_IDENTITY, null);
        if (stored != null && !newKey) {
            return unwrap(stored);
        }
        String deviceId = prefs.getString(PREF_DEVICE_ID, null);
        if (deviceId == null) deviceId = randomHex(8);
        String identity = nativeGenerateLanIdentity(deviceId);
        if (identity == null || !identity.startsWith("v1:")) {
            throw new IllegalStateException(identity == null ? "no test identity was created" : identity);
        }
        boolean saved = prefs.edit()
                .putString(PREF_DEVICE_ID, deviceId)
                .putString(PREF_IDENTITY, wrap(identity))
                .commit();
        if (!saved) throw new IllegalStateException("the test identity could not be saved");
        return identity;
    }

    /** The Android Keystore AES key that encrypts the stored test identity; it never leaves Keystore. */
    private SecretKey wrappingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(WRAP_KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(WRAP_KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private String wrap(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey());
        byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(sealed, Base64.NO_WRAP);
    }

    private String unwrap(String stored) throws Exception {
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) throw new IllegalStateException("the stored test identity is malformed");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(),
                new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private String loadPins() {
        return prefs().getString(PREF_PEER_PINS, "");
    }

    /** Remember a verified phone as "<device ID> <public key hex>", replacing its earlier key. */
    private void rememberPeer(String pin) {
        String[] fields = pin.trim().split(" ");
        if (fields.length != 2) return;
        StringBuilder updated = new StringBuilder();
        for (String line : loadPins().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(fields[0] + " ")) continue;
            updated.append(trimmed).append('\n');
        }
        updated.append(fields[0]).append(' ').append(fields[1]).append('\n');
        prefs().edit().putString(PREF_PEER_PINS, updated.toString()).apply();
    }

    private static int countPins(String pins) {
        int count = 0;
        for (String line : pins.split("\n")) {
            if (!line.trim().isEmpty()) count++;
        }
        return count;
    }

    private String identitySummary() {
        SharedPreferences prefs = prefs();
        String deviceId = prefs.getString(PREF_DEVICE_ID, null);
        int remembered = countPins(loadPins());
        if (deviceId == null || !prefs.contains(PREF_IDENTITY)) {
            return "no test identity yet (it is created on the first LAN test); remembered phones: "
                    + remembered;
        }
        return "device " + formatDeviceId(deviceId)
                + ", key stored encrypted with an Android Keystore key; remembered phones: " + remembered;
    }

    private void refreshIdentityView() {
        if (identityView != null) identityView.setText("This phone's test identity: " + identitySummary() + ".");
    }

    private void resetTestKey() {
        if (lanTaskRunning.get()) {
            toast("Wait until the LAN test finishes.");
            return;
        }
        new Thread(() -> {
            String message;
            try {
                System.loadLibrary(NATIVE_LIBRARY);
                loadOrCreateIdentity(true);
                message = "New test key created. Phones that remembered this phone will now show a "
                        + "key-change warning.";
            } catch (Throwable failure) {
                message = "Could not reset the test key: " + failure.getClass().getSimpleName() + detail(failure);
            }
            final String shown = message;
            runOnUiThread(() -> {
                Toast.makeText(this, shown, Toast.LENGTH_LONG).show();
                refreshIdentityView();
            });
        }, "secretmessage-lan-reset-key").start();
    }

    private void forgetPeers() {
        if (lanTaskRunning.get()) {
            toast("Wait until the LAN test finishes.");
            return;
        }
        prefs().edit().remove(PREF_PEER_PINS).apply();
        refreshIdentityView();
        toast("Remembered phones cleared.");
    }

    private static String formatDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() != 16) return String.valueOf(deviceId);
        return deviceId.substring(0, 4) + " " + deviceId.substring(4, 8) + " "
                + deviceId.substring(8, 12) + " " + deviceId.substring(12);
    }

    private static String detail(Throwable failure) {
        return failure.getMessage() == null ? "." : ": " + failure.getMessage();
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
                + "\nOther phone: " + lastPeerStatus
                + "\nTyped text test: " + typedTestReport()
                + "\nThis phone: " + identitySummary()
                + "\nScope: fixed synthetic MLS messages, then typed test messages shown only on the "
                + "two screens; message text is not saved and is not included in this report.";
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
        if (!INVITE_VERSION.equals(uri.getQueryParameter("v"))) {
            throw new IllegalArgumentException("This invite comes from a different test APK version. "
                    + "Install " + BuildConfig.VERSION_NAME + " on both phones and create a new invite.");
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
        return randomHex(16);
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        StringBuilder text = new StringBuilder(byteCount * 2);
        for (byte value : bytes) text.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return text.toString();
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

    /** One typed test message on this screen; never written to storage or logs. */
    private static final class ChatLine {
        final boolean mine;
        final int number;
        final String text;
        boolean delivered;

        ChatLine(boolean mine, int number, String text) {
            this.mine = mine;
            this.number = number;
            this.text = text;
        }
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
