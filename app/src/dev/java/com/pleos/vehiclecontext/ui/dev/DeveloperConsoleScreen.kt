package com.pleos.vehiclecontext.ui.dev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.TripTimelineStep
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.ui.MainViewModel
import com.pleos.vehiclecontext.ui.components.VehicleDigitalTwinCard
import com.pleos.vehiclecontext.ui.theme.PleosTheme

private val BgDark = Color(0xFF0E131F)
private val CardDark = Color(0xFF182032)
private val CardHighlight = Color(0xFF1F2B44)
private val AccentBlue = Color(0xFF38BDF8)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentGreen = Color(0xFF10B981)
private val AccentPurple = Color(0xFFA855F7)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)

/**
 * 개발자/평가자 전용 테스트베드 콘솔 화면:
 * - 좌측: 3D 디지털 트윈 캔버스, 실증 주행 리플레이어, 하차 알림 스마트 카드, 주행 맥락 대시보드, 타임라인, 이벤트 로그
 * - 우측: 자율주행/AI 에이전트 시연, STT 음성 적재 입력, 상시 보관 인벤토리, 6대 핵심 시연 케이스, 센서 신호 수동 제어, 확장 플러그인 토글
 */
@Composable
fun DeveloperConsoleScreen(
    viewModel: MainViewModel,
    onSimulateRealAaosBackgroundPopup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val session by viewModel.session.collectAsState()
    val activePrompt by viewModel.activePrompt.collectAsState()
    val enabledPlugins by viewModel.enabledPluginIds.collectAsState()
    val eventLog by viewModel.eventLog.collectAsState()
    val lastVoice by viewModel.actionDispatcher.lastVoiceAnnouncement.collectAsState()
    val replayProgress by viewModel.replayProgress.collectAsState()
    val replayStageTitle by viewModel.replayStageTitle.collectAsState()
    val isReplaying by viewModel.isReplaying.collectAsState()

    val hmi = PleosTheme.hmi

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BgDark
    ) {
        if (hmi.isSideBySideStage) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(hmi.itemSpacing),
                horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
            ) {
                DeveloperLeftColumn(
                    session = session,
                    activePrompt = activePrompt,
                    eventLog = eventLog,
                    lastVoice = lastVoice,
                    replayProgress = replayProgress,
                    replayStageTitle = replayStageTitle,
                    isReplaying = isReplaying,
                    viewModel = viewModel,
                    onSimulateRealAaosBackgroundPopup = onSimulateRealAaosBackgroundPopup,
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                )

                DeveloperRightColumn(
                    viewModel = viewModel,
                    session = session,
                    enabledPlugins = enabledPlugins,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(hmi.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(hmi.sectionSpacing)
            ) {
                DeveloperLeftColumn(
                    session = session,
                    activePrompt = activePrompt,
                    eventLog = eventLog,
                    lastVoice = lastVoice,
                    replayProgress = replayProgress,
                    replayStageTitle = replayStageTitle,
                    isReplaying = isReplaying,
                    viewModel = viewModel,
                    onSimulateRealAaosBackgroundPopup = onSimulateRealAaosBackgroundPopup,
                    modifier = Modifier.fillMaxWidth()
                )

                DeveloperRightColumn(
                    viewModel = viewModel,
                    session = session,
                    enabledPlugins = enabledPlugins,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DeveloperLeftColumn(
    session: TripSession,
    activePrompt: ContextAction?,
    eventLog: List<String>,
    lastVoice: String?,
    replayProgress: Int,
    replayStageTitle: String?,
    isReplaying: Boolean,
    viewModel: MainViewModel,
    onSimulateRealAaosBackgroundPopup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hmi = PleosTheme.hmi
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
    ) {
        DevHeaderBanner(
            session = session,
            onSimulateRealAaosBackgroundPopup = onSimulateRealAaosBackgroundPopup
        )

        EmpiricalJourneyPlayerCard(
            isReplaying = isReplaying,
            replayProgress = replayProgress,
            replayStageTitle = replayStageTitle,
            onStartReplay = { viewModel.startEmpiricalJourneyReplay() }
        )

        // 차량 탑뷰 디지털 트윈 그래픽 (트렁크/2열/창문/자율주행 센서 실시간 하이라이트)
        VehicleDigitalTwinCard(session = session)

        if (activePrompt != null) {
            PreExitPromptCard(
                action = activePrompt,
                onAcknowledge = { viewModel.acknowledgePrompt() },
                onExecuteControl = { viewModel.executeControl(it) },
                onDismiss = { viewModel.dismissPrompt() }
            )
        } else {
            StandbyPromptPlaceholder(session = session)
        }

        TripContextDashboardCard(
            session = session,
            lastVoice = lastVoice
        )

        TripTimelineSummaryCard(
            timelineSteps = session.timelineSteps
        )

        EventLogCard(eventLog = eventLog)
    }
}

@Composable
private fun DeveloperRightColumn(
    viewModel: MainViewModel,
    session: TripSession,
    enabledPlugins: Set<String>,
    modifier: Modifier = Modifier
) {
    val hmi = PleosTheme.hmi
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
    ) {
        BridgeDiagnosticsPanel(viewModel = viewModel, session = session)
        AutonomousAndAiPanel(viewModel = viewModel)
        VoiceCargoInputPanel(viewModel = viewModel)
        PermanentTrunkInventoryPanel(viewModel = viewModel, session = session)
        CoreSixDemoCasesPanel(viewModel = viewModel)
        ManualSignalControlPanel(viewModel = viewModel)
        ExtensionScenariosPanel(
            viewModel = viewModel,
            plugins = viewModel.registeredPlugins,
            enabledIds = enabledPlugins
        )
    }
}

@Composable
private fun DevHeaderBanner(
    session: TripSession,
    onSimulateRealAaosBackgroundPopup: () -> Unit
) {
    val hmi = PleosTheme.hmi
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(hmi.chipCornerRadius),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hmi.cardPadding, vertical = hmi.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WHAT'S IN MY TRUNK? · Pleos AI × Autonomous Context",
                        color = AccentBlue,
                        fontSize = hmi.badgeFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "내리기 전, 트렁크와 차량 맥락을 한 번 더 (개발자 콘솔)",
                        color = TextPrimary,
                        fontSize = hmi.subheadFontSize,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(
                        label = session.autonomousMode.displayName,
                        color = if (session.autonomousMode == AutonomousDrivingMode.MANUAL) TextSecondary else AccentPurple
                    )
                    StatusBadge(
                        label = "기어: ${session.currentGear.label}",
                        color = if (session.currentGear == GearState.PARK) AccentGreen else AccentAmber
                    )
                }
            }

            Button(
                onClick = onSimulateRealAaosBackgroundPopup,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🚘 실제 AAOS 동작 체험: 홈/내비 화면으로 나간 뒤 3.5초 후 도착+P단 팝업·HUN 띄우기",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun PreExitPromptCard(
    action: ContextAction,
    onAcknowledge: () -> Unit,
    onExecuteControl: (VehicleControlCommand) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardHighlight),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, AccentAmber, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(label = action.scenarioTitle, color = AccentAmber)
                StatusBadge(label = action.signalOrigin.displayName, color = AccentBlue)
            }

            Text(
                text = action.headline,
                color = TextPrimary,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = action.bodyMessage,
                color = TextPrimary.copy(alpha = 0.9f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            if (!action.aiReasoningNote.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentPurple.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = action.aiReasoningNote,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Text(
                text = "감지 맥락: ${action.contextReasonBadge}",
                color = AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.25f))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                ) {
                    Text(
                        text = action.primaryAckLabel,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                action.availableControls.forEach { cmd ->
                    Button(
                        onClick = { onExecuteControl(cmd) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(
                            text = cmd.buttonLabel,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(onClick = onDismiss) {
                    Text(text = action.dismissLabel, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StandbyPromptPlaceholder(session: TripSession) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "하차 전 안내 대기 중 (현재 활성 알림 없음)",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            val statusExplain = when {
                !session.hasActiveTrunkUsage && !session.hasUnoccupiedRearSeatLoading && session.openWindowsCount == 0 ->
                    "이번 이동에서 기록된 트렁크/2열 적재 이력이 없습니다. (같은 목적지에 도착해도 불필요한 안내를 생략합니다)"
                session.currentGear != GearState.PARK ->
                    "주행 중(${session.currentGear.label})에는 운전 집중을 위해 하차 안내 예약을 유지하며 대기합니다."
                !session.isArrivedAtDestination ->
                    "중간 정차 상태입니다. 최종 목적지('${session.destinationName}') 도착 전에는 하차 안내를 생략합니다."
                else ->
                    "이번 도착에 대한 하차 안내가 이미 1회 제공되었거나 사용자가 확인('확인할게요')을 완료했습니다."
            }
            Text(
                text = statusExplain,
                color = TextPrimary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun TripContextDashboardCard(
    session: TripSession,
    lastVoice: String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "현재 이동 세션 맥락 (센서 + 자율주행 + AI 메모리)",
                color = AccentBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "• 현재 목적지: ${session.destinationName} (도착 여부: ${if (session.isArrivedAtDestination) "도착 완료" else "이동 중/미도착"})",
                color = TextPrimary,
                fontSize = 14.sp
            )
            Text(
                text = "• 트렁크 개폐 이력: ${if (session.hasActiveTrunkUsage) "있음 (${session.latestTrunkUsage?.openedAtPoi?.displayName ?: "AI 메모"} / ${session.latestTrunkUsage?.cargoTag?.displayName ?: session.aiCargoMemory?.inferredPrimaryTag?.displayName})" else "없음"}",
                color = if (session.hasActiveTrunkUsage) AccentGreen else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "• 자율주행 상태: ${session.autonomousMode.displayName} | 주행 프로파일: ${session.autonomousProfile.displayName}" +
                    if (session.isAvpDepartureHoldActive) " (자율주차 출발 홀드 중)" else "",
                color = AccentPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (session.aiCargoMemory != null) {
                Text(
                    text = "• 🤖 AI 적재 메모리: ${session.aiCargoMemory.aiBriefingSummary}",
                    color = AccentAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "• 2열 미착석 적재 추론: ${if (session.hasUnoccupiedRearSeatLoading) "감지됨" else "없음"} | 열린 창문: ${session.openWindowsCount}개",
                color = TextSecondary,
                fontSize = 13.sp
            )

            if (!lastVoice.isNullOrBlank()) {
                Text(
                    text = "🔊 최근 TTS 음성 브리핑: \"$lastVoice\"",
                    color = AccentAmber,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutonomousAndAiPanel(viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "★ 자율주행(AD/AVP) × AI 컨텍스트 에이전트 시연",
                color = AccentPurple,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "자연어 음성(STT) 적재 물품 추론, Lv.3 자율주행 적재물 보호 프로파일 전환, 자율 발렛 파킹(AVP) 하차 존 짐 미수거 방지 가드를 시연합니다.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DemoCaseButton("🤖 AI 음성 적재 태깅 (\"아이스크림·케이크·와인 샀어\")") {
                    viewModel.runAiVoiceCargoTaggingDemo()
                }
                DemoCaseButton("🚘 Lv.3 자율주행 + 적재물 보호 Comfort 모드 제어") {
                    viewModel.runAutonomousLv3CargoComfortDemo()
                }
                DemoCaseButton("🅿️ 자율 발렛 파킹(AVP) 하차 존 짐 미수거 방지 가드") {
                    viewModel.runAutonomousValetDropoffGuardDemo()
                }
            }
        }
    }
}

@Composable
private fun VoiceCargoInputPanel(viewModel: MainViewModel) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceUiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startQuickVoiceRegistration(hasMicPermission = true)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🎙️ 적재 물품 직접 말하기 / 입력 (Pleos STT + Vosk + AI)",
                color = AccentAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (voiceState.active) {
                    voiceState.status
                } else {
                    "마이크 버튼을 누르거나 직접 입력하면 AI가 물품과 변질 위험을 자동 분석합니다."
                },
                color = if (voiceState.active) AccentAmber else TextSecondary,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text("예: 코스트코에서 고기랑 수박 샀어", color = TextSecondary, fontSize = 12.sp)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentAmber,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                    )
                )

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.submitCustomVoiceUtterance(inputText)
                            inputText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("입력", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (voiceState.active) {
                            viewModel.stopVoiceRegistration()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.startQuickVoiceRegistration(hasMicPermission = true)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceState.active) Color(0xFFEF4444) else AccentPurple
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (voiceState.active) "⏹ 중단" else "🎤 음성",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgeDiagnosticsPanel(
    viewModel: MainViewModel,
    session: TripSession
) {
    val context = LocalContext.current
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val demoArmed by viewModel.emulatorDemoArmed.collectAsState()
    val demoStatus by viewModel.emulatorDemoStatus.collectAsState()

    val canDrawOverlays = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    val hasNotifPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val hasPowertrainPerm = ContextCompat.checkSelfPermission(
        context,
        com.pleos.vehiclecontext.core.signal.PleosSignalProvider.AAOS_PERM_CAR_POWERTRAIN
    ) == PackageManager.PERMISSION_GRANTED
    val hasDoorPerm = ContextCompat.checkSelfPermission(
        context,
        com.pleos.vehiclecontext.core.signal.PleosSignalProvider.PLEOS_PERM_CAR_DOORS
    ) == PackageManager.PERMISSION_GRANTED

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentBlue.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📡 실시간 수신 차량 센서 모니터 (SEQ #${bridgeStatus.sensorUpdateSeq})",
                        color = AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "앱이 실제 Pleos/AAOS/GPS에서 수신 중인 원본 데이터",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                OutlinedButton(onClick = { viewModel.refreshVehicleSensors() }) {
                    Text("새로고침", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 백그라운드 팝업(D-N-D-R-P) 필수 권한 체크
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (canDrawOverlays) Color(0xFF102A22) else Color(0xFF332011),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (canDrawOverlays) AccentGreen.copy(alpha = 0.5f) else AccentAmber,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (canDrawOverlays) {
                        "✅ 백그라운드 오버레이(SYSTEM_ALERT_WINDOW) 허용됨 · 웹콘솔 D-N-D-R-P 팝업 표시 가능"
                    } else {
                        "⚠️ 백그라운드 오버레이 미허용 — 웹콘솔 D-N-D-R-P 조작 시 Android가 백그라운드 팝업을 차단할 수 있음"
                    },
                    color = if (canDrawOverlays) AccentGreen else AccentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "권한 상태 | 알림(HUN)=${if (hasNotifPerm) "OK" else "NO"} · 기어(POWERTRAIN)=${if (hasPowertrainPerm) "OK" else "NO"} · 도어=${if (hasDoorPerm) "OK" else "NO"}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (!canDrawOverlays) {
                    Button(
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "다른 앱 위에 표시(오버레이) 허용하러 가기",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // 실시간 센서 값 테이블
            SensorRow("기어(RAW / 소스)", "${bridgeStatus.gearRaw} [${bridgeStatus.gearSource}]")
            SensorRow(
                "엔진 기어/단계",
                "${session.currentGear.label} · ${session.phase.title} · R경유=${session.hasReversedDuringDrive} · 도착=${session.isArrivedAtDestination}"
            )
            SensorRow(
                "도어(REAR / RL / RR)",
                "트렁크=${bridgeStatus.trunkDoorRaw} | 2L=${bridgeStatus.rearLeftDoorRaw} | 2R=${bridgeStatus.rearRightDoorRaw}"
            )
            SensorRow("도어 범위 / 누적", "${bridgeStatus.doorBoundsLabel} · 누적 ${bridgeStatus.doorEventCount}건 · ${bridgeStatus.door}")
            SensorRow(
                "창문 / 공조 / 2열시트",
                "창문=${bridgeStatus.windowOpenSummary} | 온도=${String.format("%.1f", bridgeStatus.cabinTempCelsius)}°C | 2열=${bridgeStatus.rearSeatSummary}"
            )
            SensorRow("ADAS / 크루즈", "${bridgeStatus.cruiseRaw} (엔진: ${session.autonomousMode.displayName})")
            SensorRow("내비 경로 / 목적지", "${bridgeStatus.naviRouteState} -> ${bridgeStatus.naviDestinationRaw}")
            SensorRow("GPS / 출발지 스냅샷", bridgeStatus.gpsSnapshotRaw)

            if (bridgeStatus.sensorFeed.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF223047))
                Text(
                    text = "실시간 센서 수신 피드 (최신순)",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E1525), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    bridgeStatus.sensorFeed.take(8).forEach { line ->
                        Text(
                            text = line,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (viewModel.emulatorDemoAvailable) {
                HorizontalDivider(color = Color(0xFF223047))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("에뮬레이터 GPS 도착 시연", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(demoStatus, color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = demoArmed,
                        onCheckedChange = { viewModel.setEmulatorDemoArmed(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131B2C), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = AccentBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.36f)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(0.64f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermanentTrunkInventoryPanel(
    viewModel: MainViewModel,
    session: TripSession
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📦 트렁크 상시 보관 인벤토리 (빠른 토글)",
                    color = AccentGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "등록: ${session.permanentTrunkNotes.size}개",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "자주 트렁크에 두는 물품을 토글해 두면, 마트나 레저 목적지 도착 시 역방향 알림을 제공합니다.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.presetPermanentItems.forEach { item ->
                    val isChecked = item in session.permanentTrunkNotes
                    Button(
                        onClick = { viewModel.togglePermanentTrunkItem(item) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isChecked) AccentGreen else Color(0xFF26334D)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isChecked) "✓ $item" else "+ $item",
                            color = if (isChecked) Color.Black else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreSixDemoCasesPanel(viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "첫 시연에서 확인할 6가지 핵심 동작 (슬라이드 7)",
                color = AccentAmber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DemoCaseButton("① 트렁크 사용 후 귀가 (1회 안내)") {
                    viewModel.runDemo1TrunkUsedHomeArrival()
                }
                DemoCaseButton("② 트렁크 미사용 귀가 (안내 생략)") {
                    viewModel.runDemo2NoTrunkUsageHomeArrival()
                }
                DemoCaseButton("③ 중간 정차 (도착 전 P단 생략)") {
                    viewModel.runDemo3IntermediateStop()
                }
                DemoCaseButton("④ 반복 P 전환 (재안내 방지)") {
                    viewModel.runDemo4RepeatedParkTransition()
                }
                DemoCaseButton("⑤ 경로 변경 (목적지 갱신)") {
                    viewModel.runDemo5RouteChanged()
                }
                DemoCaseButton("⑥ 앱 재시작 (안내 상태 복원)") {
                    viewModel.runDemo6AppRestartRecovery()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualSignalControlPanel(viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "개별 센서 신호 수동 조작 (Step-by-Step)",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { viewModel.resetAll() }) {
                    Text("새 이동 초기화", color = TextSecondary, fontSize = 12.sp)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DemoCaseButton("트렁크 열림→닫힘 (마트)") {
                    viewModel.triggerManualTrunkOpenClose(PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD)
                }
                DemoCaseButton("기어 D단 (주행)") {
                    viewModel.triggerManualGear(GearState.DRIVE)
                }
                DemoCaseButton("목적지 도착 신호") {
                    viewModel.triggerManualArrival()
                }
                DemoCaseButton("기어 P단 (주차)") {
                    viewModel.triggerManualGear(GearState.PARK)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtensionScenariosPanel(
    viewModel: MainViewModel,
    plugins: List<ScenarioPlugin>,
    enabledIds: Set<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "범용 컨텍스트 프레임워크 확장 시나리오 (플러그인 모듈)",
                color = AccentGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DemoCaseButton("확장 A: 2열 뒷좌석 소지품 적재 후 도착") {
                    viewModel.runExtensionRearSeatDemo()
                }
                DemoCaseButton("확장 B: 적재 중 급감속 (음성 보호 가이드)") {
                    viewModel.runExtensionHarshDrivingDemo()
                }
                DemoCaseButton("확장 C: 창문 열림 상태로 주차 완료") {
                    viewModel.runExtensionOpenWindowExitDemo()
                }
                DemoCaseButton("확장 D: 마트 도착 시 장바구니 역제안") {
                    viewModel.runExtensionReverseReminderDemo()
                }
                DemoCaseButton("확장 E: 고온 경유 정차 시 신선식품 변질 경고") {
                    viewModel.runExtensionHighTempCargoDemo()
                }
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))

            plugins.forEach { plugin ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plugin.name,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = plugin.description,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = plugin.id in enabledIds,
                        onCheckedChange = { viewModel.togglePlugin(plugin.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventLogCard(eventLog: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "센서 · 자율주행 · AI 에이전트 실시간 로그 (출처 구분)",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (eventLog.isEmpty()) {
                Text(
                    text = "우측 시연 버튼을 눌러 센서/자율주행/AI 이벤트를 발생시켜 보세요.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                eventLog.take(8).forEach { line ->
                    Text(
                        text = line,
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoCaseButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26334D)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmpiricalJourneyPlayerCard(
    isReplaying: Boolean,
    replayProgress: Int,
    replayStageTitle: String?,
    onStartReplay: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardHighlight),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isReplaying) AccentAmber else AccentBlue, CircleShape)
                    )
                    Text(
                        text = "실증 주행 여정 자동 리플레이어",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStartReplay,
                    enabled = !isReplaying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        disabledContainerColor = AccentBlue.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isReplaying) "시뮬레이션 진행 중..." else "▶ 실증 주행 시뮬레이션 재생",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isReplaying || replayProgress > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = replayStageTitle ?: "출발 준비 완료",
                            color = AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$replayProgress%",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { replayProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = AccentAmber,
                        trackColor = Color(0xFF0F172A)
                    )
                }
            } else {
                Text(
                    text = "출발지 적재 → AI 음성 분석 → 주행(Lv.3 대기) → 도착 판정 → P단 하차 브리핑까지 전 과정을 1클릭으로 연속 시연합니다.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun TripTimelineSummaryCard(
    timelineSteps: List<TripTimelineStep>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "주행 맥락 타임라인 (Timeline Steps)",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${timelineSteps.size}개 이벤트 누적",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (timelineSteps.isEmpty()) {
                Text(
                    text = "아직 기록된 이동 타임라인이 없습니다. 트렁크를 개폐하거나 주행을 시작하면 인과관계가 자동 기록됩니다.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timelineSteps.takeLast(6).reversed().forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (step.isHighlight) CardHighlight else Color(0xFF131A29),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (step.isHighlight) AccentAmber.copy(alpha = 0.5f) else Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = if (step.isHighlight) AccentAmber.copy(alpha = 0.2f) else AccentBlue.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = step.phaseLabel,
                                    color = if (step.isHighlight) AccentAmber else AccentBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step.detail,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = step.origin.displayName,
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
