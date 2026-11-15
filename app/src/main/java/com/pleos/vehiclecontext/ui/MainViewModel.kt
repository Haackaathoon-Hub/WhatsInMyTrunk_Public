package com.pleos.vehiclecontext.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.engine.CargoActionResult
import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.settings.AppSettingsStore
import com.pleos.vehiclecontext.core.settings.InMemoryAppSettingsStore
import com.pleos.vehiclecontext.core.signal.BridgeStatus
import com.pleos.vehiclecontext.core.signal.CapabilityReport
import com.pleos.vehiclecontext.core.signal.MockSignalProvider
import com.pleos.vehiclecontext.core.signal.PleosSignalProvider
import com.pleos.vehiclecontext.core.voice.CargoVoiceCoordinator
import com.pleos.vehiclecontext.core.voice.VoiceInputUiState
import com.pleos.vehiclecontext.gleo.GleoCargoActions
import com.pleos.vehiclecontext.gleo.GleoDraft
import com.pleos.vehiclecontext.ui.model.CargoItemPresentation
import com.pleos.vehiclecontext.ui.model.FreshnessCareStatus
import com.pleos.vehiclecontext.ui.model.FreshnessRiskLevel
import com.pleos.vehiclecontext.ui.model.UserScreenDataMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val pleosSignalProvider: PleosSignalProvider,
    private val mockSignalProvider: MockSignalProvider,
    private val engine: TripContextEngine,
    val actionDispatcher: ActionDispatcher,
    private val settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    private val voiceCoordinator: CargoVoiceCoordinator? = null,
    private val gleoActions: GleoCargoActions = GleoCargoActions(
        engine = engine,
        settingsProvider = { settingsStore.settings.value }
    ),
    private val hasRuntimeDemo: Boolean = false
) : ViewModel() {

    val session: StateFlow<TripSession> = engine.session
    val activePrompt: StateFlow<ContextAction?> = engine.activePrompt
    val enabledPluginIds: StateFlow<Set<String>> = engine.enabledPluginIds
    val eventLog: StateFlow<List<String>> = engine.eventLog

    val appSettings: StateFlow<AppSettings> = settingsStore.settings

    private val _isArrivalAlertEnabled = MutableStateFlow(settingsStore.settings.value.arrivalAlertEnabled)
    val isArrivalAlertEnabled: StateFlow<Boolean> = _isArrivalAlertEnabled.asStateFlow()

    val voiceUiState: StateFlow<VoiceInputUiState> =
        voiceCoordinator?.uiState ?: MutableStateFlow(VoiceInputUiState()).asStateFlow()

    val gleoDraft: StateFlow<GleoDraft?> = gleoActions.pending

    val bridgeStatus: StateFlow<BridgeStatus> = pleosSignalProvider.diagnostics.status

    val emulatorDemoAvailable: Boolean = hasRuntimeDemo && VehicleContextRuntime.arrivalDemo.available

    val emulatorDemoArmed: StateFlow<Boolean> =
        if (hasRuntimeDemo) VehicleContextRuntime.arrivalDemo.armed else MutableStateFlow(false).asStateFlow()

    val emulatorDemoStatus: StateFlow<String> =
        if (hasRuntimeDemo) VehicleContextRuntime.arrivalDemo.status else MutableStateFlow("시연 기능 비활성").asStateFlow()

    private val _trunkCargoItems = MutableStateFlow<List<CargoItemPresentation>>(
        UserScreenDataMapper.toCargoItemList(engine.session.value)
    )
    val trunkCargoItems: StateFlow<List<CargoItemPresentation>> = _trunkCargoItems.asStateFlow()

    private val _freshnessStatus = MutableStateFlow<FreshnessCareStatus>(
        UserScreenDataMapper.toFreshnessCareStatus(engine.session.value)
    )
    val freshnessStatus: StateFlow<FreshnessCareStatus> = _freshnessStatus.asStateFlow()

    init {
        updateDerivedState(session.value)
        try {
            viewModelScope.launch {
                session.collect { updatedSession ->
                    updateDerivedState(updatedSession)
                }
            }
        } catch (_: Throwable) {
            CoroutineScope(Dispatchers.Unconfined).launch {
                session.collect { updatedSession ->
                    updateDerivedState(updatedSession)
                }
            }
        }
    }

    private fun updateDerivedState(currentSession: TripSession) {
        _trunkCargoItems.value = UserScreenDataMapper.toCargoItemList(currentSession)
        _freshnessStatus.value = UserScreenDataMapper.toFreshnessCareStatus(currentSession)
    }

    private val _replayProgress = MutableStateFlow(0)
    val replayProgress: StateFlow<Int> = _replayProgress.asStateFlow()

    private val _replayStageTitle = MutableStateFlow<String?>(null)
    val replayStageTitle: StateFlow<String?> = _replayStageTitle.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    val capabilities: List<CapabilityReport> =
        pleosSignalProvider.checkCapabilities() + mockSignalProvider.checkCapabilities()

    val registeredPlugins: List<ScenarioPlugin> = engine.getRegisteredPlugins()

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        engine.setPluginEnabled(pluginId, enabled)
    }

    fun acknowledgePrompt() {
        engine.acknowledgeActivePrompt()
    }

    fun executeControl(command: VehicleControlCommand) {
        engine.executePromptControl(command)
    }

    fun dismissPrompt() {
        engine.dismissActivePrompt()
    }

    // =========================================================================
    // 리더 기획안 슬라이드 7: "첫 시연에서 확인할 6가지 핵심 동작" 원클릭 실행
    // =========================================================================

    fun runDemo1TrunkUsedHomeArrival(origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR) {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD,
            origin = origin
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, origin))
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = "집 (성수동 자택)",
                isFinalDestination = true,
                origin = origin
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, origin))
    }

    fun runDemo2NoTrunkUsageHomeArrival(origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR) {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, origin))
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = "집 (성수동 자택)",
                isFinalDestination = true,
                origin = origin
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, origin))
    }

    fun runDemo3IntermediateStop(origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR) {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD,
            origin = origin
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, origin))
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = "경유지 (잠시 정차)",
                isFinalDestination = false,
                origin = origin
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, origin))
    }

    fun runDemo4RepeatedParkTransition(origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR) {
        if (!session.value.isArrivedAtDestination || !session.value.hasActiveTrunkUsage) {
            runDemo1TrunkUsedHomeArrival(origin)
        }
        engine.dismissActivePrompt()
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, origin))
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, origin))
    }

    fun runDemo5RouteChanged(origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR) {
        if (!session.value.hasActiveTrunkUsage) {
            engine.resetTripSession("집 (성수동 자택)")
            mockSignalProvider.simulateTrunkOpenAndClose(
                poi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD,
                origin = origin
            )
        }
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, origin))
        mockSignalProvider.emit(
            VehicleSignal.DestinationUpdated(
                destinationName = "본가 (분당 부모님댁)",
                destinationPoi = PoiCategory.HOME,
                origin = origin
            )
        )
    }

    fun runDemo6AppRestartRecovery() {
        engine.simulateAppRestartRecovery()
    }

    // =========================================================================
    // ★ 자율주행(AD/AVP) × AI 컨텍스트 에이전트 킬러 시나리오 시연 액션
    // =========================================================================

    fun runAiVoiceCargoTaggingDemo(
        utterance: String = "이마트에서 아이스크림이랑 생일 케이크, 와인 두 병 샀어"
    ) {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.GENERAL
        )
        mockSignalProvider.emit(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = utterance,
                currentLocationPoi = PoiCategory.MART_SHOPPING
            )
        )
    }

    fun runAutonomousLv3CargoComfortDemo() {
        if (!session.value.hasActiveTrunkUsage) {
            runAiVoiceCargoTaggingDemo("생일 케이크랑 와인 두 병 트렁크에 실었어")
        }
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE))
        mockSignalProvider.emit(
            VehicleSignal.AutonomousModeChanged(
                mode = AutonomousDrivingMode.HIGHWAY_PILOT_L3,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
    }

    fun runAutonomousValetDropoffGuardDemo() {
        if (!session.value.hasActiveTrunkUsage) {
            runAiVoiceCargoTaggingDemo("마트에서 아이스크림이랑 장바구니 실었어")
        }
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK))
        mockSignalProvider.emit(
            VehicleSignal.AutonomousModeChanged(
                mode = AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
    }

    // =========================================================================
    // 개별 센서 제어 및 공간/주행 확장 시나리오 시연 액션
    // =========================================================================

    fun triggerManualTrunkOpenClose(poi: PoiCategory = PoiCategory.MART_SHOPPING, tag: CargoTag = CargoTag.FRESH_FOOD) {
        mockSignalProvider.simulateTrunkOpenAndClose(poi, tag)
    }

    fun triggerManualGear(gear: GearState) {
        mockSignalProvider.emit(VehicleSignal.GearChanged(gear, SignalOrigin.MOCK_SIMULATOR))
    }

    fun triggerManualArrival() {
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = session.value.destinationName,
                isFinalDestination = true,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
    }

    fun runExtensionRearSeatDemo() {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.simulateRearDoorLoadItem(PoiCategory.OFFICE)
        mockSignalProvider.emit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR))
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = "집 (성수동 자택)",
                isFinalDestination = true,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.MOCK_SIMULATOR))
    }

    fun runExtensionHarshDrivingDemo() {
        if (!session.value.hasActiveTrunkUsage) {
            engine.resetTripSession("집 (성수동 자택)")
            mockSignalProvider.simulateTrunkOpenAndClose(
                poi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRAGILE
            )
        }
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR))
        mockSignalProvider.emit(
            VehicleSignal.HarshDrivingDetected(
                description = "급감속 감지",
                intensityG = 0.52f,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
    }

    fun runExtensionOpenWindowExitDemo() {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.emit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 2,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR))
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = "집 (성수동 자택)",
                isFinalDestination = true,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.MOCK_SIMULATOR))
    }

    fun runExtensionReverseReminderDemo() {
        engine.resetTripSession("이마트 성수점")
        if ("장바구니" !in session.value.permanentTrunkNotes) {
            engine.togglePermanentTrunkNote("장바구니")
        }
        if ("캠핑의자" !in session.value.permanentTrunkNotes) {
            engine.togglePermanentTrunkNote("캠핑의자")
        }
        mockSignalProvider.emit(
            VehicleSignal.DestinationUpdated(
                destinationName = "이마트 성수점",
                destinationPoi = PoiCategory.MART_SHOPPING,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR))
        mockSignalProvider.emit(
            VehicleSignal.DestinationArrived(
                destinationName = "이마트 성수점",
                isFinalDestination = true,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.MOCK_SIMULATOR))
    }

    fun runExtensionHighTempCargoDemo() {
        engine.resetTripSession("집 (성수동 자택)")
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD,
            origin = SignalOrigin.MOCK_SIMULATOR
        )
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR))
        mockSignalProvider.emit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 28.5f,
                origin = SignalOrigin.MOCK_SIMULATOR
            )
        )
        // 경유지 중간 정차 (목적지 미도착 상태에서 P단)
        mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.MOCK_SIMULATOR))
    }

    /**
     * 운전자가 실제로 경험하는 시간 흐름을 시연하는 실증 주행 여정 자동 리플레이어
     */
    fun startEmpiricalJourneyReplay(journeyType: Int = 1) {
        if (_isReplaying.value) return
        viewModelScope.launch {
            _isReplaying.value = true
            try {
                // Step 1 (15%): 출발지 정차 및 트렁크 열림
                _replayProgress.value = 15
                _replayStageTitle.value = "01. 출발지 정차 및 트렁크 개방"
                engine.resetTripSession("집 (성수동 자택)")
                mockSignalProvider.emit(
                    VehicleSignal.DoorStateChanged(
                        zone = DoorZone.TRUNK,
                        isOpen = true,
                        currentLocationPoi = PoiCategory.MART_SHOPPING,
                        cargoTag = CargoTag.FRESH_FOOD,
                        origin = SignalOrigin.MOCK_SIMULATOR
                    )
                )
                delay(1200L)

                // Step 2 (35%): 적재 완료 및 AI 음성 메모 인식
                _replayProgress.value = 35
                _replayStageTitle.value = "02. 물품 적재 완료 & AI 음성 메모 인식"
                mockSignalProvider.emit(
                    VehicleSignal.DoorStateChanged(
                        zone = DoorZone.TRUNK,
                        isOpen = false,
                        currentLocationPoi = PoiCategory.MART_SHOPPING,
                        cargoTag = CargoTag.FRESH_FOOD,
                        origin = SignalOrigin.MOCK_SIMULATOR
                    )
                )
                mockSignalProvider.emit(
                    VehicleSignal.VoiceCargoNoteReceived(
                        utterance = "아이스크림이랑 신선 과일 트렁크에 실었어",
                        currentLocationPoi = PoiCategory.MART_SHOPPING,
                        origin = SignalOrigin.AI_AGENT_INFERRED
                    )
                )
                delay(1200L)

                // Step 3 (65%): D단 전환 및 내비게이션 주행 중 (안전 대기)
                _replayProgress.value = 65
                _replayStageTitle.value = "03. D단 전환 및 내비게이션 주행 중 (안전 대기)"
                mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR))
                delay(1200L)

                // Step 4 (85%): 목적지 도착 판정 (성수동 자택)
                _replayProgress.value = 85
                _replayStageTitle.value = "04. 목적지 도착 판정 (성수동 자택)"
                mockSignalProvider.emit(
                    VehicleSignal.DestinationArrived(
                        destinationName = "집 (성수동 자택)",
                        isFinalDestination = true,
                        origin = SignalOrigin.MOCK_SIMULATOR
                    )
                )
                delay(1200L)

                // Step 5 (100%): P단 주차 완료 및 하차 브리핑 팝업
                _replayProgress.value = 100
                _replayStageTitle.value = "05. P단 주차 완료 & 하차 전 개인화 브리핑 팝업"
                mockSignalProvider.emit(VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.MOCK_SIMULATOR))
                delay(1800L)
            } finally {
                _isReplaying.value = false
                _replayStageTitle.value = null
            }
        }
    }

    fun resetAll() {
        engine.resetTripSession()
        updateDerivedState(session.value)
    }

    // =========================================================================
    // Epic 3: 상시 보관 물품 토글 및 사용자 커스텀 적재 음성/텍스트 주입
    // =========================================================================

    val presetPermanentItems = listOf("장바구니", "우산", "캠핑의자", "세차용품")

    fun togglePermanentTrunkItem(item: String) {
        engine.togglePermanentTrunkNote(item)
        updateDerivedState(session.value)
    }

    fun submitCustomVoiceUtterance(text: String) {
        if (text.isBlank()) return
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = text.trim(),
                currentLocationPoi = session.value.destinationPoi,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
        )
        updateDerivedState(session.value)
    }

    // =========================================================================
    // Milestone 1 & 3: 일반 운전자 화면(GeneralUserScreen) 위젯 연동 액션 & 프리셋
    // =========================================================================

    fun setArrivalAlertEnabled(enabled: Boolean) {
        _isArrivalAlertEnabled.value = enabled
        settingsStore.update { it.copy(arrivalAlertEnabled = enabled) }
    }

    fun updateSettings(newSettings: AppSettings) {
        settingsStore.update { newSettings }
        _isArrivalAlertEnabled.value = newSettings.arrivalAlertEnabled
    }

    fun startQuickVoiceRegistration(hasMicPermission: Boolean = true) {
        voiceCoordinator?.startQuickVoiceNote(hasRecordAudioPermission = hasMicPermission)
    }

    fun startGuidedVoiceRegistration(
        automatic: Boolean = false,
        autoRequestId: String? = null,
        hasMicPermission: Boolean = true
    ) {
        voiceCoordinator?.startGuidedRegistration(
            automatic = automatic,
            autoRequestId = autoRequestId,
            hasRecordAudioPermission = hasMicPermission
        )
    }

    fun stopVoiceRegistration(reason: String = "음성 입력을 종료했습니다.") {
        voiceCoordinator?.stopVoice(reason)
    }

    fun selectVoiceQuantity(amount: Int) {
        voiceCoordinator?.selectManualQuantity(amount)
    }

    fun clearVoiceError() {
        voiceCoordinator?.clearError()
    }

    fun confirmGleoDraft(draftId: String): CargoActionResult {
        return gleoActions.confirm(draftId)
    }

    fun cancelGleoDraft(draftId: String) {
        gleoActions.cancel(draftId)
    }

    fun registerStructuredCargo(
        name: String,
        quantity: Int = 1,
        purpose: String = "",
        targetDestinationName: String? = null,
        countAsBundle: Boolean = false,
        originName: String? = null,
        isPerishableOverride: Boolean? = null,
        isPermanent: Boolean = false
    ): CargoActionResult {
        return engine.registerStructuredCargo(
            name = name,
            quantity = quantity,
            purpose = purpose,
            originName = originName,
            targetDestinationName = targetDestinationName,
            countAsBundle = countAsBundle,
            origin = SignalOrigin.AI_AGENT_INFERRED,
            isPerishableOverride = isPerishableOverride,
            isPermanent = isPermanent
        )
    }

    fun requestManualCargoRegistration() {
        pleosSignalProvider.refreshLocationContext()
        engine.requestManualCargoRegistration()
    }

    fun refreshLocationContext() {
        pleosSignalProvider.refreshLocationContext()
    }

    fun refreshVehicleSensors() {
        pleosSignalProvider.refreshVehicleBindings()
        pleosSignalProvider.refreshNow()
        pleosSignalProvider.refreshLocationContext()
    }

    fun unloadCargoItem(itemId: String, quantity: Int? = null): CargoActionResult {
        return engine.unloadCargoItem(itemId, quantity)
    }

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
        val result = engine.editCargoItem(
            itemId = itemId,
            name = name,
            quantity = quantity,
            purpose = purpose,
            originName = originName,
            targetDestinationName = targetDestinationName,
            countAsBundle = countAsBundle,
            isPerishableOverride = isPerishableOverride,
            isPermanent = isPermanent
        )
        updateDerivedState(session.value)
        return result
    }

    fun removeCargoItem(itemId: String): CargoActionResult {
        val result = engine.removeCargoItem(itemId)
        updateDerivedState(session.value)
        return result
    }

    fun confirmUnloadCurrentStop(): CargoActionResult {
        return engine.confirmUnloadCargo()
    }

    fun skipLoadingRegistration() {
        engine.skipLoadingRegistration()
    }

    fun setEmulatorDemoArmed(armed: Boolean) {
        if (hasRuntimeDemo) {
            VehicleContextRuntime.arrivalDemo.setArmed(armed)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceCoordinator?.close()
    }

    fun applyCargoPresetMartGroceries() {
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "이마트에서 신선식품과 아이스크림 장보기",
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
        )
        updateDerivedState(session.value)
    }

    fun applyCargoPresetCake() {
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = session.value.destinationPoi,
                cargoTag = CargoTag.FRAGILE,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = session.value.destinationPoi,
                cargoTag = CargoTag.FRAGILE,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "베이커리에서 생일 케이크랑 와인 실었어",
                currentLocationPoi = session.value.destinationPoi,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
        )
        updateDerivedState(session.value)
    }

    fun applyCargoPresetStroller() {
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = session.value.destinationPoi,
                cargoTag = CargoTag.GENERAL,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = session.value.destinationPoi,
                cargoTag = CargoTag.GENERAL,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "트렁크에 유모차 실었어",
                currentLocationPoi = session.value.destinationPoi,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
        )
        updateDerivedState(session.value)
    }

    fun applyCargoPresetGolfBag() {
        if ("골프백" !in session.value.permanentTrunkNotes) {
            engine.togglePermanentTrunkNote("골프백")
        }
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.LEISURE_CAMPING,
                cargoTag = CargoTag.PERMANENT_KEEP,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.LEISURE_CAMPING,
                cargoTag = CargoTag.PERMANENT_KEEP,
                origin = SignalOrigin.REAL_SDK
            )
        )
        updateDerivedState(session.value)
    }

    fun clearTrunkCargo() {
        engine.clearTrunkCargo()
        if (activePrompt.value?.scenarioId == "trunk_reminder_mvp") {
            engine.dismissActivePrompt()
        }
        updateDerivedState(session.value)
    }

    class Factory(
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            VehicleContextRuntime.ensureInitialized(appContext)
            val coordinator = CargoVoiceCoordinator(
                context = appContext.applicationContext,
                engine = VehicleContextRuntime.engine,
                settingsProvider = { VehicleContextRuntime.settingsStore.settings.value }
            )
            return MainViewModel(
                pleosSignalProvider = VehicleContextRuntime.pleosSignalProvider,
                mockSignalProvider = VehicleContextRuntime.mockSignalProvider,
                engine = VehicleContextRuntime.engine,
                actionDispatcher = VehicleContextRuntime.actionDispatcher,
                settingsStore = VehicleContextRuntime.settingsStore,
                voiceCoordinator = coordinator,
                gleoActions = VehicleContextRuntime.gleoActions,
                hasRuntimeDemo = true
            ) as T
        }
    }
}
