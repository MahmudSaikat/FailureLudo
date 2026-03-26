package com.failureludo.feedback

import android.media.AudioManager
import android.media.ToneGenerator
import com.failureludo.data.FeedbackSettings

enum class FeedbackEvent {
    DICE_ROLL,
    PIECE_MOVE,
    CAPTURE,
    PIECE_FINISH,
    EXTRA_ROLL,
    TURN_SKIP,
    INVALID_ACTION,
    WIN
}

class GameFeedbackManager {

    fun emitSound(event: FeedbackEvent, settings: FeedbackSettings) {
        if (!settings.soundEnabled) return

        val duration = when (event) {
            FeedbackEvent.DICE_ROLL -> 110
            FeedbackEvent.PIECE_MOVE -> 70
            FeedbackEvent.CAPTURE -> 160
            FeedbackEvent.PIECE_FINISH -> 180
            FeedbackEvent.EXTRA_ROLL -> 140
            FeedbackEvent.TURN_SKIP -> 90
            FeedbackEvent.INVALID_ACTION -> 80
            FeedbackEvent.WIN -> 280
        }

        val toneType = when (event) {
            FeedbackEvent.DICE_ROLL -> ToneGenerator.TONE_PROP_BEEP
            FeedbackEvent.PIECE_MOVE -> ToneGenerator.TONE_PROP_ACK
            FeedbackEvent.CAPTURE -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            FeedbackEvent.PIECE_FINISH -> ToneGenerator.TONE_PROP_PROMPT
            FeedbackEvent.EXTRA_ROLL -> ToneGenerator.TONE_PROP_BEEP2
            FeedbackEvent.TURN_SKIP -> ToneGenerator.TONE_PROP_NACK
            FeedbackEvent.INVALID_ACTION -> ToneGenerator.TONE_PROP_NACK
            FeedbackEvent.WIN -> ToneGenerator.TONE_CDMA_ABBR_ALERT
        }

        val scaledVolume = (settings.masterVolume.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
        val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, scaledVolume)
        toneGenerator.startTone(toneType, duration.coerceAtLeast(30))
        toneGenerator.release()
    }
}
