package com.rpideas.secretmessage.wifitest

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rpideas.secretmessage.preview.BuildConfig
import com.rpideas.secretmessage.preview.chat.ChatViewModel
import com.rpideas.secretmessage.preview.security.AppLockAvailability
import com.rpideas.secretmessage.preview.security.AppLockController
import kotlin.concurrent.thread

/**
 * Secret Message: the WhisperPad product screens connected to another phone through the mls-rs MLS
 * session, over the Internet through the owner's relay or directly on the same Wi-Fi, after both
 * people confirm the safety code. Published from 1.0.0 at the owner's request (17 October 2026)
 * without an independent security review, which the app and its release notes say plainly.
 */
class WifiTestActivity : FragmentActivity(), ConnectActions {
    private lateinit var session: LanChatSession
    private lateinit var contactBook: ContactBook
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var appLock: AppLockController
    private var pendingInvite by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var engineInfo by mutableStateOf("")
    private var settingsMessage by mutableStateOf<String?>(null)
    private var readyEnabled by mutableStateOf(true)
    private var resumeCount by mutableIntStateOf(0)
    private var askedForNotifications = false
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The chat works either way; without notifications the connection service is just not shown.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Best-effort screenshot deterrent only, as in the product; it cannot prevent every capture path.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        Tr.init(this)
        session = WifiChat.session(this)
        contactBook = Contacts.book(this)
        readyEnabled = ReadyMode.enabled(this)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, session) as T
            }
        }
        chatViewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        appLock = AppLockController(this)
        appLock.lockForStart(chatViewModel.settings.value.appLockEnabled)
        engineInfo = NativeBridge.load()
            ?: runCatching { NativeBridge.nativeEngineInfo() }
                .getOrElse { "The MLS engine did not answer (${it.javaClass.simpleName})." }
        acceptInvite(intent)
        // A configuration change re-creates the activity with the same intent: answer only once.
        if (savedInstanceState == null) acceptRing(intent)
        // The app is on screen, so Android allows starting ready mode now.
        ChatConnectionService.ensureReady(this)
        setContent {
            WifiTestApp(
                viewModel = chatViewModel,
                session = session,
                contactBook = contactBook,
                appLock = appLock,
                versionLabel = BuildConfig.VERSION_NAME,
                engineInfo = engineInfo,
                pendingInvite = pendingInvite,
                notice = notice,
                settingsMessage = settingsMessage,
                readyEnabled = readyEnabled,
                resumeCount = resumeCount,
                actions = this,
                onUnlockRequest = ::requestUnlock,
                onAppLockToggle = ::toggleAppLock,
                onLeaveChatOpen = { moveTaskToBack(true) },
                onSettingsClosed = { settingsMessage = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptInvite(intent)
        acceptRing(intent)
    }

    override fun onStart() {
        super.onStart()
        appLock.onStart()
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        contactBook.refresh()
        chatViewModel.setForeground(true)
        if (!appLock.onResume(chatViewModel.settings.value.appLockEnabled)) {
            turnOffUnavailableLock()
        }
    }

    override fun onPause() {
        chatViewModel.setForeground(false)
        super.onPause()
    }

    override fun onStop() {
        appLock.onStop(chatViewModel.settings.value.appLockEnabled)
        super.onStop()
    }

    override fun startHost() {
        askForNotificationsOnce()
        notice = session.startInternetHost()
    }

    override fun startWifiHost() {
        askForNotificationsOnce()
        notice = session.startWifiHost()
    }

    override fun shareInvite(invite: String) {
        val scope = if (invite.contains("relay=")) "one chat" else "same Wi-Fi only, one chat"
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Secret Message invite ($scope):\n$invite")
        startActivity(Intent.createChooser(send, "Send the invite"))
    }

    override fun stopWaiting() {
        session.stopWaiting()
    }

    override fun pasteAndJoin() {
        val text = clipboardText()
        if (text.isNullOrBlank()) {
            notice = "The clipboard is empty. Copy the whole invite link from the message first."
            return
        }
        join(text)
    }

    override fun joinPending() {
        val invite = pendingInvite ?: return
        pendingInvite = null
        join(invite)
    }

    override fun answer(codesMatch: Boolean) {
        session.answer(codesMatch)
    }

    override fun copyReport() {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Secret Message connection report", buildReport()))
        settingsMessage = "Connection report copied. It holds counts only, no messages."
    }

    override fun resetKey() {
        settingsMessage = "Creating a new key…"
        thread(name = "secretmessage-wifi-reset-key") {
            val message = session.resetKey()
            runOnUiThread { settingsMessage = message }
        }
    }

    override fun forgetPhones() {
        settingsMessage = session.forgetPhones()
    }

    override fun callContact(deviceId: String) {
        // Chat on a phone that is calling this phone answers that call instead of calling back.
        val ringing = Rings.pending.value?.takeIf { !it.expired() && it.invite.contact.deviceId == deviceId }
        if (ringing != null) {
            answerRing()
            return
        }
        val contact = contactBook.find(deviceId) ?: return
        askForNotificationsOnce()
        notice = session.callContact(contact)
    }

    override fun renameContact(deviceId: String, name: String) {
        contactBook.rename(deviceId, name)
    }

    override fun forgetContact(deviceId: String) {
        if (session.busy()) {
            notice = "Wait until the chat has ended."
            return
        }
        contactBook.forget(deviceId)
        ChatConnectionService.ensureReady(this)
        notice = "Phone forgotten."
    }

    override fun answerRing() {
        val ring = Rings.take(this, null)
        notice = if (ring == null) RING_EXPIRED else session.answerRing(ring)
    }

    override fun dismissRing() {
        Rings.dismiss(this)
    }

    override fun setReady(enabled: Boolean) {
        readyEnabled = enabled
        if (enabled) askForNotificationsOnce()
        ReadyMode.setEnabled(this, enabled)
    }

    override fun openBatterySettings() {
        val power = getSystemService(PowerManager::class.java)
        val request = if (power?.isIgnoringBatteryOptimizations(packageName) == true) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            // Asked only when the person taps the button in Settings.
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        }
        val opened = runCatching { startActivity(request) }.isSuccess ||
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }.isSuccess
        if (!opened) settingsMessage = "Android did not open its battery settings. Open Settings > Apps > Secret Message > Battery."
    }

    private fun join(inviteText: String) {
        askForNotificationsOnce()
        notice = session.join(inviteText)
    }

    private fun acceptInvite(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == InviteLink.SCHEME && data.host == InviteLink.HOST) {
            pendingInvite = data.toString()
            notice = "Invite received. Tap Join this chat."
        }
    }

    /** Answers the chat request that the person tapped in the notification. */
    private fun acceptRing(intent: Intent?) {
        if (intent?.action != RingNotifier.ACTION_ANSWER) return
        val ring = Rings.take(this, intent.getStringExtra(RingNotifier.EXTRA_RING_ID))
        notice = if (ring == null) RING_EXPIRED else session.answerRing(ring)
    }

    private fun clipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun askForNotificationsOnce() {
        if (askedForNotifications || notificationsAllowed()) return
        askedForNotifications = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun buildReport(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val open = session.connection.value is ConnectionState.Connected
        val notifications = if (notificationsAllowed()) {
            "allowed"
        } else {
            "not allowed (the connection service runs without a visible notification)"
        }
        val contacts = contactBook.contacts.value
        val battery = if (getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true) {
            "not restricted"
        } else {
            "optimized (Android may delay chat requests)"
        }
        val ready = when {
            !ReadyMode.enabled(this) -> "off"
            !ReadyMode.wanted(this) -> "on, waiting for a remembered phone with a doorbell"
            else -> "on, doorbell ${Doorbell.link.value.name.lowercase()}"
        }
        val lines = listOf(
            "Secret Message report: ${BuildConfig.VERSION_NAME}",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "ABI: $abi",
            "Engine: $engineInfo",
        ) + session.report.lines(open) + listOf(
            "This phone: ${session.identity.summary()}",
            "Remembered phones: ${contacts.size} (with a doorbell: ${contacts.count { it.card != null }})",
            "Ready for chats: $ready; connects ${Doorbell.connects}, chat requests received ${Doorbell.ringsReceived}, " +
                "refused ${Doorbell.ringsDropped}" + Doorbell.lastProblem.takeIf { it.isNotEmpty() }?.let { "; last problem: $it" }.orEmpty(),
            "Chat requests: shown ${Rings.shownCount}, answered ${Rings.answeredCount}, both phones calling " +
                "${Rings.bothCallingCount}, while busy ${Rings.busyCount}",
            "Battery: $battery",
            "Notifications: $notifications",
            "Not independently security-reviewed. Message text and drawings are not included.",
        )
        return lines.joinToString("\n")
    }

    private fun requestUnlock() {
        if (!appLock.authenticate()) turnOffUnavailableLock()
    }

    /** Returns a message to show when the lock cannot be enabled on this phone. */
    private fun toggleAppLock(enabled: Boolean): String? {
        if (!enabled) {
            chatViewModel.setAppLockEnabled(false)
            appLock.unlock()
            return null
        }
        if (appLock.availability() != AppLockAvailability.AVAILABLE) {
            return "Set a screen lock (PIN, pattern, password or biometric) in Android Settings to use the app lock."
        }
        chatViewModel.setAppLockEnabled(true)
        return null
    }

    private companion object {
        const val RING_EXPIRED = "That chat request has expired. Ask the other phone to call again."
    }

    private fun turnOffUnavailableLock() {
        chatViewModel.setAppLockEnabled(false)
        appLock.unlock(message = "App lock was turned off because this phone has no usable screen lock.")
    }
}
