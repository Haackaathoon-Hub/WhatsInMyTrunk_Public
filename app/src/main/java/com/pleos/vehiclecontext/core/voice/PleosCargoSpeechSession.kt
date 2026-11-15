package com.pleos.vehiclecontext.core.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** One foreground conversation, with an offline decoder if Pleos Speech Service cannot answer. */
class PleosCargoSpeechSession(private val context: Context) : AutoCloseable {
    private var service: SpeechToText? = null

    suspend fun prepare() {
        val initialized = CompletableDeferred<Boolean>()
        try {
            val stt = SpeechToText(context, Mode.ON_DEVICE).also { service = it }
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() { initialized.complete(true) }
                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "PLEOS_STT_INIT_FAILED reason=$errorType")
                    initialized.complete(false)
                }
            })
            if (withTimeoutOrNull(2_500) { initialized.await() } != true) {
                Log.i(TAG, "PLEOS_STT_UNAVAILABLE")
                close()
                return
            }
            stt.setLocale(Locale.KOREA)
            Log.i(TAG, "PLEOS_STT_READY")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "PLEOS_STT_INIT_FAILED", failure)
            close()
        } catch (failure: LinkageError) {
            Log.w(TAG, "PLEOS_STT_SDK_UNAVAILABLE", failure)
            close()
        }
    }

    suspend fun listen(quantity: Boolean, onListening: suspend (String) -> Unit, onStatus: (String) -> Unit): String {
        if (quantity) {
            onStatus("수량 인식 준비 중 · 띠링 뒤에 말씀해 주세요")
            val count = try {
                CargoSpeechRecognizer.listen(CargoSpeechRecognizer.prepare(context), true) {
                    onListening("수량 인식 · 듣는 중")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "QUANTITY_GRAMMAR_FAILED", failure)
                ""
            } catch (failure: LinkageError) {
                Log.w(TAG, "QUANTITY_GRAMMAR_UNAVAILABLE", failure)
                ""
            }
            if (count.isNotBlank()) return count
        }
        service?.let { stt ->
            onStatus(if (quantity) "수량 다시 듣기 준비 · 띠링 뒤에 말씀해 주세요" else "Pleos 음성 인식 연결 중")
            val text = try {
                recognize(stt, quantity) { onListening("Pleos 음성 인식 · 듣는 중") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "PLEOS_STT_REQUEST_FAILED", failure)
                null
            } catch (failure: LinkageError) {
                Log.w(TAG, "PLEOS_STT_SDK_UNAVAILABLE", failure)
                null
            }
            if (!text.isNullOrBlank()) {
                Log.i(TAG, "PLEOS_STT_RESULT")
                if (!quantity || spokenQuantity(text) != null) return text
                Log.i(TAG, "PLEOS_STT_QUANTITY_RETRY")
            } else {
                Log.i(TAG, "PLEOS_STT_SWITCH_TO_OFFLINE")
                close()
            }
        }
        if (quantity) return ""
        onStatus("오프라인 음성 인식 준비 중")
        val model = CargoSpeechRecognizer.prepare(context)
        return CargoSpeechRecognizer.listen(model, quantity) { onListening("오프라인 음성 인식 · 듣는 중") }
    }

    private suspend fun recognize(stt: SpeechToText, quantity: Boolean, onListening: suspend () -> Unit): String? {
        val ready = CompletableDeferred<Unit>()
        val result = CompletableDeferred<String?>()
        val listener = object : ResultListener {
            override fun onReady() { ready.complete(Unit) }
            override fun onStartedRecognition() { ready.complete(Unit) }
            override fun onUpdated(stt: String, completed: Boolean) {
                ready.complete(Unit)
                if (completed) result.complete(stt.trim())
            }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onEndedRecognition() = Unit
            override fun onError(errorType: ErrorType) {
                Log.w(TAG, "PLEOS_STT_ERROR reason=$errorType")
                result.complete(null)
            }
        }
        try {
            stt.addListener(listener)
            val requestedAt = SystemClock.elapsedRealtime()
            stt.request()
            val listening = withTimeoutOrNull(2_500) {
                select {
                    ready.onAwait { true }
                    result.onAwait { false }
                }
            }
            return when (listening) {
                true -> {
                    val listeningAt = SystemClock.elapsedRealtime()
                    Log.i(TAG, "PLEOS_STT_LISTENING elapsed=$listeningAt requestMs=${listeningAt - requestedAt}")
                    withContext(Dispatchers.Main.immediate) { onListening() }
                    withTimeoutOrNull(if (quantity) 5_000L else 8_000L) { result.await() }
                }
                false -> result.await()
                null -> {
                    Log.w(TAG, "PLEOS_STT_LISTENING_TIMEOUT")
                    null
                }
            }
        } finally {
            runCatching { stt.removeListener(listener) }
            runCatching { stt.stop() }
        }
    }

    override fun close() {
        val stt = service ?: return
        service = null
        runCatching { stt.release() }
            .onFailure { Log.w(TAG, "PLEOS_STT_RELEASE_FAILED", it) }
    }

    private companion object {
        const val TAG = "PleosSpeech"
    }
}
