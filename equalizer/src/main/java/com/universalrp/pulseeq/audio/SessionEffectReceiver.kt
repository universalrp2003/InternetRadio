package com.universalrp.pulseeq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Catches the audio sessions that other players announce.
 *
 * Well-behaved players (Poweramp, VLC, Musicolet, Vinyl and others) broadcast
 * `OPEN_AUDIO_EFFECT_CONTROL_SESSION` with their session id, which is Android's
 * official way of saying "an equalizer may shape my audio". We attach one there
 * so those apps get PulseEQ's curve with no capture and no root.
 *
 * Players that stay silent are simply not covered — nothing to fake about it.
 */
class SessionEffectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
        if (sessionId <= 0) return
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (EqCore.cooperatingEnabled()) EqCore.onForeignSessionOpened(sessionId)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                EqCore.onForeignSessionClosed(sessionId)
            }
        }
    }
}
