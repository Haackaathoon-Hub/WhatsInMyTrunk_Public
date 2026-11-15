package com.pleos.vehiclecontext.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs

/** Uses PCM from the foreground app's microphone and an offline Korean decoder. */
object CargoSpeechRecognizer {
    private const val TAG = "PleosSpeech"
    private const val RATE = 16_000
    private const val MODEL_DIR = "vosk-model-small-ko-0.22"
    private val modelLock = Mutex()
    private var model: Model? = null

    suspend fun prepare(context: Context): Model = withContext(Dispatchers.IO) {
        modelLock.withLock {
            model ?: Model(extractModel(context).absolutePath).also {
                model = it
                Log.i(TAG, "OFFLINE_MODEL_READY")
            }
        }
    }

    @SuppressLint("MissingPermission") // The foreground Activity checks RECORD_AUDIO before calling.
    suspend fun listen(model: Model, quantity: Boolean, onListening: suspend () -> Unit): String = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(bufferSize > 0) { "마이크의 16kHz 입력을 사용할 수 없습니다." }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(bufferSize, RATE)
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "마이크를 열 수 없습니다." }
            val recognizer = if (quantity) Recognizer(model, RATE.toFloat(), koreanQuantityGrammar)
            else Recognizer(model, RATE.toFloat())
            recognizer.use { decoder ->
                if (quantity) {
                    decoder.setEndpointerMode(Recognizer.EndpointerMode.SHORT)
                    decoder.setWords(true)
                }
                currentCoroutineContext().ensureActive()
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "마이크 녹음이 시작되지 않았습니다." }
                Log.i(TAG, "OFFLINE_LISTENING elapsed=${SystemClock.elapsedRealtime()}")
                val cue = launch(Dispatchers.Main.immediate) { onListening() }
                val samples = ShortArray(RATE / 10)
                val quantityAudio = if (quantity) ShortArray(RATE * 5) else null
                val deadline = SystemClock.elapsedRealtime() + if (quantity) 5_000L else 8_000L
                var peak = 0
                var captured = 0
                var result: JSONObject? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    currentCoroutineContext().ensureActive()
                    val read = recorder.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
                    check(read >= 0) { "마이크 입력 오류 ($read)." }
                    if (read == 0) {
                        delay(20)
                        continue
                    }
                    // The mic is already live, but the decoder should start after the audible cue.
                    if (!cue.isCompleted) continue
                    for (index in 0 until read) peak = maxOf(peak, abs(samples[index].toInt()))
                    if (quantityAudio != null) {
                        val copied = minOf(read, quantityAudio.size - captured)
                        samples.copyInto(quantityAudio, captured, endIndex = copied)
                        captured += copied
                    }
                    if (decoder.acceptWaveForm(samples, read)) {
                        val segment = JSONObject(decoder.result)
                        if (segment.optString("text").isNotBlank()) {
                            result = segment
                            break
                        }
                    }
                }
                val decoded = result ?: JSONObject(decoder.finalResult)
                val answer = if (quantityAudio == null) decoded.optString("text").trim() else {
                    recorder.stop()
                    confirmQuantity(model, decoded, quantityAudio, captured)
                }
                if (answer.isNotEmpty()) {
                    Log.i(TAG, "OFFLINE_RESULT")
                    return@withContext answer
                }
                Log.w(TAG, if (peak < 100) "OFFLINE_NO_MIC_SIGNAL" else "OFFLINE_NO_MATCH")
                if (peak < 100) error("마이크에서 음성이 들어오지 않았어요. 에뮬레이터의 PC 마이크 입력을 확인해 주세요.")
                if (quantity) return@withContext ""
                error("답변을 알아듣지 못했어요. 한 항목만 짧게 말씀해 주세요.")
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun confirmQuantity(model: Model, constrained: JSONObject, audio: ShortArray, length: Int): String {
        if (length == 0 || spokenQuantity(constrained.optString("text")) == null) return ""
        return Recognizer(model, RATE.toFloat()).use { verifier ->
            val raw = if (verifier.acceptWaveForm(audio, length)) verifier.result else verifier.finalResult
            confirmedQuantityResult(constrained, JSONObject(raw).optString("text"))
        }
    }

    private suspend fun extractModel(context: Context): File {
        val target = File(context.noBackupFilesDir, MODEL_DIR)
        if (File(target, "graph/HCLr.fst").isFile()) return target
        val temporary = File(context.noBackupFilesDir, "$MODEL_DIR.tmp")
        temporary.deleteRecursively()
        check(temporary.mkdirs()) { "한국어 모델을 저장할 공간을 만들 수 없습니다." }
        try {
            val base = temporary.canonicalPath + File.separator
            ZipInputStream(context.assets.open("cargo_voice/vosk-ko-0.22.zip").buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    currentCoroutineContext().ensureActive()
                    val relative = entry.name.removePrefix("$MODEL_DIR/")
                    if (relative.isEmpty()) continue
                    check(entry.name.startsWith("$MODEL_DIR/"))
                    val output = File(temporary, relative)
                    check(output.canonicalPath.startsWith(base))
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                currentCoroutineContext().ensureActive()
                                stream.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
            check(File(temporary, "graph/HCLr.fst").isFile()) { "한국어 모델 파일을 찾지 못했습니다." }
            target.deleteRecursively()
            check(temporary.renameTo(target)) { "한국어 모델 설치를 마칠 수 없습니다." }
            return target
        } finally {
            temporary.deleteRecursively()
        }
    }
}
