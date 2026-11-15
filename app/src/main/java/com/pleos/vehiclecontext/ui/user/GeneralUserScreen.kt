package com.pleos.vehiclecontext.ui.user

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pleos.vehiclecontext.core.model.CargoItemRecord
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.voice.VoiceInputUiState
import com.pleos.vehiclecontext.gleo.GleoDraft
import com.pleos.vehiclecontext.ui.MainViewModel
import com.pleos.vehiclecontext.ui.model.CargoItemPresentation
import com.pleos.vehiclecontext.ui.model.FreshnessCareStatus
import com.pleos.vehiclecontext.ui.model.FreshnessRiskLevel
import com.pleos.vehiclecontext.ui.model.UserScreenDataMapper
import com.pleos.vehiclecontext.ui.model.UserSubTab
import com.pleos.vehiclecontext.ui.overlay.UnifiedVehicleAlertPopup
import com.pleos.vehiclecontext.ui.overlay.buildCargoConfirmedPopupSpec
import com.pleos.vehiclecontext.ui.overlay.buildTrunkLoadingDetectedSpec
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 운전자 모드 (General User Mode) 최상위 컨테이너:
 * ViewModel 상태를 수집하여 모던 콕핏 3단 프레임워크인 [GeneralUserScreenContent]로 전달합니다.
 */
@Composable
fun GeneralUserScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val cargoItems by viewModel.trunkCargoItems.collectAsState()
    val freshness by viewModel.freshnessStatus.collectAsState()
    val isArrivalAlertEnabled by viewModel.isArrivalAlertEnabled.collectAsState()
    val activePrompt by viewModel.activePrompt.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val voiceState by viewModel.voiceUiState.collectAsState()
    val gleoDraft by viewModel.gleoDraft.collectAsState()

    LaunchedEffect(
        session.autoVoiceRegistrationId,
        appSettings.autoVoiceOnTrunkClose,
        session.currentGear,
        session.openDoors,
        session.unloadConfirmationNeeded,
        voiceState.active,
        gleoDraft
    ) {
        val autoReqId = session.autoVoiceRegistrationId
        val eligible = autoReqId != null &&
            appSettings.autoVoiceOnTrunkClose &&
            session.currentGear == GearState.PARK &&
            DoorZone.TRUNK !in session.openDoors &&
            !session.unloadConfirmationNeeded &&
            !voiceState.active &&
            gleoDraft == null
        if (eligible && autoReqId != null) {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.startGuidedVoiceRegistration(
                automatic = true,
                autoRequestId = autoReqId,
                hasMicPermission = hasMic
            )
        }
    }

    GeneralUserScreenContent(
        session = session,
        cargoItems = cargoItems,
        freshness = freshness,
        isArrivalAlertEnabled = isArrivalAlertEnabled,
        activePrompt = activePrompt,
        onToggleArrivalAlert = { viewModel.setArrivalAlertEnabled(it) },
        onAcknowledgePrompt = { viewModel.acknowledgePrompt() },
        onExecuteControl = { viewModel.executeControl(it) },
        onApplyPresetMart = { viewModel.applyCargoPresetMartGroceries() },
        onApplyPresetCake = { viewModel.applyCargoPresetCake() },
        onApplyPresetStroller = { viewModel.applyCargoPresetStroller() },
        onApplyPresetGolfBag = { viewModel.applyCargoPresetGolfBag() },
        onClearTrunk = { viewModel.clearTrunkCargo() },
        onSubmitVoiceNote = { viewModel.submitCustomVoiceUtterance(it) },
        appSettings = appSettings,
        onUpdateSettings = { viewModel.updateSettings(it) },
        voiceState = voiceState,
        gleoDraft = gleoDraft,
        onStartVoiceRegistration = {
            viewModel.startGuidedVoiceRegistration(automatic = false, hasMicPermission = true)
        },
        onStopVoiceRegistration = { viewModel.stopVoiceRegistration() },
        onSelectVoiceQuantity = { viewModel.selectVoiceQuantity(it) },
        onConfirmGleo = { viewModel.confirmGleoDraft(it) },
        onCancelGleo = { viewModel.cancelGleoDraft(it) },
        onUnloadCargoItem = { viewModel.unloadCargoItem(it) },
        onConfirmUnloadStop = { viewModel.confirmUnloadCurrentStop() },
        onOpenManualAdd = { viewModel.requestManualCargoRegistration() },
        onRefreshLocationContext = { viewModel.refreshLocationContext() },
        onConfirmStructuredCargo = { name, quantity, purpose, originName, targetDest, countAsBundle, isPerishable, isPermanent ->
            viewModel.registerStructuredCargo(
                name = name,
                quantity = quantity,
                purpose = purpose,
                targetDestinationName = targetDest,
                countAsBundle = countAsBundle,
                originName = originName,
                isPerishableOverride = isPerishable,
                isPermanent = isPermanent
            )
        },
        onSkipCargoRegistration = { viewModel.skipLoadingRegistration() },
        onEditCargoItem = { itemId, name, quantity, purpose, originName, targetDest, countAsBundle, isPerishable, isPermanent ->
            viewModel.editCargoItem(
                itemId = itemId,
                name = name,
                quantity = quantity,
                purpose = purpose,
                originName = originName,
                targetDestinationName = targetDest,
                countAsBundle = countAsBundle,
                isPerishableOverride = isPerishable,
                isPermanent = isPermanent
            )
        },
        onDeleteCargoItem = { itemId -> viewModel.removeCargoItem(itemId) },
        modifier = modifier
    )
}

/**
 * 현대차 ccNC / 테슬라 표준 모던 콕핏 3단 프레임워크:
 * 1. [좌측] 세로 네비게이션 레일 (84dp)
 * 2. [상단] 글로벌 텔레메트리 헤더 바 (56dp)
 * 3. [중앙] 일체형 트렁크 캔버스 스테이지 & 서브 상세 페이지 (광활한 무대)
 * 4. [하단] 고정 플로팅 액션 덱 (104dp 고정 독 - 버튼 클리핑 0%)
 */
@Composable
fun GeneralUserScreenContent(
    session: TripSession,
    cargoItems: List<CargoItemPresentation>,
    freshness: FreshnessCareStatus,
    isArrivalAlertEnabled: Boolean,
    activePrompt: ContextAction?,
    onToggleArrivalAlert: (Boolean) -> Unit,
    onAcknowledgePrompt: () -> Unit,
    onExecuteControl: (VehicleControlCommand) -> Unit,
    onApplyPresetMart: () -> Unit,
    onApplyPresetCake: () -> Unit,
    onApplyPresetStroller: () -> Unit,
    onApplyPresetGolfBag: () -> Unit,
    onClearTrunk: () -> Unit,
    onSubmitVoiceNote: (String) -> Unit,
    initialSubTab: UserSubTab = UserSubTab.DASHBOARD,
    initialSidePanelMode: CockpitSidePanelMode? = null,
    appSettings: AppSettings = AppSettings(),
    onUpdateSettings: (AppSettings) -> Unit = {},
    voiceState: VoiceInputUiState = VoiceInputUiState(),
    gleoDraft: GleoDraft? = null,
    onStartVoiceRegistration: (() -> Unit)? = null,
    onStopVoiceRegistration: () -> Unit = {},
    onSelectVoiceQuantity: (Int) -> Unit = {},
    onConfirmGleo: (String) -> Unit = {},
    onCancelGleo: (String) -> Unit = {},
    onUnloadCargoItem: ((String) -> Unit)? = null,
    onConfirmUnloadStop: (() -> Unit)? = null,
    onOpenManualAdd: () -> Unit = {},
    onRefreshLocationContext: () -> Unit = {},
    onConfirmStructuredCargo: (
        name: String,
        quantity: Int,
        purpose: String,
        originName: String?,
        targetDestinationName: String?,
        countAsBundle: Boolean,
        isPerishable: Boolean,
        isPermanent: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSkipCargoRegistration: () -> Unit = {},
    onEditCargoItem: (
        itemId: String,
        name: String,
        quantity: Int,
        purpose: String,
        originName: String?,
        targetDestinationName: String?,
        countAsBundle: Boolean,
        isPerishable: Boolean,
        isPermanent: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDeleteCargoItem: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    var selectedSubTab by rememberSaveable { mutableStateOf(initialSubTab) }
    var sidePanelMode by rememberSaveable { mutableStateOf(initialSidePanelMode) }
    var openFormFromLoadingAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmedPopupCargo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingCargoItem by remember { mutableStateOf<CargoItemRecord?>(null) }

    LaunchedEffect(session.pendingLoadingPromptRequestId) {
        if (session.pendingLoadingPromptRequestId == null) {
            openFormFromLoadingAlertId = null
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // [1] Left Navigation Rail (Modern Automotive Standard)
        CockpitNavigationRail(
            selectedTab = selectedSubTab,
            onTabSelected = { selectedSubTab = it },
            cargoCount = cargoItems.size,
            isSettingsOpen = sidePanelMode == CockpitSidePanelMode.SETTINGS,
            isUserAccountOpen = sidePanelMode == CockpitSidePanelMode.USER_ACCOUNT,
            onOpenSettings = {
                sidePanelMode = if (sidePanelMode == CockpitSidePanelMode.SETTINGS) null else CockpitSidePanelMode.SETTINGS
            },
            onOpenUserAccount = {
                sidePanelMode = if (sidePanelMode == CockpitSidePanelMode.USER_ACCOUNT) null else CockpitSidePanelMode.USER_ACCOUNT
            }
        )

        // [2] Right Content Canvas & Control System
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // [2.1] Top Telemetry & Destination Status Header
            CockpitTopHeader(
                session = session,
                isArrivalAlertEnabled = isArrivalAlertEnabled,
                onToggleArrivalAlert = onToggleArrivalAlert
            )

            // [2.2] Center Wide Visual Stage + Split Side Panel (Settings & User Profile)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Left Stage (Main Dashboard & Sub-tabs)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Crossfade(
                        targetState = selectedSubTab,
                        animationSpec = tween(durationMillis = 200),
                        label = "CockpitStageCrossfade"
                    ) { tab ->
                        when (tab) {
                            UserSubTab.DASHBOARD -> {
                                CockpitStageCanvas(
                                    session = session,
                                    cargoItems = cargoItems,
                                    freshness = freshness,
                                    onNavigateToCargoList = { selectedSubTab = UserSubTab.CARGO_LIST },
                                    onNavigateToFreshness = { selectedSubTab = UserSubTab.FRESHNESS }
                                )
                            }
                            UserSubTab.CARGO_LIST -> {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    CargoLiveOverviewWidget(
                                        session = session,
                                        cargoItems = cargoItems,
                                        onClearTrunk = onClearTrunk,
                                        onUnloadItem = onUnloadCargoItem,
                                        onConfirmUnloadStop = onConfirmUnloadStop,
                                        onEditItem = { uiItem ->
                                            editingCargoItem = UserScreenDataMapper.resolveEditableRecord(session, uiItem)
                                        },
                                        onDeleteItem = { itemId ->
                                            onDeleteCargoItem?.invoke(itemId) ?: onUnloadCargoItem?.invoke(itemId)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            UserSubTab.FRESHNESS -> {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    FreshnessCareWidget(
                                        freshness = freshness,
                                        cabinTempCelsius = session.cabinTempCelsius,
                                        onActivateCooling = { onExecuteControl(VehicleControlCommand.ACTIVATE_CABIN_COOLING) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            UserSubTab.TIMELINE -> {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    TripTimelineWidget(
                                        currentPhase = session.phase,
                                        timelineSteps = session.timelineSteps,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Split Screen Side Panel (Settings & User Account)
                CockpitSplitSidePanel(
                    isOpen = sidePanelMode != null,
                    mode = sidePanelMode ?: CockpitSidePanelMode.SETTINGS,
                    onClose = { sidePanelMode = null },
                    settings = appSettings,
                    onUpdateSettings = onUpdateSettings
                )
            }

            // [2.3] Bottom Fixed Floating Action Deck (Zero clipping guaranteed)
            CockpitFloatingActionDeck(
                activePrompt = if (isArrivalAlertEnabled) activePrompt else null,
                onExecuteControl = onExecuteControl,
                onAcknowledgePrompt = onAcknowledgePrompt,
                onApplyPresetMart = onApplyPresetMart,
                onApplyPresetCake = onApplyPresetCake,
                onApplyPresetStroller = onApplyPresetStroller,
                onApplyPresetGolfBag = onApplyPresetGolfBag,
                onClearTrunk = onClearTrunk,
                onSubmitVoiceNote = onSubmitVoiceNote,
                onOpenManualAdd = onOpenManualAdd,
                voiceState = voiceState,
                gleoDraft = gleoDraft,
                onStartVoiceRegistration = onStartVoiceRegistration,
                onStopVoiceRegistration = onStopVoiceRegistration,
                onSelectVoiceQuantity = onSelectVoiceQuantity,
                onConfirmGleo = onConfirmGleo,
                onCancelGleo = onCancelGleo
            )
        }

        // [3] 통일된 규격의 적재 감지 알림 / 직접 추가 폼 / 등록 확인 후 선택적 트렁크 제어 팝업
        val visiblePrompt = if (isArrivalAlertEnabled) activePrompt else null
        val pendingReqId = session.pendingLoadingPromptRequestId
        val isManualReq = pendingReqId?.startsWith("manual-") == true
        val isAutoLoadReq = pendingReqId?.startsWith("load-") == true
        val showRegistrationPopup = pendingReqId != null &&
            !session.unloadConfirmationNeeded &&
            (isManualReq || DoorZone.TRUNK !in session.openDoors) &&
            visiblePrompt == null &&
            gleoDraft == null

        when {
            editingCargoItem != null -> {
                val targetRecord = editingCargoItem!!
                CargoRegistrationDialog(
                    session = session,
                    voiceState = voiceState,
                    onRefreshLocationContext = onRefreshLocationContext,
                    onConfirm = { name, quantity, purpose, originName, targetDest, countAsBundle, isPerishable, isPermanent ->
                        onEditCargoItem(
                            targetRecord.id,
                            name,
                            quantity,
                            purpose,
                            originName,
                            targetDest,
                            countAsBundle,
                            isPerishable,
                            isPermanent
                        )
                        editingCargoItem = null
                    },
                    onSkip = { editingCargoItem = null },
                    onStartVoice = { onStartVoiceRegistration?.invoke() },
                    onStopVoice = onStopVoiceRegistration,
                    onSelectVoiceQuantity = onSelectVoiceQuantity,
                    editingItem = targetRecord
                )
            }
            confirmedPopupCargo != null -> {
                val (itemName, destName) = confirmedPopupCargo!!
                val confirmedSpec = remember(itemName, destName, session.openDoors) {
                    buildCargoConfirmedPopupSpec(
                        itemName = itemName,
                        targetDestination = destName,
                        session = session
                    )
                }
                UnifiedVehicleAlertPopup(
                    spec = confirmedSpec,
                    onPrimaryClick = {
                        val cmd = if (DoorZone.TRUNK in session.openDoors) {
                            VehicleControlCommand.CLOSE_TRUNK
                        } else {
                            VehicleControlCommand.OPEN_TRUNK
                        }
                        onExecuteControl(cmd)
                        confirmedPopupCargo = null
                    },
                    onSecondaryClick = { confirmedPopupCargo = null },
                    onDismiss = { confirmedPopupCargo = null }
                )
            }
            showRegistrationPopup && isAutoLoadReq && openFormFromLoadingAlertId != pendingReqId -> {
                val loadingSpec = remember(session) {
                    buildTrunkLoadingDetectedSpec(session)
                }
                UnifiedVehicleAlertPopup(
                    spec = loadingSpec,
                    onPrimaryClick = {
                        openFormFromLoadingAlertId = pendingReqId
                    },
                    onSecondaryClick = onSkipCargoRegistration,
                    onDismiss = onSkipCargoRegistration
                )
            }
            showRegistrationPopup -> {
                val wasManualDirectAdd = isManualReq
                CargoRegistrationDialog(
                    session = session,
                    voiceState = voiceState,
                    onRefreshLocationContext = onRefreshLocationContext,
                    onConfirm = { name, quantity, purpose, originName, targetDest, countAsBundle, isPerishable, isPermanent ->
                        onConfirmStructuredCargo(
                            name,
                            quantity,
                            purpose,
                            originName,
                            targetDest,
                            countAsBundle,
                            isPerishable,
                            isPermanent
                        )
                        openFormFromLoadingAlertId = null
                        if (wasManualDirectAdd) {
                            confirmedPopupCargo = name to (targetDest ?: if (isPermanent) "상시 보관" else session.destinationName)
                        }
                    },
                    onSkip = {
                        openFormFromLoadingAlertId = null
                        onSkipCargoRegistration()
                    },
                    onStartVoice = { onStartVoiceRegistration?.invoke() },
                    onStopVoice = onStopVoiceRegistration,
                    onSelectVoiceQuantity = onSelectVoiceQuantity
                )
            }
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (Android Studio 0초 실시간 렌더링 검증)
// =========================================================================

@Preview(name = "Cockpit Framework - Dark (1024x600)", widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun GeneralUserScreenPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummySession = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            phase = TripPhase.ARRIVED_PARKED,
            openWindowsCount = 0
        )
        val dummyPrompt = ContextAction(
            scenarioId = "PROMPT_TRUNK_ARRIVAL",
            scenarioTitle = "트렁크 하차 케어",
            headline = "목적지 도착! 트렁크 확인 안내",
            bodyMessage = "출발 전 실으신 [이마트 장본 짐]을 잊지 말고 챙기세요!",
            contextReasonBadge = "도착 + P단 주차 감지",
            signalOrigin = SignalOrigin.REAL_SDK,
            voiceTtsMessage = "목적지에 도착했습니다.",
            primaryAckLabel = "확인 완료"
        )
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품)", "신선식품", "🛒", "트렁크 중앙", "14:22", true),
            CargoItemPresentation("2", "투썸 딸기 케이크", "파손주의", "🎂", "트렁크 우측", "14:35", true)
        )
        val dummyFreshness = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "신선식품 3종",
            currentTempCelsius = 21.0f,
            elapsedMinutes = 20,
            limitMinutes = 60,
            remainingMinutes = 40,
            progress = 0.33f,
            riskLevel = FreshnessRiskLevel.SAFE
        )

        GeneralUserScreenContent(
            session = dummySession,
            cargoItems = dummyItems,
            freshness = dummyFreshness,
            isArrivalAlertEnabled = true,
            activePrompt = dummyPrompt,
            onToggleArrivalAlert = {},
            onAcknowledgePrompt = {},
            onExecuteControl = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Preview(name = "Cockpit Framework - Split Settings Mode (1024x600)", widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun GeneralUserScreenSplitSettingsPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummySession = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            phase = TripPhase.ARRIVED_PARKED,
            openWindowsCount = 0
        )
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품)", "신선식품", "🛒", "트렁크 중앙", "14:22", true),
            CargoItemPresentation("2", "투썸 딸기 케이크", "파손주의", "🎂", "트렁크 우측", "14:35", true)
        )
        val dummyFreshness = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "신선식품 3종",
            currentTempCelsius = 21.0f,
            elapsedMinutes = 20,
            limitMinutes = 60,
            remainingMinutes = 40,
            progress = 0.33f,
            riskLevel = FreshnessRiskLevel.SAFE
        )

        GeneralUserScreenContent(
            session = dummySession,
            cargoItems = dummyItems,
            freshness = dummyFreshness,
            isArrivalAlertEnabled = true,
            activePrompt = null,
            initialSidePanelMode = CockpitSidePanelMode.SETTINGS,
            onToggleArrivalAlert = {},
            onAcknowledgePrompt = {},
            onExecuteControl = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Preview(name = "Cockpit Framework - Split User Account Mode (1024x600)", widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun GeneralUserScreenSplitUserAccountPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummySession = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            phase = TripPhase.ARRIVED_PARKED,
            openWindowsCount = 0
        )
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품)", "신선식품", "🛒", "트렁크 중앙", "14:22", true)
        )
        val dummyFreshness = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "신선식품",
            currentTempCelsius = 21.0f,
            elapsedMinutes = 20,
            limitMinutes = 60,
            remainingMinutes = 40,
            progress = 0.33f,
            riskLevel = FreshnessRiskLevel.SAFE
        )

        GeneralUserScreenContent(
            session = dummySession,
            cargoItems = dummyItems,
            freshness = dummyFreshness,
            isArrivalAlertEnabled = true,
            activePrompt = null,
            initialSidePanelMode = CockpitSidePanelMode.USER_ACCOUNT,
            onToggleArrivalAlert = {},
            onAcknowledgePrompt = {},
            onExecuteControl = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Preview(name = "Cockpit Framework - Light (1024x600)", widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun GeneralUserScreenPreviewLight() {
    PleosTheme(darkTheme = false) {
        val dummySession = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            phase = TripPhase.ARRIVED_PARKED,
            openWindowsCount = 0
        )
        val dummyPrompt = ContextAction(
            scenarioId = "PROMPT_TRUNK_ARRIVAL",
            scenarioTitle = "트렁크 하차 케어",
            headline = "목적지 도착! 트렁크 확인 안내",
            bodyMessage = "출발 전 실으신 [이마트 장본 짐]을 잊지 말고 챙기세요!",
            contextReasonBadge = "도착 + P단 주차 감지",
            signalOrigin = SignalOrigin.REAL_SDK,
            voiceTtsMessage = "목적지에 도착했습니다.",
            primaryAckLabel = "확인 완료"
        )
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품)", "신선식품", "🛒", "트렁크 중앙", "14:22", true)
        )
        val dummyFreshness = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "신선식품",
            currentTempCelsius = 21.0f,
            elapsedMinutes = 20,
            limitMinutes = 60,
            remainingMinutes = 40,
            progress = 0.33f,
            riskLevel = FreshnessRiskLevel.SAFE
        )

        GeneralUserScreenContent(
            session = dummySession,
            cargoItems = dummyItems,
            freshness = dummyFreshness,
            isArrivalAlertEnabled = true,
            activePrompt = dummyPrompt,
            onToggleArrivalAlert = {},
            onAcknowledgePrompt = {},
            onExecuteControl = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Composable
private fun GeneralUserVehiclePreviewScaffold(darkTheme: Boolean) {
    PleosTheme(darkTheme = darkTheme) {
        val dummySession = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            phase = TripPhase.DRIVING,
            openWindowsCount = 0,
            cabinTempCelsius = 21.5f
        )
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (1묶음)", "신선식품", "🛒", "트렁크 · 우리 집 (장보기)", "14:22", true),
            CargoItemPresentation("2", "투썸 딸기 케이크 (2개)", "파손주의", "🎂", "트렁크 · 우리 집 (생일 파티)", "14:35", true),
            CargoItemPresentation("3", "타이틀리스트 골프백", "상시보관", "⛳", "트렁크 상시 보관", "어제", false)
        )
        val dummyFreshness = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "이마트 장보기 외 1건",
            currentTempCelsius = 21.5f,
            elapsedMinutes = 20,
            limitMinutes = 60,
            remainingMinutes = 40,
            progress = 0.33f,
            riskLevel = FreshnessRiskLevel.SAFE
        )

        GeneralUserScreenContent(
            session = dummySession,
            cargoItems = dummyItems,
            freshness = dummyFreshness,
            isArrivalAlertEnabled = true,
            activePrompt = null,
            onToggleArrivalAlert = {},
            onAcknowledgePrompt = {},
            onExecuteControl = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Preview(
    name = "Pleos Connect Ultra-Wide - Dark (2560x1440)",
    widthDp = 2560,
    heightDp = 1440,
    showBackground = true
)
@Composable
fun GeneralUserScreenPreviewPleosUltraWideDark() {
    GeneralUserVehiclePreviewScaffold(darkTheme = true)
}

@Preview(
    name = "Pleos Connect Ultra-Wide - Light (2560x1440)",
    widthDp = 2560,
    heightDp = 1440,
    showBackground = true
)
@Composable
fun GeneralUserScreenPreviewPleosUltraWideLight() {
    GeneralUserVehiclePreviewScaffold(darkTheme = false)
}

@Preview(
    name = "ccNC Panoramic Wide - Dark (1920x720)",
    widthDp = 1920,
    heightDp = 720,
    showBackground = true
)
@Composable
fun GeneralUserScreenPreviewCcncWideDark() {
    GeneralUserVehiclePreviewScaffold(darkTheme = true)
}
