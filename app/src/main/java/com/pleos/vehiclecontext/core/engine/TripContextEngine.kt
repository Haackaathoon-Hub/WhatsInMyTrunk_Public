package com.pleos.vehiclecontext.core.engine

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.ai.AiContextService
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.AutonomousDrivingProfile
import com.pleos.vehiclecontext.core.model.CargoItemRecord
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ClosureUsageRecord
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PersonalizedCargoProfile
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.TripTimelineStep
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.settings.isShoppingContext
import com.pleos.vehiclecontext.core.voice.extractQuantityFromUtterance
import com.pleos.vehiclecontext.core.voice.samePlaceName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CargoActionResult(
    val success: Boolean,
    val message: String,
    val itemId: String? = null
)

/**
 * Layer 2 & Layer 3 통합 엔진 (차량 센서 + 자율주행 상태 + AI 에이전트 결합):
 * 1. 차량 센서/내비/자율주행/음성 신호(VehicleSignal)를 수집해 TripSession 맥락 상태를 전이합니다.
 * 2. AI 컨텍스트 에이전트(AiContextService)를 통해 자연어 적재 메모를 분석하고 브리핑을 개인화합니다.
 * 3. 중간 정차 필터, 반복 P 전환 중복 방지, 경로 변경 갱신, 자율주행 모드별 화면 노출 가드, 앱 재시작 복원을 보장합니다.
 */
class TripContextEngine(
    private val plugins: List<ScenarioPlugin>,
    private val persistence: SessionPersistence = InMemorySessionPersistence(),
    private val actionDispatcher: ActionDispatcher = ActionDispatcher(),
    private val aiService: AiContextService = PleosAiContextAgent(),
    private val settingsProvider: () -> AppSettings = { AppSettings() }
) {
    private val _session = MutableStateFlow(persistence.restore() ?: TripSession())
    val session: StateFlow<TripSession> = _session.asStateFlow()

    private val _enabledPluginIds = MutableStateFlow(
        plugins.map { it.id }.toSet()
    )
    val enabledPluginIds: StateFlow<Set<String>> = _enabledPluginIds.asStateFlow()

    private val _activePrompt = MutableStateFlow<ContextAction?>(null)
    val activePrompt: StateFlow<ContextAction?> = _activePrompt.asStateFlow()

    private val _eventLog = MutableStateFlow<List<String>>(emptyList())
    val eventLog: StateFlow<List<String>> = _eventLog.asStateFlow()

    private var claimedAutoVoiceRequestId: String? = null

    init {
        val restored = persistence.restore()
        if (restored != null) {
            appendLog("[상태 복원] 이전 이동 세션(${restored.phase.title}, 목적지: ${restored.destinationName})을 복원했습니다.")
        }
    }

    fun getRegisteredPlugins(): List<ScenarioPlugin> = plugins.sortedBy { it.priority }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        val updated = _enabledPluginIds.value.toMutableSet()
        if (enabled) updated.add(pluginId) else updated.remove(pluginId)
        _enabledPluginIds.value = updated
        appendLog("[플러그인 설정] $pluginId ${if (enabled) "활성화" else "비활성화"}")
    }

    /**
     * 신호 유입 시 세션 상태를 갱신하고 시나리오 플러그인 및 AI 컨텍스트를 평가합니다.
     */
    fun processSignal(signal: VehicleSignal) {
        val current = _session.value
        val updated = reduceSession(current, signal)
        _session.value = updated
        persistence.save(updated)

        // 수동 운전(MANUAL) 중 주행 기어(D/R)일 때는 운전 방해 방지를 위해 일반 하차 팝업을 내림
        // 단, 자율주행(Lv.3+ 또는 AVP Drop-off) 모드에서는 자율주행 전용 카드 노출을 허용
        if ((updated.currentGear == GearState.DRIVE || updated.currentGear == GearState.REVERSE) &&
            !updated.autonomousMode.allowsRichUiWhileMoving
        ) {
            val active = _activePrompt.value
            if (active != null && !active.isDrivingSafeVoiceOnly && !active.canDisplayInAutonomousDriving) {
                _activePrompt.value = null
            }
        }

        evaluateScenarios(updated, signal)
    }

    private fun reduceSession(current: TripSession, signal: VehicleSignal): TripSession {
        return when (signal) {
            is VehicleSignal.DoorStateChanged -> {
                if (signal.isOpen) {
                    appendLog("[${signal.origin.displayName}] ${signal.zone.displayName} 열림 (${signal.currentLocationPoi.displayName})")
                    if (signal.zone == DoorZone.TRUNK) {
                        claimedAutoVoiceRequestId = null
                    }
                    current.copy(
                        openDoors = current.openDoors + signal.zone,
                        pendingOpenPoiByZone = current.pendingOpenPoiByZone + (signal.zone to signal.currentLocationPoi),
                        pendingCargoTagByZone = current.pendingCargoTagByZone + (signal.zone to signal.cargoTag),
                        pendingLoadingPromptRequestId = if (signal.zone == DoorZone.TRUNK) null else current.pendingLoadingPromptRequestId,
                        autoVoiceRegistrationId = if (signal.zone == DoorZone.TRUNK) null else current.autoVoiceRegistrationId
                    )
                } else {
                    val wasOpen = signal.zone in current.openDoors
                    val openPoi = current.pendingOpenPoiByZone[signal.zone] ?: signal.currentLocationPoi
                    val cargoTag = current.pendingCargoTagByZone[signal.zone] ?: signal.cargoTag
                    val newHistory = if (wasOpen) {
                        appendLog(
                            "[${signal.origin.displayName}] ${signal.zone.displayName} 닫힘 -> 이번 이동의 개폐 이력으로 기록됨 (${openPoi.displayName} / ${cargoTag.displayName})"
                        )
                        current.closureHistory + ClosureUsageRecord(
                            zone = signal.zone,
                            openedAtPoi = openPoi,
                            cargoTag = cargoTag,
                            origin = signal.origin,
                            closedAtTimestampMs = signal.timestampMs
                        )
                    } else {
                        appendLog("[${signal.origin.displayName}] ${signal.zone.displayName} 닫힘 신호 수신")
                        current.closureHistory
                    }
                    val matchingUnloadItems = if (wasOpen && signal.zone == DoorZone.TRUNK && current.isArrivedAtDestination && current.currentGear == GearState.PARK) {
                        current.cargoInventory.filter { item ->
                            !item.isUnloaded && !item.isPermanent && item.remainingQuantity > 0 &&
                                samePlaceName(item.targetDestinationName, current.destinationName)
                        }
                    } else {
                        emptyList()
                    }
                    val isUnloadingClose = matchingUnloadItems.isNotEmpty()
                    val hasRecentSpecificItem = current.cargoInventory.any { item ->
                        !item.isUnloaded &&
                            (item.origin == SignalOrigin.AI_AGENT_INFERRED || !item.id.startsWith("trunk-sensor-")) &&
                            (signal.timestampMs - item.loadedTimestampMs) in 0L..180_000L
                    }
                    val inferenceResult = if (wasOpen && signal.zone == DoorZone.TRUNK && !isUnloadingClose && !hasRecentSpecificItem) {
                        CargoDestinationInference.inferDestination(
                            loadedPoi = openPoi,
                            currentTimeMs = signal.timestampMs,
                            profile = current.personalizedProfile,
                            explicitDestinationPoi = current.destinationPoi,
                            explicitDestinationName = current.destinationName
                        )
                    } else null

                    val updatedInventory = if (wasOpen && signal.zone == DoorZone.TRUNK && cargoTag != CargoTag.PERMANENT_KEEP && !isUnloadingClose && !hasRecentSpecificItem) {
                        val defaultCargoName = when (cargoTag) {
                            CargoTag.FRESH_FOOD -> "신선/냉동 식재료"
                            CargoTag.FRAGILE -> "파손주의 물품"
                            CargoTag.GENERAL -> when (openPoi) {
                                PoiCategory.MART_SHOPPING -> "마트 장보기 짐"
                                PoiCategory.LEISURE_CAMPING -> "캠핑/레저 장비"
                                PoiCategory.AIRPORT_STATION -> "여행용 캐리어"
                                else -> "트렁크 적재 화물"
                            }
                            else -> "트렁크 적재 화물"
                        }
                        val isPerishable = cargoTag == CargoTag.FRESH_FOOD || openPoi == PoiCategory.MART_SHOPPING
                        val itemRecord = CargoItemRecord(
                            id = "trunk-sensor-${signal.timestampMs}-${current.cargoInventory.size + 1}",
                            name = defaultCargoName,
                            zone = signal.zone,
                            categoryTag = cargoTag,
                            loadedPoi = openPoi.takeIf { it != PoiCategory.UNKNOWN } ?: current.originPoi,
                            originName = current.originName.takeIf { it.isNotBlank() },
                            targetDestinationPoi = current.destinationPoi,
                            targetDestinationName = current.destinationName,
                            inferredDestinationPoi = inferenceResult?.inferredPoi,
                            destinationConfidence = inferenceResult?.confidenceScore ?: 1.0f,
                            isDestinationAutoInferred = inferenceResult != null,
                            loadedTimestampMs = signal.timestampMs,
                            origin = signal.origin,
                            isPerishable = isPerishable,
                            isPermanent = false
                        )
                        if (inferenceResult?.matchedRoutine != null) {
                            appendLog("[🎯 개인화 루틴 감지] ${inferenceResult.reasoningDetail}")
                        } else if (inferenceResult != null) {
                            appendLog("[🧭 목적지 B 자동 추론] ${inferenceResult.reasoningDetail}")
                        }
                        current.cargoInventory + itemRecord
                    } else {
                        current.cargoInventory
                    }

                    val loadingRequestId = if (wasOpen && signal.zone == DoorZone.TRUNK && cargoTag != CargoTag.PERMANENT_KEEP && !isUnloadingClose && !hasRecentSpecificItem) {
                        "load-${signal.timestampMs}"
                    } else {
                        null
                    }
                    val autoVoiceId = if (loadingRequestId != null && settingsProvider().autoVoiceOnTrunkClose) {
                        loadingRequestId
                    } else {
                        null
                    }
                    if (isUnloadingClose) {
                        appendLog("[트렁크 하차 감지] '${current.destinationName}' 도착 상태에서 트렁크 닫힘 -> 수령/하차 확인 필요")
                    } else if (loadingRequestId != null) {
                        appendLog("[트렁크 적재 감지] 문 닫힘 감지 -> 자동 적재 등록 요청 ($loadingRequestId)")
                    }

                    val updatedTimeline = if (wasOpen) {
                        val record = newHistory.last()
                        val step = TripTimelineStep(
                            phaseLabel = "${current.phase.stepNumber} ${current.phase.title}",
                            title = if (isUnloadingClose) "${record.zone.displayName} 하차/수령 확인 대기" else "${record.zone.displayName} 적재/개폐 완료",
                            detail = "${record.openedAtPoi.displayName} · ${record.cargoTag.displayName}",
                            origin = record.origin,
                            isHighlight = record.zone == DoorZone.TRUNK,
                            timestampMs = record.closedAtTimestampMs
                        )
                        appendTimeline(current.timelineSteps, step)
                    } else {
                        current.timelineSteps
                    }
                    current.copy(
                        openDoors = current.openDoors - signal.zone,
                        closureHistory = newHistory,
                        cargoInventory = updatedInventory,
                        matchedRoutineTitle = inferenceResult?.matchedRoutine?.title ?: current.matchedRoutineTitle,
                        pendingLoadingPromptRequestId = loadingRequestId ?: current.pendingLoadingPromptRequestId,
                        autoVoiceRegistrationId = autoVoiceId ?: current.autoVoiceRegistrationId,
                        unloadConfirmationNeeded = if (isUnloadingClose) true else current.unloadConfirmationNeeded,
                        timelineSteps = updatedTimeline
                    )
                }
            }

            is VehicleSignal.GearChanged -> {
                var inferredArrived = current.isArrivedAtDestination
                var nextReversedFlag = current.hasReversedDuringDrive
                var nextIntermediateFlag = current.hasExplicitIntermediateStop
                var nextPromptedIds = current.promptedScenarioIdsForCurrentArrival
                var nextAckedIds = current.acknowledgedScenarioIds

                val nextPhase = when (signal.gear) {
                    GearState.DRIVE -> {
                        nextReversedFlag = false
                        nextIntermediateFlag = false
                        TripPhase.DRIVING
                    }
                    GearState.REVERSE -> {
                        if (current.phase == TripPhase.DRIVING ||
                            current.phase == TripPhase.INTERMEDIATE_STOP ||
                            current.phase == TripPhase.ARRIVED_PARKED ||
                            current.currentGear == GearState.DRIVE ||
                            current.currentGear == GearState.NEUTRAL
                        ) {
                            nextReversedFlag = true
                            // 이전 도착 후 다시 주행·후진 기동(D-N-D-R-P)에 들어가면 새 주차 판정 세션으로 초기화
                            if (current.isArrivedAtDestination &&
                                (current.currentGear == GearState.DRIVE || current.currentGear == GearState.NEUTRAL)
                            ) {
                                inferredArrived = false
                                nextPromptedIds = emptySet()
                                nextAckedIds = emptySet()
                            }
                        }
                        TripPhase.DRIVING
                    }
                    GearState.PARK -> {
                        val hasAlertableContext = current.hasActiveTrunkUsage ||
                            current.hasUnoccupiedRearSeatLoading ||
                            current.openWindowsCount > 0 ||
                            current.permanentTrunkNotes.isNotEmpty()
                        if (inferredArrived) {
                            nextReversedFlag = false
                            TripPhase.ARRIVED_PARKED
                        } else if (nextReversedFlag &&
                            !nextIntermediateFlag &&
                            current.phase == TripPhase.DRIVING &&
                            hasAlertableContext
                        ) {
                            inferredArrived = true
                            nextReversedFlag = false
                            appendLog(
                                "[후진(R)→주차(P) 완료] 최종 주차 전환으로 '${current.destinationName}' 도착 및 주차 상태로 판정합니다."
                            )
                            TripPhase.ARRIVED_PARKED
                        } else if (current.phase == TripPhase.DRIVING || current.phase == TripPhase.INTERMEDIATE_STOP) {
                            nextReversedFlag = false
                            TripPhase.INTERMEDIATE_STOP
                        } else {
                            nextReversedFlag = false
                            TripPhase.PRE_TRIP
                        }
                    }
                    GearState.NEUTRAL -> {
                        if (current.phase == TripPhase.DRIVING ||
                            current.currentGear == GearState.DRIVE ||
                            current.currentGear == GearState.REVERSE
                        ) {
                            TripPhase.DRIVING
                        } else {
                            current.phase
                        }
                    }
                }
                appendLog("[${signal.origin.displayName}] 기어 전환: ${signal.gear.label} -> 단계: ${nextPhase.title}")
                val updatedTimeline = if (signal.gear == GearState.DRIVE && current.currentGear != GearState.DRIVE) {
                    val step = TripTimelineStep(
                        phaseLabel = "${nextPhase.stepNumber} ${nextPhase.title}",
                        title = "주행 출발 (D단)",
                        detail = "목적지 '${current.destinationName}' 향해 이동 시작",
                        origin = signal.origin,
                        isHighlight = false,
                        timestampMs = signal.timestampMs
                    )
                    appendTimeline(current.timelineSteps, step)
                } else {
                    current.timelineSteps
                }
                val clearPromptOnMove = signal.gear == GearState.DRIVE || signal.gear == GearState.REVERSE
                if (clearPromptOnMove) {
                    claimedAutoVoiceRequestId = null
                }
                current.copy(
                    currentGear = signal.gear,
                    phase = nextPhase,
                    isArrivedAtDestination = inferredArrived,
                    hasReversedDuringDrive = nextReversedFlag,
                    hasExplicitIntermediateStop = nextIntermediateFlag,
                    promptedScenarioIdsForCurrentArrival = nextPromptedIds,
                    acknowledgedScenarioIds = nextAckedIds,
                    pendingLoadingPromptRequestId = if (clearPromptOnMove) null else current.pendingLoadingPromptRequestId,
                    autoVoiceRegistrationId = if (clearPromptOnMove) null else current.autoVoiceRegistrationId,
                    unloadConfirmationNeeded = if (clearPromptOnMove) false else current.unloadConfirmationNeeded,
                    timelineSteps = updatedTimeline
                )
            }

            is VehicleSignal.DestinationUpdated -> {
                appendLog("[${signal.origin.displayName}] 경로/목적지 변경: '${signal.destinationName}' (${signal.destinationPoi.displayName}) - 도착 판정 초기화 및 안내 예약 유지")
                val nextPhase = if (current.currentGear == GearState.DRIVE) TripPhase.DRIVING else TripPhase.PRE_TRIP
                _activePrompt.value = null
                claimedAutoVoiceRequestId = null
                val step = TripTimelineStep(
                    phaseLabel = "${nextPhase.stepNumber} ${nextPhase.title}",
                    title = "경로/목적지 갱신",
                    detail = signal.destinationName,
                    origin = signal.origin,
                    isHighlight = false,
                    timestampMs = signal.timestampMs
                )
                current.copy(
                    destinationName = signal.destinationName,
                    destinationPoi = signal.destinationPoi,
                    isArrivedAtDestination = false,
                    hasReversedDuringDrive = false,
                    hasExplicitIntermediateStop = false,
                    phase = nextPhase,
                    promptedScenarioIdsForCurrentArrival = emptySet(),
                    pendingLoadingPromptRequestId = if (current.currentGear == GearState.PARK && current.phase == TripPhase.PRE_TRIP) {
                        current.pendingLoadingPromptRequestId
                    } else {
                        null
                    },
                    autoVoiceRegistrationId = if (current.currentGear == GearState.PARK && current.phase == TripPhase.PRE_TRIP) {
                        current.autoVoiceRegistrationId
                    } else {
                        null
                    },
                    unloadConfirmationNeeded = false,
                    timelineSteps = appendTimeline(current.timelineSteps, step)
                )
            }

            is VehicleSignal.DestinationArrived -> {
                val arrived = signal.isFinalDestination
                val nextPhase = if (arrived && current.currentGear == GearState.PARK) {
                    TripPhase.ARRIVED_PARKED
                } else if (!arrived && current.currentGear == GearState.PARK) {
                    TripPhase.INTERMEDIATE_STOP
                } else {
                    current.phase
                }
                appendLog(
                    if (arrived) {
                        "[${signal.origin.displayName}] 목적지 도착 감지: '${signal.destinationName}'"
                    } else {
                        "[${signal.origin.displayName}] 중간 경유지/정차 지점 감지 (최종 목적지 미도착)"
                    }
                )
                val step = TripTimelineStep(
                    phaseLabel = "${nextPhase.stepNumber} ${nextPhase.title}",
                    title = "목적지 도착 감지",
                    detail = signal.destinationName,
                    origin = signal.origin,
                    isHighlight = true,
                    timestampMs = signal.timestampMs
                )
                current.copy(
                    destinationName = signal.destinationName,
                    isArrivedAtDestination = arrived,
                    hasExplicitIntermediateStop = !arrived,
                    hasReversedDuringDrive = if (arrived) false else current.hasReversedDuringDrive,
                    phase = nextPhase,
                    timelineSteps = appendTimeline(current.timelineSteps, step)
                )
            }

            is VehicleSignal.HarshDrivingDetected -> {
                appendLog("[${signal.origin.displayName}] 주행 거동 감지: ${signal.description} (${signal.intensityG}G)")
                current.copy(harshDrivingCount = current.harshDrivingCount + 1)
            }

            is VehicleSignal.CabinStatusUpdated -> {
                appendLog(
                    "[${signal.origin.displayName}] 실내 상태 갱신: 2열 착석=${signal.rearSeatOccupied}, 열린 창문=${signal.openWindowsCount}개, 실내온도=${signal.cabinTempCelsius}°C"
                )
                current.copy(
                    rearSeatOccupied = signal.rearSeatOccupied,
                    openWindowsCount = signal.openWindowsCount,
                    cabinTempCelsius = signal.cabinTempCelsius
                )
            }

            is VehicleSignal.AutonomousModeChanged -> {
                val updatedProfile = signal.profile ?: current.autonomousProfile
                val nextPhase = if (signal.mode == AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF) {
                    TripPhase.ARRIVED_PARKED
                } else {
                    current.phase
                }
                appendLog(
                    "[${signal.origin.displayName}] 자율주행 상태 변경: ${signal.mode.displayName} / 프로파일: ${updatedProfile.displayName}"
                )
                current.copy(
                    autonomousMode = signal.mode,
                    autonomousProfile = updatedProfile,
                    phase = nextPhase
                )
            }

            is VehicleSignal.VoiceCargoNoteReceived -> {
                val memory = aiService.analyzeCargoUtterance(
                    utterance = signal.utterance,
                    currentPoi = signal.currentLocationPoi
                )
                appendLog(
                    "[🤖 AI 음성 적재 분석] 사용자 발화: \"${signal.utterance}\" -> ${memory.aiBriefingSummary}"
                )
                val voiceInference = CargoDestinationInference.inferDestination(
                    loadedPoi = signal.currentLocationPoi,
                    currentTimeMs = signal.timestampMs,
                    profile = current.personalizedProfile,
                    explicitDestinationPoi = current.destinationPoi,
                    explicitDestinationName = current.destinationName
                )
                val extractedQty = extractQuantityFromUtterance(signal.utterance) ?: 1
                val autoBundle = settingsProvider().autoShoppingBundle &&
                    isShoppingContext(signal.utterance, current.destinationName)
                val voiceItems = memory.inferredItems.mapIndexed { idx, itemText ->
                    val cleanName = itemText.substringBefore(" (").trim()
                    val isPerishable = memory.isTemperatureSensitive ||
                        cleanName.contains("아이스크림") || cleanName.contains("신선") ||
                        cleanName.contains("냉동") || cleanName.contains("정육") || cleanName.contains("고기")
                    val isPermanent = memory.inferredPrimaryTag == CargoTag.PERMANENT_KEEP
                    CargoItemRecord(
                        id = "voice-cargo-${signal.timestampMs}-$idx",
                        name = cleanName,
                        zone = DoorZone.TRUNK,
                        categoryTag = memory.inferredPrimaryTag,
                        loadedPoi = signal.currentLocationPoi,
                        targetDestinationPoi = current.destinationPoi,
                        targetDestinationName = current.destinationName,
                        inferredDestinationPoi = voiceInference.inferredPoi,
                        destinationConfidence = voiceInference.confidenceScore,
                        isDestinationAutoInferred = true,
                        loadedTimestampMs = signal.timestampMs,
                        origin = signal.origin,
                        isPerishable = isPerishable,
                        isPermanent = isPermanent,
                        quantity = if (idx == 0) extractedQty else 1,
                        countAsBundle = autoBundle && idx == 0
                    )
                }
                // 방금 등록된 기본 센서 레코드("트렁크 적재 화물", "신선/냉동 식재료" 등)가 있다면 보다 구체적인 AI 분석 품목으로 교체/병합
                val cleanedInventory = current.cargoInventory.filterNot {
                    it.id.startsWith("trunk-sensor-") && (signal.timestampMs - it.loadedTimestampMs) in 0L..180_000L
                }
                val updatedInventory = (cleanedInventory + voiceItems).distinctBy { it.name }
                claimedAutoVoiceRequestId = null
                val step = TripTimelineStep(
                    phaseLabel = "${current.phase.stepNumber} ${current.phase.title}",
                    title = "AI 적재 메모리 분석",
                    detail = signal.utterance,
                    origin = signal.origin,
                    isHighlight = true,
                    timestampMs = signal.timestampMs
                )
                current.copy(
                    aiCargoMemory = memory,
                    cargoInventory = updatedInventory,
                    matchedRoutineTitle = voiceInference.matchedRoutine?.title ?: current.matchedRoutineTitle,
                    pendingLoadingPromptRequestId = null,
                    autoVoiceRegistrationId = null,
                    unloadConfirmationNeeded = false,
                    timelineSteps = appendTimeline(current.timelineSteps, step)
                )
            }

            is VehicleSignal.LocationContextUpdated -> {
                val nextOriginName = signal.originName.trim().ifBlank { current.originName }
                val nextDestName = signal.destinationName.trim().ifBlank { current.destinationName }
                val nextDestPoi = if (signal.destinationName.isNotBlank()) {
                    inferPoiForName(nextDestName, current.destinationPoi)
                } else {
                    current.destinationPoi
                }
                val nextOriginPoi = signal.originPoi.takeIf { it != PoiCategory.UNKNOWN }
                    ?: current.originPoi
                current.copy(
                    originName = nextOriginName,
                    originAddress = signal.originAddress.ifBlank { current.originAddress },
                    originLatitude = signal.originLatitude ?: current.originLatitude,
                    originLongitude = signal.originLongitude ?: current.originLongitude,
                    originPoi = nextOriginPoi,
                    originSourceLabel = signal.originSourceLabel.ifBlank { current.originSourceLabel },
                    destinationName = nextDestName,
                    destinationPoi = nextDestPoi,
                    destinationAddress = signal.destinationAddress.ifBlank { current.destinationAddress },
                    originSuggestions = signal.originSuggestions.ifEmpty { current.originSuggestions },
                    destinationSuggestions = signal.destinationSuggestions.ifEmpty { current.destinationSuggestions },
                    suggestedPresetKey = signal.suggestedPresetKey ?: current.suggestedPresetKey
                )
            }
        }
    }

    private fun evaluateScenarios(currentSession: TripSession, latestSignal: VehicleSignal) {
        val activePlugins = plugins
            .filter { it.id in _enabledPluginIds.value }
            .sortedBy { it.priority }

        for (plugin in activePlugins) {
            val rawAction = plugin.evaluate(currentSession, latestSignal) ?: continue
            val candidateAction = aiService.enrichActionWithAiContext(currentSession, rawAction)

            // 1. 주행 중 음성 전용 가이드 또는 자율주행 전용 제어 카드
            if (candidateAction.isDrivingSafeVoiceOnly || candidateAction.canDisplayInAutonomousDriving) {
                if ((plugin.id in currentSession.promptedScenarioIdsForCurrentArrival ||
                        plugin.id in currentSession.acknowledgedScenarioIds) &&
                    candidateAction.canDisplayInAutonomousDriving
                ) {
                    continue
                }
                val markedSession = if (candidateAction.canDisplayInAutonomousDriving) {
                    currentSession.copy(
                        promptedScenarioIdsForCurrentArrival = currentSession.promptedScenarioIdsForCurrentArrival + plugin.id
                    )
                } else {
                    currentSession
                }
                _session.value = markedSession
                persistence.save(markedSession)

                actionDispatcher.dispatchVoiceIfPresent(candidateAction)
                _activePrompt.value = candidateAction
                appendLog("[AI · 자율주행 시나리오 발동 - ${plugin.name}] ${candidateAction.headline}")
                return
            }

            // 2. 일반 하차 전 시각 안내(Pre-Exit Prompt) 공통 안전/중복 방지 가드:
            // 반드시 최종 목적지 도착 + P단 주차 상태여야 함 (중간 정차 시 생략)
            if (!currentSession.isArrivedAtDestination || currentSession.currentGear != GearState.PARK) {
                continue
            }

            // 같은 도착에서 이미 안내되었거나 확인된 시나리오면 재안내 방지 (반복 P 전환 대응)
            if (plugin.id in currentSession.promptedScenarioIdsForCurrentArrival ||
                plugin.id in currentSession.acknowledgedScenarioIds
            ) {
                appendLog("[중복 방지 가드] '${plugin.name}' 시나리오는 이번 도착에서 이미 안내되어 생략합니다.")
                continue
            }

            val step = TripTimelineStep(
                phaseLabel = "${currentSession.phase.stepNumber} ${currentSession.phase.title}",
                title = "하차 전 안내 발동 (P단)",
                detail = candidateAction.headline,
                origin = latestSignal.origin,
                isHighlight = true,
                timestampMs = latestSignal.timestampMs
            )
            val markedSession = currentSession.copy(
                promptedScenarioIdsForCurrentArrival = currentSession.promptedScenarioIdsForCurrentArrival + plugin.id,
                timelineSteps = appendTimeline(currentSession.timelineSteps, step)
            )
            _session.value = markedSession
            persistence.save(markedSession)

            _activePrompt.value = candidateAction
            actionDispatcher.dispatchVoiceIfPresent(candidateAction)
            appendLog("[하차 전 안내 발동 - ${plugin.name}] ${candidateAction.headline}")
            return
        }
    }

    /**
     * 리더 기획안 슬라이드 4: "'확인할게요'는 확인 의사로만 기록합니다"
     */
    fun acknowledgeActivePrompt() {
        val prompt = _activePrompt.value ?: return
        val current = _session.value
        val updatedProfile = recordRoutineCompletion(current)
        val updated = current.copy(
            acknowledgedScenarioIds = current.acknowledgedScenarioIds + prompt.scenarioId,
            personalizedProfile = updatedProfile
        )
        _session.value = updated
        persistence.save(updated)
        _activePrompt.value = null
        appendLog("[사용자 응답] '${prompt.primaryAckLabel}' 확인 의사로 기록 완료 (${prompt.scenarioTitle})")
    }

    /**
     * 하차 전 안내 또는 자율주행 카드에서 차량/자율주행 제어를 즉시 실행
     */
    fun executePromptControl(command: VehicleControlCommand) {
        val feedback = actionDispatcher.executeVehicleControl(command)
        appendLog(feedback)

        val current = _session.value
        val activeScenarioId = _activePrompt.value?.scenarioId ?: ""
        val updated = when (command) {
            VehicleControlCommand.OPEN_TRUNK -> {
                val updatedCargoInventory = current.cargoInventory.map { item ->
                    if (!item.isPermanent && (item.targetDestinationPoi == null || item.targetDestinationPoi == current.destinationPoi)) {
                        item.copy(isUnloaded = true)
                    } else {
                        item
                    }
                }
                val updatedProfile = recordRoutineCompletion(current)
                current.copy(
                    openDoors = current.openDoors + DoorZone.TRUNK,
                    cargoInventory = updatedCargoInventory,
                    personalizedProfile = updatedProfile,
                    acknowledgedScenarioIds = current.acknowledgedScenarioIds + activeScenarioId
                )
            }
            VehicleControlCommand.CLOSE_TRUNK -> {
                current.copy(
                    openDoors = current.openDoors - DoorZone.TRUNK,
                    pendingOpenPoiByZone = current.pendingOpenPoiByZone - DoorZone.TRUNK,
                    pendingCargoTagByZone = current.pendingCargoTagByZone - DoorZone.TRUNK
                )
            }
            VehicleControlCommand.CLOSE_ALL_WINDOWS -> {
                current.copy(
                    openWindowsCount = 0,
                    acknowledgedScenarioIds = current.acknowledgedScenarioIds + activeScenarioId
                )
            }
            VehicleControlCommand.KEEP_IN_TRUNK_PERMANENTLY -> {
                val updatedHistory = current.closureHistory.map {
                    if (it.zone == DoorZone.TRUNK) it.copy(cargoTag = CargoTag.PERMANENT_KEEP) else it
                }
                val updatedCargoInventory = current.cargoInventory.map {
                    if (it.zone == DoorZone.TRUNK) it.copy(isPermanent = true, categoryTag = CargoTag.PERMANENT_KEEP) else it
                }
                current.copy(
                    closureHistory = updatedHistory,
                    cargoInventory = updatedCargoInventory,
                    aiCargoMemory = current.aiCargoMemory?.copy(inferredPrimaryTag = CargoTag.PERMANENT_KEEP),
                    permanentTrunkNotes = current.permanentTrunkNotes + "상시 보관 등록 물품",
                    acknowledgedScenarioIds = current.acknowledgedScenarioIds + activeScenarioId
                )
            }
            VehicleControlCommand.SWITCH_AD_CARGO_COMFORT -> {
                current.copy(
                    autonomousProfile = AutonomousDrivingProfile.CARGO_COMFORT_GUARD,
                    acknowledgedScenarioIds = current.acknowledgedScenarioIds + activeScenarioId
                )
            }
            VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK -> {
                current.copy(
                    isAvpDepartureHoldActive = true,
                    openDoors = current.openDoors + DoorZone.TRUNK,
                    acknowledgedScenarioIds = current.acknowledgedScenarioIds + activeScenarioId
                )
            }
            VehicleControlCommand.ACTIVATE_CABIN_COOLING -> {
                current.copy(
                    cabinTempCelsius = 20.0f,
                    acknowledgedScenarioIds = current.acknowledgedScenarioIds + activeScenarioId
                )
            }
        }
        _session.value = updated
        persistence.save(updated)
        _activePrompt.value = null
    }

    fun dismissActivePrompt() {
        val prompt = _activePrompt.value ?: return
        val current = _session.value
        val updated = current.copy(
            acknowledgedScenarioIds = current.acknowledgedScenarioIds + prompt.scenarioId
        )
        _session.value = updated
        persistence.save(updated)
        _activePrompt.value = null
        appendLog("[안내 닫기] '${prompt.scenarioTitle}' 알림 카드를 닫았습니다.")
    }

    /**
     * 앱 프로세스 재시작 시뮬레이션: 메모리 상의 엔진 상태를 저장소(SessionPersistence)로부터 다시 읽어옵니다.
     */
    fun simulateAppRestartRecovery() {
        val restored = persistence.restore() ?: TripSession()
        _session.value = restored
        appendLog(
            "[앱 재시작 복원 검증] 저장소에서 현재 이동 상태(트렁크 사용=${restored.hasActiveTrunkUsage}, 자율주행=${restored.autonomousMode.displayName}, AI메모=${restored.aiCargoMemory != null})를 복원했습니다."
        )

        if (restored.isArrivedAtDestination &&
            restored.currentGear == GearState.PARK &&
            restored.hasActiveTrunkUsage &&
            "trunk_reminder_mvp" !in restored.promptedScenarioIdsForCurrentArrival
        ) {
            evaluateScenarios(
                restored,
                VehicleSignal.GearChanged(GearState.PARK)
            )
        }
    }

    /**
     * 상시 보관 물품(트렁크 인벤토리)을 추가 또는 제거합니다.
     */
    fun togglePermanentTrunkNote(item: String) {
        val currentNotes = _session.value.permanentTrunkNotes
        val updatedNotes = if (item in currentNotes) {
            currentNotes - item
        } else {
            currentNotes + item
        }
        val currentInventory = _session.value.cargoInventory
        val updatedInventory = if (item in updatedNotes) {
            if (currentInventory.none { it.name == item }) {
                currentInventory + CargoItemRecord(
                    id = "perm-note-${System.currentTimeMillis()}",
                    name = item,
                    zone = DoorZone.TRUNK,
                    categoryTag = CargoTag.PERMANENT_KEEP,
                    loadedPoi = _session.value.destinationPoi,
                    isPermanent = true
                )
            } else {
                currentInventory.map { if (it.name == item) it.copy(isPermanent = true) else it }
            }
        } else {
            currentInventory.filterNot { it.name == item && it.isPermanent }
        }
        val updated = _session.value.copy(
            permanentTrunkNotes = updatedNotes,
            cargoInventory = updatedInventory
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[트렁크 인벤토리] 상시 보관 물품 '$item' ${if (item in updatedNotes) "등록" else "제거"}")
    }

    /**
     * 구조화된 화물 등록 (음성 4단계 입력, Gleo 보조 등록, 수동 상세 입력 공통 경로).
     * 최근 트렁크 센서 플레이스홀더를 자동으로 업그레이드하고 로딩 대기 상태를 해제합니다.
     */
    fun registerStructuredCargo(
        name: String,
        quantity: Int = 1,
        purpose: String = "",
        originName: String? = null,
        targetDestinationName: String? = null,
        countAsBundle: Boolean = false,
        zone: DoorZone = DoorZone.TRUNK,
        origin: SignalOrigin = SignalOrigin.AI_AGENT_INFERRED,
        isPerishableOverride: Boolean? = null,
        isPermanent: Boolean = false,
        replaceRecentSensorPlaceholder: Boolean = true
    ): CargoActionResult {
        val cleanedName = name.trim()
        if (cleanedName.isBlank()) {
            return CargoActionResult(success = false, message = "적재 품목 이름이 비어 있습니다.")
        }
        val safeQuantity = quantity.coerceAtLeast(1)
        val cleanedPurpose = purpose.trim()
        val now = System.currentTimeMillis()
        val current = _session.value
        val resolvedOrigin = originName?.trim()?.takeIf { it.isNotBlank() }
            ?: current.originName.trim().takeIf { it.isNotBlank() }
        val resolvedTarget = if (isPermanent) {
            targetDestinationName?.trim()?.takeIf { it.isNotBlank() } ?: "차량 상시 보관"
        } else {
            targetDestinationName?.trim()?.takeIf { it.isNotBlank() } ?: current.destinationName
        }
        val originDerivedPoi = resolvedOrigin?.let {
            com.pleos.vehiclecontext.core.location.VehicleLocationResolver.inferPoiFromPlaceText(it)
        }?.takeIf { it != PoiCategory.UNKNOWN }
        val openPoi = current.pendingOpenPoiByZone[zone]
            ?: originDerivedPoi
            ?: current.originPoi.takeIf { it != PoiCategory.UNKNOWN }
            ?: current.closureHistory.lastOrNull { it.zone == zone }?.openedAtPoi
            ?: if (isShoppingContext(cleanedName, resolvedTarget)) PoiCategory.MART_SHOPPING else PoiCategory.HOME

        val utteranceSummary = listOfNotNull(
            cleanedName,
            if (safeQuantity > 1) "${safeQuantity}개" else null,
            cleanedPurpose.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        val memory = aiService.analyzeCargoUtterance(
            utterance = utteranceSummary,
            currentPoi = openPoi
        )
        val inferredPoi = inferPoiForName(resolvedTarget, current.destinationPoi)
        val inferenceResult = CargoDestinationInference.inferDestination(
            loadedPoi = openPoi,
            currentTimeMs = now,
            profile = current.personalizedProfile,
            explicitDestinationPoi = inferredPoi,
            explicitDestinationName = resolvedTarget
        )
        val effectiveTag = when {
            isPermanent -> CargoTag.PERMANENT_KEEP
            isPerishableOverride == true -> CargoTag.FRESH_FOOD
            else -> memory.inferredPrimaryTag
        }
        val isPerishable = isPerishableOverride ?: (
            memory.isTemperatureSensitive ||
                effectiveTag == CargoTag.FRESH_FOOD ||
                cleanedName.contains("아이스크림") ||
                cleanedName.contains("신선") ||
                cleanedName.contains("냉동") ||
                cleanedName.contains("케이크") ||
                cleanedName.contains("정육") ||
                cleanedName.contains("생선") ||
                cleanedName.contains("고기")
            )
        val effectivePermanent = isPermanent || effectiveTag == CargoTag.PERMANENT_KEEP
        val bundleFlag = countAsBundle || (
            settingsProvider().autoShoppingBundle && isShoppingContext(cleanedName, resolvedTarget)
            )
        val itemId = "cargo-$now-${current.cargoInventory.size + 1}"
        val record = CargoItemRecord(
            id = itemId,
            name = cleanedName,
            zone = zone,
            categoryTag = effectiveTag,
            loadedPoi = openPoi,
            originName = resolvedOrigin,
            targetDestinationPoi = inferredPoi,
            targetDestinationName = resolvedTarget,
            inferredDestinationPoi = inferenceResult.inferredPoi,
            destinationConfidence = inferenceResult.confidenceScore,
            isDestinationAutoInferred = targetDestinationName.isNullOrBlank(),
            loadedTimestampMs = now,
            origin = origin,
            isPerishable = isPerishable,
            isPermanent = effectivePermanent,
            quantity = safeQuantity,
            unloadedQuantity = 0,
            purpose = cleanedPurpose,
            countAsBundle = bundleFlag
        )
        val pendingLoadTs = current.pendingLoadingPromptRequestId
            ?.removePrefix("load-")
            ?.toLongOrNull()
        val baseInventory = if (replaceRecentSensorPlaceholder) {
            current.cargoInventory.filterNot {
                it.id.startsWith("trunk-sensor-") && (
                    (now - it.loadedTimestampMs) in 0L..180_000L ||
                        (pendingLoadTs != null && it.loadedTimestampMs == pendingLoadTs)
                    )
            }
        } else {
            current.cargoInventory
        }
        val updatedInventory = (baseInventory + record).distinctBy { "${it.name}:${it.targetDestinationName ?: ""}" }
        val updatedNotes = if (effectivePermanent) {
            current.permanentTrunkNotes + cleanedName
        } else {
            current.permanentTrunkNotes
        }
        val step = TripTimelineStep(
            phaseLabel = "${current.phase.stepNumber} ${current.phase.title}",
            title = "구조화 적재 등록",
            detail = "${record.name} (${record.amountLabel()}) -> $resolvedTarget",
            origin = origin,
            isHighlight = true,
            timestampMs = now
        )
        claimedAutoVoiceRequestId = null
        val updated = current.copy(
            aiCargoMemory = memory,
            cargoInventory = updatedInventory,
            permanentTrunkNotes = updatedNotes,
            matchedRoutineTitle = inferenceResult.matchedRoutine?.title ?: current.matchedRoutineTitle,
            pendingLoadingPromptRequestId = null,
            autoVoiceRegistrationId = null,
            unloadConfirmationNeeded = false,
            timelineSteps = appendTimeline(current.timelineSteps, step)
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[구조화 적재] ${record.name} (${record.amountLabel()}) · 목적지 '$resolvedTarget' 등록")
        return CargoActionResult(
            success = true,
            message = "${record.name} ${record.amountLabel()}을(를) '${resolvedTarget}' 보관 짐으로 등록했습니다.",
            itemId = itemId
        )
    }

    /**
     * 목적지 또는 품목명 기준으로 하차/수령 확인 처리합니다 (부분 수령 수량 지원).
     */
    fun confirmUnloadCargo(targetName: String? = null, quantity: Int? = null): CargoActionResult {
        val current = _session.value
        val activeNonPermanent = current.cargoInventory.filter {
            !it.isUnloaded && !it.isPermanent && it.remainingQuantity > 0
        }
        if (activeNonPermanent.isEmpty()) {
            val updated = current.copy(unloadConfirmationNeeded = false)
            _session.value = updated
            persistence.save(updated)
            return CargoActionResult(success = false, message = "하차 확인할 트렁크 보관 물품이 없습니다.")
        }

        val resolvedTarget = targetName?.trim()?.takeIf { it.isNotBlank() } ?: current.destinationName
        val matchingIds = activeNonPermanent
            .filter { samePlaceName(it.targetDestinationName, resolvedTarget) }
            .ifEmpty {
                if (targetName.isNullOrBlank()) activeNonPermanent else emptyList()
            }
            .map { it.id }
            .toSet()

        if (matchingIds.isEmpty()) {
            return CargoActionResult(
                success = false,
                message = "'$resolvedTarget'에 해당하는 트렁크 적재 물품을 찾지 못했습니다."
            )
        }

        return applyUnloadByIds(
            current = current,
            targetIds = matchingIds,
            quantity = quantity,
            contextLabel = resolvedTarget
        )
    }

    /**
     * UI 목록에서 특정 물품 ID를 선택해 하차 완료 처리합니다.
     */
    fun unloadCargoItem(itemId: String, quantity: Int? = null): CargoActionResult {
        val current = _session.value
        val target = current.cargoInventory.firstOrNull {
            it.id == itemId && !it.isUnloaded && it.remainingQuantity > 0
        } ?: return CargoActionResult(success = false, message = "이미 하차 완료되었거나 찾을 수 없는 물품입니다.")
        return applyUnloadByIds(
            current = current,
            targetIds = setOf(target.id),
            quantity = quantity,
            contextLabel = target.name
        )
    }

    private fun applyUnloadByIds(
        current: TripSession,
        targetIds: Set<String>,
        quantity: Int?,
        contextLabel: String
    ): CargoActionResult {
        val now = System.currentTimeMillis()
        var affectedCount = 0
        var lastAffectedId: String? = null
        val updatedInventory = current.cargoInventory.map { item ->
            if (item.id !in targetIds) {
                item
            } else {
                affectedCount += 1
                lastAffectedId = item.id
                val delta = quantity?.coerceAtLeast(1) ?: item.remainingQuantity
                val nextUnloadedQty = (item.unloadedQuantity + delta).coerceAtMost(item.quantity)
                val completed = nextUnloadedQty >= item.quantity
                item.copy(
                    unloadedQuantity = nextUnloadedQty,
                    isUnloaded = completed
                )
            }
        }
        val remainingForDestination = updatedInventory.any {
            !it.isUnloaded && !it.isPermanent && it.remainingQuantity > 0 &&
                samePlaceName(it.targetDestinationName, current.destinationName)
        }
        val step = TripTimelineStep(
            phaseLabel = "${current.phase.stepNumber} ${current.phase.title}",
            title = "하차/수령 확인 완료",
            detail = "$contextLabel (${affectedCount}건 처리)",
            origin = SignalOrigin.AI_AGENT_INFERRED,
            isHighlight = true,
            timestampMs = now
        )
        val updated = current.copy(
            cargoInventory = updatedInventory,
            unloadConfirmationNeeded = remainingForDestination && current.unloadConfirmationNeeded,
            timelineSteps = appendTimeline(current.timelineSteps, step)
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[하차 확인] '$contextLabel' 기준 ${affectedCount}건 하차 처리 완료")
        return CargoActionResult(
            success = true,
            message = "'$contextLabel' 기준 ${affectedCount}건의 하차 확인을 마쳤습니다.",
            itemId = lastAffectedId
        )
    }

    /**
     * 기존 트렁크 물품의 목적지/사용 목적/수량/상시보관 여부를 수정합니다 (Gleo UPDATE_STORAGE 지원).
     */
    fun updateCargoStorage(
        itemId: String? = null,
        cargoName: String? = null,
        targetPlace: String? = null,
        purpose: String? = null,
        quantity: Int? = null,
        keepInCar: Boolean? = null
    ): CargoActionResult {
        val current = _session.value
        val activeItems = current.cargoInventory.filter { !it.isUnloaded && it.remainingQuantity > 0 }
        val targetItem = when {
            !itemId.isNullOrBlank() -> activeItems.firstOrNull { it.id == itemId }
            !cargoName.isNullOrBlank() -> activeItems.lastOrNull {
                it.name.contains(cargoName.trim(), ignoreCase = true) ||
                    cargoName.trim().contains(it.name, ignoreCase = true)
            }
            else -> activeItems.lastOrNull()
        } ?: return CargoActionResult(success = false, message = "수정할 적재 물품을 찾지 못했습니다.")

        val newPermanent = keepInCar ?: targetItem.isPermanent
        val newTargetName = when {
            newPermanent -> null
            !targetPlace.isNullOrBlank() -> targetPlace.trim()
            else -> targetItem.targetDestinationName
        }
        val newPurpose = purpose?.trim()?.takeIf { it.isNotBlank() } ?: targetItem.purpose
        val newQuantity = quantity?.coerceAtLeast(1) ?: targetItem.quantity
        val updatedInventory = current.cargoInventory.map { item ->
            if (item.id == targetItem.id) {
                item.copy(
                    targetDestinationName = newTargetName,
                    targetDestinationPoi = if (!newTargetName.isNullOrBlank()) {
                        inferPoiForName(newTargetName, item.targetDestinationPoi ?: PoiCategory.HOME)
                    } else {
                        item.targetDestinationPoi
                    },
                    purpose = newPurpose,
                    quantity = newQuantity,
                    isPermanent = newPermanent,
                    categoryTag = if (newPermanent) CargoTag.PERMANENT_KEEP else item.categoryTag,
                    isUnloaded = item.unloadedQuantity >= newQuantity
                )
            } else {
                item
            }
        }
        val updatedNotes = if (newPermanent) {
            current.permanentTrunkNotes + targetItem.name
        } else {
            current.permanentTrunkNotes - targetItem.name
        }
        val updated = current.copy(
            cargoInventory = updatedInventory,
            permanentTrunkNotes = updatedNotes
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[적재 수정] '${targetItem.name}' 정보 갱신 (목적지=${newTargetName ?: if (newPermanent) "상시보관" else "미정"}, 수량=${newQuantity})")
        return CargoActionResult(
            success = true,
            message = "'${targetItem.name}' 보관 정보를 수정했습니다.",
            itemId = targetItem.id
        )
    }

    /**
     * 사용자가 보관함 목록에서 특정 화물의 모든 상세 요소를 직접 편집/변경합니다.
     */
    fun editCargoItem(
        itemId: String,
        name: String,
        quantity: Int = 1,
        purpose: String = "",
        originName: String? = null,
        targetDestinationName: String? = null,
        countAsBundle: Boolean = false,
        isPerishableOverride: Boolean? = null,
        isPermanent: Boolean = false
    ): CargoActionResult {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return CargoActionResult(success = false, message = "항목 이름을 입력해 주세요.")
        }
        val current = _session.value
        val now = System.currentTimeMillis()
        val safeQty = quantity.coerceAtLeast(1)
        val cleanedPurpose = purpose.trim()
        val cleanedOrigin = originName?.trim()?.takeIf { it.isNotBlank() }
        val resolvedTarget = if (isPermanent) {
            null
        } else {
            targetDestinationName?.trim()?.takeIf { it.isNotBlank() }
        }
        val targetPoi = resolvedTarget?.let { inferPoiForName(it, current.destinationPoi) }
        val existingRecord = current.cargoInventory.firstOrNull { it.id == itemId }

        val memory = aiService.analyzeCargoUtterance(
            utterance = listOfNotNull(trimmedName, cleanedPurpose.ifBlank { null }, cleanedOrigin, resolvedTarget).joinToString(" "),
            currentPoi = existingRecord?.loadedPoi ?: current.originPoi
        )
        val effectivePerishable = isPerishableOverride ?: existingRecord?.isPerishable ?: (
            memory.isTemperatureSensitive ||
                trimmedName.contains("아이스크림") ||
                trimmedName.contains("신선") ||
                trimmedName.contains("냉동") ||
                trimmedName.contains("케이크") ||
                trimmedName.contains("정육") ||
                trimmedName.contains("생선") ||
                trimmedName.contains("고기")
            )
        val effectiveTag = when {
            isPermanent -> CargoTag.PERMANENT_KEEP
            effectivePerishable -> CargoTag.FRESH_FOOD
            else -> memory.inferredPrimaryTag
        }

        val updatedRecord: CargoItemRecord
        val baseInventory: List<CargoItemRecord>
        var nextNotes = current.permanentTrunkNotes
        var nextAiMemory = current.aiCargoMemory
        var nextClosureHistory = current.closureHistory

        if (existingRecord != null) {
            updatedRecord = existingRecord.copy(
                name = trimmedName,
                quantity = safeQty,
                purpose = cleanedPurpose,
                originName = cleanedOrigin,
                targetDestinationName = resolvedTarget,
                targetDestinationPoi = targetPoi ?: existingRecord.targetDestinationPoi,
                countAsBundle = countAsBundle,
                isPerishable = effectivePerishable,
                isPermanent = isPermanent,
                categoryTag = effectiveTag,
                isUnloaded = existingRecord.unloadedQuantity >= safeQty
            )
            baseInventory = current.cargoInventory.map { if (it.id == itemId) updatedRecord else it }
            if (existingRecord.name != trimmedName || !isPermanent) {
                nextNotes = nextNotes - existingRecord.name
            }
            if (isPermanent) {
                nextNotes = nextNotes + trimmedName
            }
        } else {
            val newId = "cargo-$now-${current.cargoInventory.size + 1}"
            updatedRecord = CargoItemRecord(
                id = newId,
                name = trimmedName,
                zone = DoorZone.TRUNK,
                categoryTag = effectiveTag,
                loadedPoi = current.originPoi,
                originName = cleanedOrigin,
                targetDestinationPoi = targetPoi,
                targetDestinationName = resolvedTarget,
                inferredDestinationPoi = targetPoi ?: current.destinationPoi,
                destinationConfidence = 1.0f,
                isDestinationAutoInferred = false,
                loadedTimestampMs = now,
                origin = SignalOrigin.AI_AGENT_INFERRED,
                isPerishable = effectivePerishable,
                isPermanent = isPermanent,
                quantity = safeQty,
                unloadedQuantity = 0,
                purpose = cleanedPurpose,
                countAsBundle = countAsBundle
            )
            baseInventory = current.cargoInventory + updatedRecord
            when {
                itemId.startsWith("perm-") -> {
                    val oldNote = itemId.removePrefix("perm-")
                    nextNotes = nextNotes - oldNote
                    if (isPermanent) nextNotes = nextNotes + trimmedName
                }
                itemId.startsWith("ai-item-") -> {
                    val idx = itemId.removePrefix("ai-item-").toIntOrNull()
                    if (idx != null && nextAiMemory != null) {
                        val remaining = nextAiMemory.inferredItems.filterIndexed { i, _ -> i != idx }
                        nextAiMemory = if (remaining.isEmpty()) null else nextAiMemory.copy(inferredItems = remaining)
                    }
                    if (isPermanent) nextNotes = nextNotes + trimmedName
                }
                itemId.startsWith("trunk-") -> {
                    val idx = itemId.removePrefix("trunk-").substringAfterLast("-").toIntOrNull()
                    val trunkIndices = current.closureHistory.mapIndexedNotNull { i, rec ->
                        if (rec.zone == DoorZone.TRUNK && rec.cargoTag != CargoTag.PERMANENT_KEEP) i else null
                    }
                    val targetClosureIdx = idx?.let { trunkIndices.getOrNull(it) }
                    if (targetClosureIdx != null) {
                        nextClosureHistory = current.closureHistory.filterIndexed { i, _ -> i != targetClosureIdx }
                    }
                    if (isPermanent) nextNotes = nextNotes + trimmedName
                }
                else -> if (isPermanent) nextNotes = nextNotes + trimmedName
            }
        }

        val step = TripTimelineStep(
            phaseLabel = "${current.phase.stepNumber} ${current.phase.title}",
            title = "적재 정보 편집",
            detail = "${updatedRecord.name} (${updatedRecord.amountLabel()}) · ${resolvedTarget ?: if (isPermanent) "상시 보관" else "목적지 미정"}",
            origin = SignalOrigin.AI_AGENT_INFERRED,
            isHighlight = false,
            timestampMs = now
        )
        val updated = current.copy(
            cargoInventory = baseInventory,
            permanentTrunkNotes = nextNotes,
            aiCargoMemory = nextAiMemory,
            closureHistory = nextClosureHistory,
            timelineSteps = appendTimeline(current.timelineSteps, step)
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[적재 편집] '${updatedRecord.name}' 정보 수정 완료 (수량=${safeQty}, 목적지=${resolvedTarget ?: if (isPermanent) "상시 보관" else "미정"})")
        return CargoActionResult(
            success = true,
            message = "'${updatedRecord.name}' 정보를 수정했습니다.",
            itemId = updatedRecord.id
        )
    }

    /**
     * 사용자가 보관함 목록에서 개별 항목을 삭제합니다.
     */
    fun removeCargoItem(itemId: String): CargoActionResult {
        val current = _session.value
        val now = System.currentTimeMillis()
        var removedLabel: String? = null
        var updatedInventory = current.cargoInventory
        var updatedNotes = current.permanentTrunkNotes
        var updatedAiMemory = current.aiCargoMemory
        var updatedClosures = current.closureHistory

        val invTarget = current.cargoInventory.firstOrNull { it.id == itemId }
        when {
            invTarget != null -> {
                removedLabel = invTarget.name
                updatedInventory = current.cargoInventory.filterNot { it.id == itemId }
                if (invTarget.isPermanent && updatedInventory.none { it.isPermanent && it.name == invTarget.name }) {
                    updatedNotes = updatedNotes - invTarget.name
                }
                if (updatedInventory.none { !it.isUnloaded && !it.isPermanent && it.remainingQuantity > 0 }) {
                    updatedAiMemory = null
                    updatedClosures = updatedClosures.filterNot {
                        it.zone == DoorZone.TRUNK && it.cargoTag != CargoTag.PERMANENT_KEEP
                    }
                }
            }
            itemId.startsWith("perm-") -> {
                val note = itemId.removePrefix("perm-")
                removedLabel = note
                updatedNotes = updatedNotes - note
                updatedInventory = current.cargoInventory.filterNot { it.isPermanent && it.name == note }
            }
            itemId.startsWith("ai-item-") -> {
                val idx = itemId.removePrefix("ai-item-").toIntOrNull()
                if (idx != null && updatedAiMemory != null && idx in updatedAiMemory.inferredItems.indices) {
                    removedLabel = updatedAiMemory.inferredItems[idx].substringBefore(" (").trim()
                    val remaining = updatedAiMemory.inferredItems.filterIndexed { i, _ -> i != idx }
                    updatedAiMemory = if (remaining.isEmpty()) null else updatedAiMemory.copy(inferredItems = remaining)
                    if (remaining.isEmpty() && updatedInventory.none { !it.isUnloaded && !it.isPermanent }) {
                        updatedClosures = updatedClosures.filterNot {
                            it.zone == DoorZone.TRUNK && it.cargoTag != CargoTag.PERMANENT_KEEP
                        }
                    }
                }
            }
            itemId.startsWith("trunk-") -> {
                val idx = itemId.removePrefix("trunk-").substringAfterLast("-").toIntOrNull()
                val trunkIndices = current.closureHistory.mapIndexedNotNull { i, rec ->
                    if (rec.zone == DoorZone.TRUNK && rec.cargoTag != CargoTag.PERMANENT_KEEP) i else null
                }
                val targetClosureIdx = idx?.let { trunkIndices.getOrNull(it) }
                if (targetClosureIdx != null) {
                    removedLabel = "트렁크 적재 항목"
                    updatedClosures = current.closureHistory.filterIndexed { i, _ -> i != targetClosureIdx }
                }
            }
        }

        if (removedLabel == null) {
            return CargoActionResult(success = false, message = "삭제할 항목을 찾지 못했습니다.")
        }

        val remainingForDest = updatedInventory.any {
            !it.isUnloaded && !it.isPermanent && it.remainingQuantity > 0 &&
                samePlaceName(it.targetDestinationName, current.destinationName)
        }
        val step = TripTimelineStep(
            phaseLabel = "${current.phase.stepNumber} ${current.phase.title}",
            title = "적재 항목 삭제",
            detail = "$removedLabel 삭제 완료",
            origin = SignalOrigin.AI_AGENT_INFERRED,
            isHighlight = false,
            timestampMs = now
        )
        val updated = current.copy(
            cargoInventory = updatedInventory,
            permanentTrunkNotes = updatedNotes,
            aiCargoMemory = updatedAiMemory,
            closureHistory = updatedClosures,
            unloadConfirmationNeeded = remainingForDest && current.unloadConfirmationNeeded,
            timelineSteps = appendTimeline(current.timelineSteps, step)
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[적재 삭제] '$removedLabel' 항목을 보관함에서 삭제했습니다.")
        return CargoActionResult(success = true, message = "'$removedLabel' 항목을 삭제했습니다.", itemId = itemId)
    }

    /**
     * 백그라운드/외부 적재 감지 팝업에서 '품목 직접 추가' 선택 시 앱 내부에서 감지 알림이 중복 팝업되지 않도록
     * 수동 입력 폼 요청으로 승격합니다.
     */
    fun promoteLoadingAlertToManualForm() {
        val current = _session.value
        val pending = current.pendingLoadingPromptRequestId ?: return
        if (pending.startsWith("load-")) {
            claimedAutoVoiceRequestId = null
            val updated = current.copy(
                pendingLoadingPromptRequestId = "manual-${System.currentTimeMillis()}",
                autoVoiceRegistrationId = null
            )
            _session.value = updated
            persistence.save(updated)
        }
    }

    fun requestManualCargoRegistration() {
        val current = _session.value
        val reqId = "manual-${System.currentTimeMillis()}"
        val updated = current.copy(pendingLoadingPromptRequestId = reqId)
        _session.value = updated
        persistence.save(updated)
    }

    fun skipLoadingRegistration() {
        val current = _session.value
        claimedAutoVoiceRequestId = null
        val updated = current.copy(
            pendingLoadingPromptRequestId = null,
            autoVoiceRegistrationId = null,
            unloadConfirmationNeeded = false
        )
        _session.value = updated
        persistence.save(updated)
    }

    fun claimAutoVoiceRegistration(requestId: String): Boolean {
        val current = _session.value
        if (current.autoVoiceRegistrationId != requestId) return false
        if (claimedAutoVoiceRequestId == requestId) return false
        claimedAutoVoiceRequestId = requestId
        return true
    }

    fun releaseAutoVoiceRegistration(requestId: String) {
        val current = _session.value
        if (claimedAutoVoiceRequestId == requestId) {
            claimedAutoVoiceRequestId = null
        }
        if (current.autoVoiceRegistrationId == requestId) {
            val updated = current.copy(autoVoiceRegistrationId = null)
            _session.value = updated
            persistence.save(updated)
        }
    }

    private fun inferPoiForName(placeName: String, fallback: PoiCategory): PoiCategory {
        val normalized = placeName.lowercase()
        return when {
            normalized.contains("집") || normalized.contains("자택") || normalized.contains("아파트") || normalized.contains("본가") -> PoiCategory.HOME
            normalized.contains("회사") || normalized.contains("오피스") || normalized.contains("사무실") -> PoiCategory.OFFICE
            normalized.contains("마트") || normalized.contains("백화점") || normalized.contains("코스트코") || normalized.contains("이마트") || normalized.contains("시장") -> PoiCategory.MART_SHOPPING
            normalized.contains("캠핑") || normalized.contains("골프") || normalized.contains("등산") -> PoiCategory.LEISURE_CAMPING
            normalized.contains("공항") || normalized.contains("터미널") || normalized.contains("역") -> PoiCategory.AIRPORT_STATION
            else -> fallback
        }
    }

    /**
     * 트렁크 적재 화물 및 인벤토리를 전체 비웁니다 (주행 단계, 기어, 목적지 및 타임라인은 유지).
     */
    fun clearTrunkCargo() {
        val current = _session.value
        claimedAutoVoiceRequestId = null
        val updated = current.copy(
            closureHistory = current.closureHistory.filter { it.zone != DoorZone.TRUNK },
            cargoInventory = emptyList(),
            aiCargoMemory = null,
            openDoors = current.openDoors - DoorZone.TRUNK,
            permanentTrunkNotes = emptySet(),
            pendingLoadingPromptRequestId = null,
            autoVoiceRegistrationId = null,
            unloadConfirmationNeeded = false
        )
        _session.value = updated
        persistence.save(updated)
        appendLog("[트렁크 비우기] 트렁크 적재 물품 및 인벤토리를 모두 비웠습니다.")
    }

    /**
     * 새로운 주행 세션을 위해 초기화 (타임라인 포함 초기화)
     */
    fun resetTripSession(destinationName: String = "집 (성수동 자택)") {
        claimedAutoVoiceRequestId = null
        val fresh = TripSession(destinationName = destinationName)
        _session.value = fresh
        _activePrompt.value = null
        persistence.clear()
        persistence.save(fresh)
        appendLog("[새 이동 시작] 센서 이력·자율주행 상태·AI 메모리·타임라인을 초기화했습니다. (목적지: $destinationName)")
    }

    private fun recordRoutineCompletion(current: TripSession): PersonalizedCargoProfile {
        val lastClosure = current.latestTrunkUsage ?: return current.personalizedProfile
        val originPoi = lastClosure.openedAtPoi
        val destPoi = current.destinationPoi
        val updatedRoutines = current.personalizedProfile.routines.map { routine ->
            if (routine.originPoi == originPoi && routine.destinationPoi == destPoi) {
                routine.copy(
                    occurrenceCount = routine.occurrenceCount + 1,
                    confidenceScore = (routine.confidenceScore + 0.01f).coerceAtMost(0.99f),
                    lastObservedTimestampMs = System.currentTimeMillis()
                )
            } else {
                routine
            }
        }
        return current.personalizedProfile.copy(
            routines = updatedRoutines,
            lastUpdatedTimestampMs = System.currentTimeMillis()
        )
    }

    private fun appendTimeline(
        currentSteps: List<TripTimelineStep>,
        newStep: TripTimelineStep
    ): List<TripTimelineStep> {
        return (currentSteps + newStep).takeLast(15)
    }

    private fun appendLog(entry: String) {
        val currentList = _eventLog.value
        _eventLog.value = (listOf(entry) + currentList).take(35)
    }
}
