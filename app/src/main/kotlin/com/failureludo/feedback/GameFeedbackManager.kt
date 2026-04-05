package com.failureludo.feedback

import android.content.Context
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

class GameFeedbackManager(context: Context) {

    private val audioManager = GameAudioManager(context)

    fun emitSound(event: FeedbackEvent, settings: FeedbackSettings) {
        if (!settings.soundEnabled) return

        // Legacy beep fallback is intentionally disabled so only curated custom SFX are emitted.
        audioManager.play(event, settings.masterVolume)
    }

    fun release() {
        audioManager.release()
    }
}
