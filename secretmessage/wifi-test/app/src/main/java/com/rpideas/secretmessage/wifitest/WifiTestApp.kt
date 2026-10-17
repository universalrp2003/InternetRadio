package com.rpideas.secretmessage.wifitest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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

/**
 * Root of the Wi-Fi test build: lock screen, settings, the connect screen, or the WhisperPad chat
 * once both people have confirmed the safety code.
 */
@Composable
internal fun WifiTestApp(
    viewModel: ChatViewModel,
    session: LanChatSession,
    appLock: AppLockController,
    versionLabel: String,
    engineInfo: String,
    pendingInvite: String?,
    notice: String?,
    actions: ConnectActions,
    onUnlockRequest: () -> Unit,
    onAppLockToggle: (Boolean) -> String?,
    onLeaveChatOpen: () -> Unit,
) {
    val connection by session.connection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val nowElapsedMillis by viewModel.nowElapsedMillis.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val statusDescription by viewModel.statusDescription.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    var chatNotice by remember { mutableStateOf<String?>(null) }

    // Keep the screen on while a chat starts or is open, as the LAN smoke test does.
    val view = LocalView.current
    val keepScreenOn = connection !is ConnectionState.Idle
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
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
                    showSettings -> SettingsScreen(
                        settings = settings,
                        appLockMessage = settingsMessage ?: appLock.lastMessage,
                        versionLabel = versionLabel,
                        onBack = {
                            showSettings = false
                            settingsMessage = null
                        },
                        onViewSecondsChange = viewModel::setViewSeconds,
                        onSelectTone = viewModel::selectTone,
                        onPreviewTone = viewModel::previewTone,
                        onAppLockChange = { enabled -> settingsMessage = onAppLockToggle(enabled) },
                        wording = WifiWording.settings,
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
                            statusDescription = statusDescription,
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
                                    "This message is too large to send. Remove some drawings and try again."
                                }
                            },
                            onSendFullPicture = { strokes ->
                                chatNotice = if (viewModel.sendPicture(strokes)) {
                                    null
                                } else {
                                    "This picture is too large to send. Clear it and draw a simpler one."
                                }
                            },
                            onOpenSettings = { showSettings = true },
                            onChatVisibilityChanged = viewModel::setChatOnScreen,
                            wording = WifiWording.chat,
                            onEndChat = session::endChat,
                        )
                    }
                    else -> ConnectScreen(
                        state = connection,
                        identitySummary = session.identity.summary(),
                        engineInfo = engineInfo,
                        pendingInvite = pendingInvite,
                        notice = notice,
                        actions = actions,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
