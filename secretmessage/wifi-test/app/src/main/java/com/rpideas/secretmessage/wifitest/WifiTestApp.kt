package com.rpideas.secretmessage.wifitest

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpideas.secretmessage.preview.chat.ChatViewModel
import com.rpideas.secretmessage.preview.chat.ComposerPolicy
import com.rpideas.secretmessage.preview.security.AppLockController
import com.rpideas.secretmessage.preview.ui.ChatScreen
import com.rpideas.secretmessage.preview.ui.LockScreen
import com.rpideas.secretmessage.preview.ui.Night
import com.rpideas.secretmessage.preview.ui.SecretMessageTheme
import com.rpideas.secretmessage.preview.ui.SettingsScreen
import com.rpideas.secretmessage.wifitest.updates.AppUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Root of the app: lock screen, settings and guide, the connect screen with the remembered phones,
 * or the WhisperPad chat once the chat is open.
 */
@Composable
internal fun WifiTestApp(
    viewModel: ChatViewModel,
    session: LanChatSession,
    contactBook: ContactBook,
    appLock: AppLockController,
    versionLabel: String,
    engineInfo: String,
    pendingInvite: String?,
    notice: String?,
    settingsMessage: String?,
    readyEnabled: Boolean,
    resumeCount: Int,
    actions: ConnectActions,
    onUnlockRequest: () -> Unit,
    onAppLockToggle: (Boolean) -> String?,
    onLeaveChatOpen: () -> Unit,
    onSettingsClosed: () -> Unit,
) {
    val connection by session.connection.collectAsStateWithLifecycle()
    val contacts by contactBook.contacts.collectAsStateWithLifecycle()
    val ring by Rings.pending.collectAsStateWithLifecycle()
    val doorbell by Doorbell.link.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val nowElapsedMillis by viewModel.nowElapsedMillis.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val statusDescription by viewModel.statusDescription.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var chatNotice by remember { mutableStateOf<String?>(null) }
    val presence = remember { mutableStateMapOf<String, Doorbell.Presence>() }

    // A chat request is shown until it expires; this tick removes it from the screen on time.
    var ringClock by remember { mutableIntStateOf(0) }
    LaunchedEffect(ring?.id) {
        val waiting = ring ?: return@LaunchedEffect
        val left = Rings.ANSWER_WINDOW_MILLIS - (SystemClock.elapsedRealtime() - waiting.receivedAtElapsed)
        if (left > 0) delay(left + 500)
        ringClock++
    }
    val currentRing = ring?.takeIf { ringClock >= 0 && !it.expired() }
    var laterRingId by remember { mutableStateOf<String?>(null) }

    // Keep the screen on while a chat starts or is open, as the LAN smoke test does.
    val view = LocalView.current
    val keepScreenOn = connection !is ConnectionState.Idle
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // While the start screen shows, ask the relay now and then which remembered phones are ready.
    val onStartScreen = !appLock.locked && !showSettings && connection is ConnectionState.Idle
    LaunchedEffect(onStartScreen, contacts, resumeCount) {
        if (!onStartScreen) return@LaunchedEffect
        while (true) {
            contacts.forEach { contact ->
                val inbox = contact.card?.inboxId ?: return@forEach
                presence[contact.deviceId] = withContext(Dispatchers.IO) { Doorbell.presence(inbox) }
            }
            delay(PRESENCE_REFRESH_MILLIS)
        }
    }

    SecretMessageTheme {
        Scaffold(containerColor = Night) { insets ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets),
            ) {
                when {
                    appLock.locked -> LockScreen(message = appLock.lastMessage, onUnlock = onUnlockRequest)
                    showSettings && showGuide -> GuideScreen(onBack = { showGuide = false })
                    showSettings -> SettingsScreen(
                        settings = settings,
                        appLockMessage = lockMessage ?: appLock.lastMessage,
                        versionLabel = versionLabel,
                        onBack = {
                            showSettings = false
                            lockMessage = null
                            onSettingsClosed()
                        },
                        onViewSecondsChange = viewModel::setViewSeconds,
                        onSelectTone = viewModel::selectTone,
                        onPreviewTone = viewModel::previewTone,
                        onAppLockChange = { enabled -> lockMessage = onAppLockToggle(enabled) },
                        wording = WifiWording.settings,
                        extraContent = {
                            AppSettingsSections(
                                info = AppSettingsInfo(
                                    versionLabel = versionLabel,
                                    identitySummary = session.identity.summary(),
                                    engineInfo = engineInfo,
                                    readyEnabled = readyEnabled,
                                    doorbell = doorbell,
                                    hasDoorbellContacts = contacts.any { it.card != null },
                                    idle = connection is ConnectionState.Idle,
                                    message = settingsMessage,
                                    resumeCount = resumeCount,
                                ),
                                actions = actions,
                                onOpenGuide = { showGuide = true },
                            )
                        },
                    )
                    connection is ConnectionState.Connected -> {
                        // Back leaves the chat open (the connection service keeps it); End closes it.
                        BackHandler(onBack = onLeaveChatOpen)
                        ChatScreen(
                            messages = messages,
                            composer = composer,
                            canSend = ComposerPolicy.isSendable(composer),
                            nowElapsedMillis = nowElapsedMillis,
                            sessionStatus = sessionStatus,
                            statusDescription = tr(statusDescription),
                            notice = chatNotice,
                            onTypeEnglishChar = { key ->
                                chatNotice = null
                                viewModel.typeText(key)
                            },
                            onBackspace = viewModel::backspace,
                            onInsertInlineGlyph = { strokes, heightDp -> viewModel.insertGlyph(strokes, heightDp) },
                            onSendInlineMessage = {
                                chatNotice = if (viewModel.sendComposedMessage()) {
                                    null
                                } else {
                                    tr("This message is too large to send. Remove some drawings and try again.")
                                }
                            },
                            onSendFullPicture = { strokes ->
                                chatNotice = if (viewModel.sendPicture(strokes)) {
                                    null
                                } else {
                                    tr("This picture is too large to send. Clear it and draw a simpler one.")
                                }
                            },
                            onOpenSettings = { showSettings = true },
                            onChatVisibilityChanged = viewModel::setChatOnScreen,
                            wording = WifiWording.chat,
                            onEndChat = session::endChat,
                        )
                    }
                    else -> {
                        ConnectScreen(
                            state = connection,
                            contacts = contacts,
                            presence = presence,
                            ring = currentRing,
                            pendingInvite = pendingInvite,
                            notice = notice,
                            actions = actions,
                            onOpenSettings = { showSettings = true },
                        )
                        // As in Oor Arivippu: a verified update found at launch is offered once. The
                        // check runs on the first start screen of this app start only.
                        if (connection is ConnectionState.Idle && LaunchUpdateCheck.pending) {
                            AppUpdates.Control("Secret-Message", automatic = true)
                            DisposableEffect(Unit) { onDispose { LaunchUpdateCheck.pending = false } }
                        }
                    }
                }
                // A remembered phone is calling: ask on top of the start screen or Settings, also
                // while this phone waits for its own call or invite (answering stops that waiting).
                // Not while locked, so the name appears only after unlocking.
                val canAnswer = connection is ConnectionState.Idle || connection is ConnectionState.Hosting
                if (currentRing != null && canAnswer && !appLock.locked && currentRing.id != laterRingId) {
                    IncomingRingDialog(
                        ring = currentRing,
                        onAnswer = actions::answerRing,
                        onIgnore = actions::dismissRing,
                        onLater = { laterRingId = currentRing.id },
                    )
                }
            }
        }
    }
}

private const val PRESENCE_REFRESH_MILLIS = 45_000L

/** The launch update check runs until the first start screen of this app start goes away. */
private object LaunchUpdateCheck {
    @Volatile
    var pending = true
}
