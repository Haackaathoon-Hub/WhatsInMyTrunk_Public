package com.pleos.vehiclecontext.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class CargoVoiceQuestion(val label: String, val prompt: String, val clip: String) {
    ALL_IN_ONE("일괄 음성 입력", "항목, 개수, 내릴 목적지를 한 번에 말씀해 주세요.", "registration.wav"),
    NAME("짐 이름", "어떤 짐을 실으셨나요?", "name.wav"),
    QUANTITY("수량", "몇 개를 실으셨나요?", "quantity.wav"),
    PURPOSE("사용 목적", "어떤 용도로 쓰실 건가요?", "purpose.wav"),
    TARGET("내릴 장소", "어디에서 내리실 건가요?", "target.wav")
}

class CargoPromptPlayer(private val context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val speaker = lazy {
        TextToSpeech(context.applicationContext) { status ->
            Handler(Looper.getMainLooper()).post { initialize(status) }
        }
    }
    private val engine by speaker
    private var closed = false

    /** The fixed Korean questions use packaged neural clips; composite prompts prefer Korean TTS with clip fallback. */
    suspend fun say(question: CargoVoiceQuestion) {
        if (question == CargoVoiceQuestion.ALL_IN_ONE) {
            val spokeTts = try {
                val tts = engine
                withTimeoutOrNull(2_000) { ready.await() } == true && speak(tts, question.prompt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "ALL_IN_ONE_TTS_FAILED", error)
                false
            }
            if (spokeTts) {
                Log.i(TAG, "TTS_SPOKEN_FINISHED ${question.name}")
                return
            }
        }
        try {
            playClip(question.clip)
            Log.i(TAG, "TTS_CLIP_FINISHED ${question.name}")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "TTS_CLIP_UNAVAILABLE ${question.name}", error)
        }
    }

    suspend fun sayBackgroundRegistration() {
        runCatching { playClip("registration.wav") }
            .onFailure { if (it is CancellationException) throw it }
    }

    suspend fun sayGleoRegistration() {
        runCatching { playClip("gleo_question.wav") }
            .onFailure { if (it is CancellationException) throw it }
    }

    suspend fun playListeningCue() {
        try {
            playClip("listening.wav", takeFocus = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "LISTENING_CUE_UNAVAILABLE", error)
        }
    }

    /** Read variable cargo details only when the device has a verified local Korean voice. */
    suspend fun sayArrival(text: String) {
        val played = try {
            val tts = engine
            withTimeoutOrNull(2_500) { ready.await() } == true && speak(tts, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "ARRIVAL_ANDROID_TTS_FAILED", error)
            false
        }
        if (played) {
            Log.i(TAG, "ARRIVAL_KOREAN_TTS_FINISHED")
        } else {
            runCatching { playClip("arrival.wav") }
            Log.i(TAG, "ARRIVAL_CLIP_FINISHED")
        }
    }

    private fun initialize(status: Int) {
        if (closed) return
        val supported = status == TextToSpeech.SUCCESS && runCatching {
            val tts = engine
            val voice = tts.voices.orEmpty()
                .filter {
                    it.locale.language == "ko" && !it.isNetworkConnectionRequired &&
                        it.quality >= Voice.QUALITY_NORMAL &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                }
                .maxWithOrNull(compareBy<Voice> { it.locale.country == "KR" }.thenBy { it.quality }.thenBy { -it.latency })
            if (voice == null) false else {
                tts.setAudioAttributes(attributes)
                tts.setVoice(voice) == TextToSpeech.SUCCESS && tts.voice?.locale?.language == "ko"
            }
        }.getOrDefault(false)
        ready.complete(supported)
    }

    private suspend fun speak(tts: TextToSpeech, text: String): Boolean {
        if (text.length > TextToSpeech.getMaxSpeechInputLength()) return false
        val id = UUID.randomUUID().toString()
        val completed = CompletableDeferred<Boolean>()
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == id) completed.complete(true) }
            @Deprecated("Android also calls the overload with an error code")
            override fun onError(utteranceId: String?) { if (utteranceId == id) completed.complete(false) }
        }
        val focus = requestFocus { completed.complete(false) }
        var played = false
        try {
            val parameters = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.82f)
            }
            if (tts.setOnUtteranceProgressListener(listener) != TextToSpeech.SUCCESS ||
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, parameters, id) != TextToSpeech.SUCCESS) return false
            val timeout = (text.length * 180L).coerceIn(12_000L, 60_000L)
            played = withTimeoutOrNull(timeout) { completed.await() } == true
            return played
        } finally {
            if (!played && !closed) tts.stop()
            focus?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }

    private suspend fun playClip(clip: String, takeFocus: Boolean = true) = withContext(Dispatchers.IO) {
        val wav = context.assets.open("cargo_voice/$clip").use { it.readBytes() }
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        val size = wav.size - PCM_OFFSET
        check(size > 2 && size % 2 == 0 && header.getShort(20).toInt() == 1 &&
            header.getShort(22).toInt() == 1 && header.getInt(24) == PCM_RATE &&
            header.getShort(34).toInt() == 16 && header.getInt(40) == size)
        val frames = size / 2
        val track = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(PCM_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(size)
            .build()
        val completed = CompletableDeferred<Boolean>()
        var focus: AudioFocusRequest? = null
        try {
            check(track.write(wav, PCM_OFFSET, size) == size && track.state == AudioTrack.STATE_INITIALIZED)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) { completed.complete(true) }
                override fun onPeriodicNotification(track: AudioTrack) = Unit
            }, Handler(Looper.getMainLooper()))
            check(track.setNotificationMarkerPosition(frames - 1) == AudioTrack.SUCCESS)
            if (takeFocus) focus = requestFocus { completed.complete(false) }
            track.play()
            if (!takeFocus) Log.i(TAG, "LISTENING_CUE_PLAY elapsed=${SystemClock.elapsedRealtime()}")
            val markerGraceMs = if (takeFocus) 750L else 250L
            val played = withTimeoutOrNull(frames * 1000L / PCM_RATE + markerGraceMs) { completed.await() }
            check(played == true || (played == null && track.playbackHeadPosition >= frames - 1)) {
                "음성 안내를 재생하지 못했습니다."
            }
        } finally {
            track.release()
            focus?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }

    private fun requestFocus(onLoss: () -> Unit): AudioFocusRequest? {
        val manager = audioManager ?: return null
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    onLoss()
                }
            }
            .build()
        return if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request else {
            Log.w(TAG, "PROMPT_AUDIO_FOCUS_UNAVAILABLE")
            null
        }
    }

    fun close() {
        closed = true
        if (speaker.isInitialized()) {
            engine.stop()
            engine.shutdown()
        }
    }

    private companion object {
        const val TAG = "PleosSpeech"
        const val PCM_OFFSET = 44
        const val PCM_RATE = 48_000
    }
}
