package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpideas.secretmessage.preview.chat.ChatViewModel
import com.rpideas.secretmessage.preview.chat.ComposerPolicy
import com.rpideas.secretmessage.preview.security.AppLockController

/** Root of the preview: lock screen, settings, or the chat with the WhisperPad input deck. */
@Composable
internal fun SecretMessageApp(
    viewModel: ChatViewModel,
    appLock: AppLockController,
    versionLabel: String,
    onUnlockRequest: () -> Unit,
    onAppLockToggle: (Boolean) -> String?,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val nowElapsedMillis by viewModel.nowElapsedMillis.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val statusDescription by viewModel.statusDescription.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    var chatNotice by remember { mutableStateOf<String?>(null) }

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
                    )
                    else -> ChatScreen(
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
                                t("This message is too large to send. Remove some drawings and try again.")
                            }
                        },
                        onSendFullPicture = { strokes ->
                            chatNotice = if (viewModel.sendPicture(strokes)) {
                                null
                            } else {
                                t("This picture is too large to send. Clear it and draw a simpler one.")
                            }
                        },
                        onOpenSettings = { showSettings = true },
                        onChatVisibilityChanged = viewModel::setChatOnScreen,
                    )
                }
            }
        }
    }
}
