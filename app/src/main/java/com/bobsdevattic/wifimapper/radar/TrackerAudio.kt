package com.bobsdevattic.wifimapper.radar

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.bobsdevattic.wifimapper.model.ToneSynth
import com.bobsdevattic.wifimapper.model.TrackerMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Motion-tracker sounds, synthesised so the return bleep's pitch can rise as
 * you get closer (system beeps have fixed pitches). Tones are queued and written
 * to a streaming AudioTrack on a background thread, so drawing never waits.
 */
class TrackerAudio private constructor(private val track: AudioTrack) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<ShortArray>(capacity = 4)   // drop sounds rather than lag behind

    init {
        track.play()
        scope.launch {
            for (pcm in queue) runCatching { track.write(pcm, 0, pcm.size) }
        }
    }

    /** Soft outgoing "tick" as the wave leaves the emitter. */
    fun ping() { queue.trySend(PING) }

    /** Return bleep when the wave reaches the target; higher pitch = closer. */
    fun blip(proximity: Float) {
        val f = TrackerMath.blipPitchHz(proximity)
        queue.trySend(ToneSynth.chirp(f, f * 1.08f, durationMs = 80, volume = 0.5f, sampleRate = SAMPLE_RATE))
    }

    fun release() {
        queue.close()
        scope.cancel()
        runCatching { track.stop() }
        track.release()
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        private val PING = ToneSynth.chirp(1100f, 900f, durationMs = 35, volume = 0.22f, sampleRate = SAMPLE_RATE)

        /** Null if audio output isn't available. */
        fun create(): TrackerAudio? = runCatching {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_RATE / 5 * 2))   // ≥ 200 ms
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            TrackerAudio(track)
        }.getOrNull()
    }
}
