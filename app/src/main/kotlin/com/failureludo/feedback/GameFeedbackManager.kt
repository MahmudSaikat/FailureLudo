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

class GameFeedbackManager(
    context: Context,
    soundPrefix: String = "sfx_",
    soundOverrides: Map<FeedbackEvent, Int> = emptyMap()
) {

    private val audioManager = GameAudioManager(context, soundPrefix, soundOverrides)

    fun emitSound(event: FeedbackEvent, settings: FeedbackSettings) {
        if (!settings.soundEnabled) return

        // Legacy beep fallback is intentionally disabled so only curated custom SFX are emitted.
        audioManager.play(event, settings.masterVolume)
    }

    fun release() {
        audioManager.release()
    }
}
