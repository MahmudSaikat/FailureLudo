package com.failureludo.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

class GameAudioManager(context: Context) {

    private data class EventMixConfig(
        val gain: Float,
        val pitchMin: Float = 1f,
        val pitchMax: Float = 1f
    )

    private val appContext = context.applicationContext
    private val random = Random.Default
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundAssetNames: Map<FeedbackEvent, String> = mapOf(
        FeedbackEvent.DICE_ROLL to "sfx_dice_roll",
        FeedbackEvent.PIECE_MOVE to "sfx_piece_move",
        FeedbackEvent.CAPTURE to "sfx_capture",
        FeedbackEvent.PIECE_FINISH to "sfx_piece_finish",
        FeedbackEvent.EXTRA_ROLL to "sfx_extra_roll",
        FeedbackEvent.TURN_SKIP to "sfx_turn_skip",
        FeedbackEvent.INVALID_ACTION to "sfx_invalid_action",
        FeedbackEvent.WIN to "sfx_win"
    )

    private val eventMixConfig: Map<FeedbackEvent, EventMixConfig> = mapOf(
        FeedbackEvent.DICE_ROLL to EventMixConfig(gain = 0.85f, pitchMin = 0.97f, pitchMax = 1.03f),
        FeedbackEvent.PIECE_MOVE to EventMixConfig(gain = 0.75f, pitchMin = 0.98f, pitchMax = 1.02f),
        FeedbackEvent.CAPTURE to EventMixConfig(gain = 1.20f),
        FeedbackEvent.PIECE_FINISH to EventMixConfig(gain = 0.24f),
        FeedbackEvent.EXTRA_ROLL to EventMixConfig(gain = 0.23f),
        FeedbackEvent.TURN_SKIP to EventMixConfig(gain = 0.24f),
        FeedbackEvent.INVALID_ACTION to EventMixConfig(gain = 0.25f),
        FeedbackEvent.WIN to EventMixConfig(gain = 0.55f)
    )

    private val loadedSoundIds = mutableMapOf<FeedbackEvent, Int>()
    private val readySoundIds = mutableSetOf<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                readySoundIds += sampleId
            }
        }
        preloadKnownSounds()
    }

    private fun preloadKnownSounds() {
        soundAssetNames.forEach { (event, rawName) ->
            val resourceId = appContext.resources.getIdentifier(rawName, "raw", appContext.packageName)
            if (resourceId != 0) {
                loadedSoundIds[event] = soundPool.load(appContext, resourceId, 1)
            }
        }
    }

    fun play(event: FeedbackEvent, masterVolume: Float): Boolean {
        val soundId = loadedSoundIds[event] ?: return false
        if (soundId !in readySoundIds) return false

        val mixConfig = eventMixConfig[event] ?: EventMixConfig(gain = 1f)
        val clampedVolume = (masterVolume.coerceIn(0f, 1f) * mixConfig.gain).coerceIn(0f, 1f)
        val playbackRate = if (mixConfig.pitchMin == mixConfig.pitchMax) {
            mixConfig.pitchMin
        } else {
            random.nextDouble(mixConfig.pitchMin.toDouble(), mixConfig.pitchMax.toDouble()).toFloat()
        }

        val streamId = soundPool.play(soundId, clampedVolume, clampedVolume, 1, 0, playbackRate)
        return streamId != 0
    }

    fun release() {
        loadedSoundIds.clear()
        readySoundIds.clear()
        soundPool.release()
    }
}