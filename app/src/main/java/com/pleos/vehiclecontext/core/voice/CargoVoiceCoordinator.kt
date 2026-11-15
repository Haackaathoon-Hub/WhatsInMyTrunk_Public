package com.pleos.vehiclecontext.core.voice

import android.content.Context
import android.util.Log
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.settings.isShoppingContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoiceSessionMode {
    IDLE,
    QUICK_NOTE,
    GUIDED_REGISTRATION
}

data class VoiceInputUiState(
    val active: Boolean = false,
    val automatic: Boolean = false,
    val mode: VoiceSessionMode = VoiceSessionMode.IDLE,
    val question: CargoVoiceQuestion? = null,
    val status: String = "트렁크가 닫히면 음성 또는 버튼으로 짐을 바로 기록할 수 있습니다.",
    val listening: Boolean = false,
    val recognizedPreview: String = "",
    val needsQuantityChoice: Boolean = false,
    val lastError: String? = null
)

/**
 * 1차 통합 음성 입력("항목 + 개수 + 목적지") 후 이해하지 못한 정보만 재질문할 때 사용하는 어댑터
 */
internal interface GuidedVoiceChannel {
    suspend fun ask(question: CargoVoiceQuestion, isQuantity: Boolean, customPromptStatus: String? = null): String
    suspend fun askQuantityWithTapFallback(customPromptStatus: String? = null): Int
}

/**
 * Pleos SpeechToText SDK + Vosk 오프라인 한국어 음성 인식 및 뉴럴 WAV 안내를 조율하여
 * 트렁크 자동 적재 감지(1차 통합 입력 후 누락 항목만 재질문)와 원터치 음성 메모(AI 적재 분석)를
 * 모두 단일 TripContextEngine 백엔드로 연결합니다.
 */
class CargoVoiceCoordinator(
    private val context: Context,
    private val engine: TripContextEngine,
    private val settingsProvider: () -> AppSettings,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState.asStateFlow()

    private var promptPlayer: CargoPromptPlayer? = null
    private var activeJob: Job? = null
    private var quantityChoiceDeferred: CompletableDeferred<Int?>? = null

    fun startGuidedRegistration(
        automatic: Boolean = false,
        autoRequestId: String? = null,
        hasRecordAudioPermission: Boolean = true
    ) {
        if (!hasRecordAudioPermission) {
            autoRequestId?.let { engine.releaseAutoVoiceRegistration(it) }
            _uiState.update {
                it.copy(
                    active = false,
                    mode = VoiceSessionMode.IDLE,
                    listening = false,
                    lastError = "마이크 권한(RECORD_AUDIO)이 필요합니다."
                )
            }
            return
        }
        if (autoRequestId != null && !engine.claimAutoVoiceRegistration(autoRequestId)) {
            return
        }
        cancelActiveSession()
        val scopeJob = externalScope.launch {
            runGuidedRegistration(automatic = automatic, autoRequestId = autoRequestId)
        }
        activeJob = scopeJob
    }

    fun startQuickVoiceNote(hasRecordAudioPermission: Boolean = true) {
        if (!hasRecordAudioPermission) {
            _uiState.update {
                it.copy(
                    active = false,
                    mode = VoiceSessionMode.IDLE,
                    listening = false,
                    lastError = "마이크 권한(RECORD_AUDIO)이 필요합니다."
                )
            }
            return
        }
        cancelActiveSession()
        val scopeJob = externalScope.launch {
            runQuickVoiceNote()
        }
        activeJob = scopeJob
    }

    fun selectManualQuantity(amount: Int) {
        quantityChoiceDeferred?.complete(amount.coerceIn(1, 99))
    }

    fun stopVoice(reason: String = "음성 입력을 종료했습니다.") {
        cancelActiveSession()
        _uiState.value = VoiceInputUiState(
            active = false,
            mode = VoiceSessionMode.IDLE,
            status = reason,
            listening = false,
            needsQuantityChoice = false
        )
    }

    fun clearError() {
        _uiState.update { it.copy(lastError = null) }
    }

    private fun cancelActiveSession() {
        quantityChoiceDeferred?.cancel()
        quantityChoiceDeferred = null
        activeJob?.cancel()
        activeJob = null
    }

    private suspend fun runQuickVoiceNote() {
        val session = PleosCargoSpeechSession(context)
        val player = getOrCreatePlayer()
        try {
            _uiState.value = VoiceInputUiState(
                active = true,
                automatic = false,
                mode = VoiceSessionMode.QUICK_NOTE,
                status = "음성 인식 준비 중...",
                listening = false
            )
            session.prepare()
            player.playListeningCue()
            delay(150)
            val utterance = session.listen(
                quantity = false,
                onListening = { label ->
                    _uiState.update {
                        it.copy(
                            active = true,
                            listening = true,
                            status = label
                        )
                    }
                },
                onStatus = { msg ->
                    _uiState.update { it.copy(status = msg) }
                }
            )
            val cleaned = utterance.trim()
            if (cleaned.isNotBlank()) {
                val curSession = engine.session.value
                val currentPoi = curSession.closureHistory.lastOrNull()?.openedAtPoi ?: PoiCategory.MART_SHOPPING
                engine.processSignal(
                    VehicleSignal.VoiceCargoNoteReceived(
                        utterance = cleaned,
                        currentLocationPoi = currentPoi,
                        origin = SignalOrigin.AI_AGENT_INFERRED
                    )
                )
                _uiState.value = VoiceInputUiState(
                    active = false,
                    mode = VoiceSessionMode.IDLE,
                    status = "음성 등록 완료: \"$cleaned\"",
                    recognizedPreview = cleaned
                )
            } else {
                _uiState.value = VoiceInputUiState(
                    active = false,
                    mode = VoiceSessionMode.IDLE,
                    status = "인식된 음성이 없습니다. 다시 시도해 주세요."
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "QUICK_VOICE_FAILED", error)
            _uiState.value = VoiceInputUiState(
                active = false,
                mode = VoiceSessionMode.IDLE,
                status = "음성 입력을 완료하지 못했습니다.",
                lastError = error.message
            )
        } finally {
            session.close()
        }
    }

    private suspend fun runGuidedRegistration(
        automatic: Boolean,
        autoRequestId: String?
    ) {
        val session = PleosCargoSpeechSession(context)
        val player = getOrCreatePlayer()
        val settings = settingsProvider()
        val channel = object : GuidedVoiceChannel {
            override suspend fun ask(
                question: CargoVoiceQuestion,
                isQuantity: Boolean,
                customPromptStatus: String?
            ): String = askQuestion(session, player, question, isQuantity, customPromptStatus)

            override suspend fun askQuantityWithTapFallback(customPromptStatus: String?): Int =
                this@CargoVoiceCoordinator.askQuantityWithFallback(session, player, customPromptStatus)
        }
        try {
            _uiState.value = VoiceInputUiState(
                active = true,
                automatic = automatic,
                mode = VoiceSessionMode.GUIDED_REGISTRATION,
                question = CargoVoiceQuestion.ALL_IN_ONE,
                status = if (automatic) {
                    "트렁크 닫힘 감지 · 항목, 개수, 목적지를 한 번에 말씀해 주세요."
                } else {
                    "항목, 개수, 내릴 목적지를 한 번에 말씀해 주세요."
                }
            )
            session.prepare()
            executeGuidedVoiceRegistration(
                engine = engine,
                settings = settings,
                channel = channel,
                onStateUpdate = { transform -> _uiState.update(transform) }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "GUIDED_VOICE_FAILED", error)
            _uiState.value = VoiceInputUiState(
                active = false,
                mode = VoiceSessionMode.IDLE,
                status = "음성 등록 중 문제가 발생했습니다.",
                lastError = error.message
            )
        } finally {
            if (autoRequestId != null) {
                engine.releaseAutoVoiceRegistration(autoRequestId)
            }
            quantityChoiceDeferred?.cancel()
            quantityChoiceDeferred = null
            session.close()
        }
    }

    private suspend fun askQuestion(
        session: PleosCargoSpeechSession,
        player: CargoPromptPlayer,
        question: CargoVoiceQuestion,
        isQuantity: Boolean,
        customPromptStatus: String? = null
    ): String {
        _uiState.update {
            it.copy(
                active = true,
                question = question,
                listening = false,
                needsQuantityChoice = false,
                status = customPromptStatus ?: question.prompt
            )
        }
        player.say(question)
        player.playListeningCue()
        delay(150)
        return session.listen(
            quantity = isQuantity,
            onListening = { label ->
                _uiState.update {
                    it.copy(
                        listening = true,
                        status = "${question.label} · $label"
                    )
                }
            },
            onStatus = { msg ->
                _uiState.update { it.copy(status = msg) }
            }
        ).also { result ->
            _uiState.update {
                it.copy(
                    listening = false,
                    recognizedPreview = if (result.isNotBlank()) result else it.recognizedPreview
                )
            }
        }
    }

    private suspend fun askQuantityWithFallback(
        session: PleosCargoSpeechSession,
        player: CargoPromptPlayer,
        customPromptStatus: String? = null
    ): Int {
        val raw = askQuestion(session, player, CargoVoiceQuestion.QUANTITY, isQuantity = true, customPromptStatus)
        spokenQuantity(raw)?.let { return it }

        // 인식되지 않으면 화면에서 1~5개 버튼 탭으로 바로 선택 가능하게 대기
        val deferred = CompletableDeferred<Int?>()
        quantityChoiceDeferred = deferred
        _uiState.update {
            it.copy(
                active = true,
                question = CargoVoiceQuestion.QUANTITY,
                listening = false,
                needsQuantityChoice = true,
                status = "수량을 아래 버튼에서 선택하거나 다시 말씀해 주세요."
            )
        }
        return deferred.await() ?: 1
    }

    private fun getOrCreatePlayer(): CargoPromptPlayer {
        return promptPlayer ?: CargoPromptPlayer(context.applicationContext).also { promptPlayer = it }
    }

    fun close() {
        cancelActiveSession()
        promptPlayer?.close()
        promptPlayer = null
    }

    companion object {
        private const val TAG = "CargoVoiceCoordinator"

        /**
         * 1차 통합 음성 입력("항목 + 개수 + 목적지")을 먼저 수신한 뒤,
         * 이해하지 못했거나 빠진 정보만 골라서 재질문하고 구조화 화물로 등록합니다.
         */
        internal suspend fun executeGuidedVoiceRegistration(
            engine: TripContextEngine,
            settings: AppSettings,
            channel: GuidedVoiceChannel,
            onStateUpdate: ((VoiceInputUiState) -> VoiceInputUiState) -> Unit = {}
        ): Boolean {
            val activeDestination = engine.session.value.destinationName

            // 1단계: 항목 · 개수 · 목적지를 한 번에 입력받기
            val rawCombined = channel.ask(
                question = CargoVoiceQuestion.ALL_IN_ONE,
                isQuantity = false
            )
            val initialDraft = parseCombinedCargoUtterance(rawCombined, activeDestination)

            var cargoName = initialDraft.itemName.orEmpty()
            var quantity = initialDraft.quantity
            var isBundle = initialDraft.isBundle
            var targetDestination = initialDraft.targetDestination.orEmpty()
            var purpose = initialDraft.purpose.orEmpty()

            fun buildPartialPreview(): String = listOfNotNull(
                cargoName.takeIf { it.isNotBlank() },
                quantity?.let { if (isBundle) "${it}묶음" else "${it}개" },
                targetDestination.takeIf { it.isNotBlank() }
            ).joinToString(" · ")

            fun buildMissingPrompt(question: CargoVoiceQuestion): String {
                val understood = buildPartialPreview()
                return if (understood.isBlank()) {
                    "잘 이해하지 못해 다시 여쭤볼게요 · ${question.prompt}"
                } else {
                    "인식됨($understood) · 놓친 '${question.label}'만 다시 말씀해 주세요."
                }
            }

            // 2단계: 이해하지 못한 정보만 골라서 재질문
            if (cargoName.isBlank()) {
                val rawName = channel.ask(
                    question = CargoVoiceQuestion.NAME,
                    isQuantity = false,
                    customPromptStatus = buildMissingPrompt(CargoVoiceQuestion.NAME)
                )
                val retryDraft = parseCombinedCargoUtterance(rawName, activeDestination)
                cargoName = retryDraft.itemName?.takeIf { it.isNotBlank() } ?: spokenCargoName(rawName)
                if (quantity == null && retryDraft.quantity != null) {
                    quantity = retryDraft.quantity
                    isBundle = isBundle || retryDraft.isBundle
                }
                if (targetDestination.isBlank() && !retryDraft.targetDestination.isNullOrBlank()) {
                    targetDestination = retryDraft.targetDestination
                }
                if (purpose.isBlank() && !retryDraft.purpose.isNullOrBlank()) {
                    purpose = retryDraft.purpose
                }
                if (cargoName.isBlank()) {
                    onStateUpdate {
                        VoiceInputUiState(
                            active = false,
                            mode = VoiceSessionMode.IDLE,
                            status = "품목 이름이 인식되지 않아 자동 등록을 취소했습니다."
                        )
                    }
                    return false
                }
            }

            val isShoppingBundle = settings.autoShoppingBundle &&
                isShoppingContext(cargoName, targetDestination.ifBlank { activeDestination })
            if (quantity == null && isShoppingBundle) {
                quantity = 1
                isBundle = true
            }

            if (quantity == null) {
                quantity = channel.askQuantityWithTapFallback(
                    customPromptStatus = buildMissingPrompt(CargoVoiceQuestion.QUANTITY)
                )
            }

            if (targetDestination.isBlank()) {
                val rawTarget = channel.ask(
                    question = CargoVoiceQuestion.TARGET,
                    isQuantity = false,
                    customPromptStatus = buildMissingPrompt(CargoVoiceQuestion.TARGET)
                )
                val retryDraft = parseCombinedCargoUtterance(rawTarget, activeDestination)
                targetDestination = retryDraft.targetDestination
                    ?.takeIf { it.isNotBlank() }
                    ?: spokenPlace(rawTarget, activeDestination).ifBlank { activeDestination }
            }

            val isPermanent = targetDestination == "상시 보관"
            val finalQty = quantity.coerceIn(1, 999)
            val countAsBundle = isBundle || (isShoppingBundle && finalQty == 1)
            val result = engine.registerStructuredCargo(
                name = cargoName,
                quantity = finalQty,
                purpose = purpose,
                targetDestinationName = if (isPermanent) null else targetDestination,
                countAsBundle = countAsBundle,
                isPermanent = isPermanent,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
            onStateUpdate {
                VoiceInputUiState(
                    active = false,
                    mode = VoiceSessionMode.IDLE,
                    status = result.message,
                    recognizedPreview = "$cargoName (${if (countAsBundle) "${finalQty}묶음" else "${finalQty}개"}) · $targetDestination"
                )
            }
            return result.success
        }
    }
}
