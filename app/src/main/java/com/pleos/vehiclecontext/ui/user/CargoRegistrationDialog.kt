package com.pleos.vehiclecontext.ui.user

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pleos.vehiclecontext.core.location.VehicleLocationResolver
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.CargoItemRecord
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.voice.VoiceInputUiState
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 팝업 내부에서 1탭으로 폼을 채우거나 즉시 등록할 수 있는 화물 프리셋 정의
 */
private data class PopupCargoPreset(
    val key: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val itemName: String,
    val quantity: Int,
    val purpose: String,
    val isPerishable: Boolean,
    val isPermanent: Boolean,
    val countAsBundle: Boolean
)

private val POPUP_CARGO_PRESETS = listOf(
    PopupCargoPreset(
        key = VehicleLocationResolver.PRESET_MART,
        icon = "🛒",
        title = "마트 장보기",
        subtitle = "신선식품 · 묶음 · 냉기케어",
        itemName = "이마트 장본 신선식품 · 아이스크림",
        quantity = 1,
        purpose = "마트 장보기",
        isPerishable = true,
        isPermanent = false,
        countAsBundle = true
    ),
    PopupCargoPreset(
        key = VehicleLocationResolver.PRESET_CAKE,
        icon = "🎂",
        title = "케이크 · 와인",
        subtitle = "파손주의 · 부드러운 주행",
        itemName = "생일 케이크 & 와인",
        quantity = 1,
        purpose = "파손주의 · 기념일",
        isPerishable = true,
        isPermanent = false,
        countAsBundle = false
    ),
    PopupCargoPreset(
        key = VehicleLocationResolver.PRESET_STROLLER,
        icon = "👶",
        title = "유아차",
        subtitle = "동승자 케어 · 목적지 알림",
        itemName = "유아차 (절충형)",
        quantity = 1,
        purpose = "아기 동승 나들이",
        isPerishable = false,
        isPermanent = false,
        countAsBundle = false
    ),
    PopupCargoPreset(
        key = VehicleLocationResolver.PRESET_GOLFBAG,
        icon = "⛳",
        title = "골프백",
        subtitle = "트렁크 상시 보관 등록",
        itemName = "골프백",
        quantity = 1,
        purpose = "상시 보관 장비",
        isPerishable = false,
        isPermanent = true,
        countAsBundle = false
    )
)

/**
 * AAOS 표준(터치 타겟 72dp+, 고대비 타이포, 주행 중 입력 가드)을 준수한 대형 화물 등록 팝업.
 * - 하단 플로팅 액션 덱의 '직접 추가' 버튼 또는 트렁크 닫힘 자동 감지로 열립니다.
 * - 출발지는 내비 출발지 또는 GPS/지도 현위치로, 도착지는 활성 내비 목적지로 자동 최적화됩니다.
 * - 나머지 항목(품목/목적/수량/태그)은 프리셋 원터치 선택 또는 음성/직접 입력으로 채울 수 있습니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CargoRegistrationDialog(
    session: TripSession,
    voiceState: VoiceInputUiState,
    onRefreshLocationContext: () -> Unit,
    onConfirm: (
        name: String,
        quantity: Int,
        purpose: String,
        originName: String?,
        targetDestinationName: String?,
        countAsBundle: Boolean,
        isPerishable: Boolean,
        isPermanent: Boolean
    ) -> Unit,
    onSkip: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onSelectVoiceQuantity: (Int) -> Unit,
    editingItem: CargoItemRecord? = null
) {
    val context = LocalContext.current
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val keyboardController = LocalSoftwareKeyboardController.current

    val isEditMode = editingItem != null
    val requestId = editingItem?.id ?: session.pendingLoadingPromptRequestId ?: "manual"
    val isTrunkAutoDetected = !isEditMode && requestId.startsWith("load-")
    val isDrivingRestricted = session.currentGear != GearState.PARK &&
        session.autonomousMode == AutonomousDrivingMode.MANUAL

    val autoOrigin = remember(session.originName, session.originPoi, session.originAddress) {
        session.originName.trim().ifBlank {
            session.originPoi.takeIf { it != PoiCategory.UNKNOWN }?.displayName ?: ""
        }
    }
    val autoDest = remember(session.destinationName) {
        session.destinationName.trim().ifBlank { "집 (성수동 자택)" }
    }

    var itemName by remember(requestId) { mutableStateOf(editingItem?.name.orEmpty()) }
    var quantity by remember(requestId) { mutableIntStateOf((editingItem?.remainingQuantity ?: editingItem?.quantity ?: 1).coerceAtLeast(1)) }
    var purpose by remember(requestId) { mutableStateOf(editingItem?.purpose.orEmpty()) }
    var originPlace by remember(requestId) { mutableStateOf(editingItem?.originName ?: autoOrigin) }
    var originEditedByUser by remember(requestId) { mutableStateOf(!editingItem?.originName.isNullOrBlank()) }
    var targetPlace by remember(requestId) { mutableStateOf(editingItem?.targetDestinationName ?: autoDest) }
    var targetEditedByUser by remember(requestId) { mutableStateOf(!editingItem?.targetDestinationName.isNullOrBlank()) }
    var keepInCar by remember(requestId) { mutableStateOf(editingItem?.isPermanent ?: false) }
    var countAsBundle by remember(requestId) { mutableStateOf(editingItem?.countAsBundle ?: false) }
    var isPerishable by remember(requestId) { mutableStateOf(editingItem?.isPerishable ?: false) }
    var selectedPresetKey by remember(requestId) { mutableStateOf<String?>(null) }
    var showValidationError by remember(requestId) { mutableStateOf(false) }

    // 비동기 GPS/내비 위치 또는 목적지 응답이 팝업 열린 상태에서 늦게 도착하면 사용자가 직접 고치기 전까지는 자동 동기화
    LaunchedEffect(autoOrigin) {
        if (!originEditedByUser && autoOrigin.isNotBlank()) {
            originPlace = autoOrigin
        }
    }
    LaunchedEffect(autoDest) {
        if (!targetEditedByUser && !keepInCar && autoDest.isNotBlank()) {
            targetPlace = autoDest
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoice()
        }
    }

    val applyPresetToForm: (PopupCargoPreset, Boolean) -> Unit = { preset, registerImmediately ->
        itemName = preset.itemName
        quantity = preset.quantity
        purpose = preset.purpose
        isPerishable = preset.isPerishable
        keepInCar = preset.isPermanent
        countAsBundle = preset.countAsBundle
        selectedPresetKey = preset.key
        showValidationError = false
        if (registerImmediately && !isDrivingRestricted) {
            keyboardController?.hide()
            if (voiceState.active) onStopVoice()
            onConfirm(
                preset.itemName,
                preset.quantity,
                preset.purpose,
                originPlace.trim().ifBlank { null },
                if (preset.isPermanent) null else targetPlace.trim().ifBlank { autoDest },
                preset.countAsBundle,
                preset.isPerishable,
                preset.isPermanent
            )
        }
    }

    val originQuickOptions = remember(
        autoOrigin,
        session.originSuggestions,
        session.originPoi
    ) {
        buildList {
            if (autoOrigin.isNotBlank()) add(autoOrigin)
            addAll(session.originSuggestions)
            add("이마트")
            add("집 (성수동 자택)")
            add("회사")
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
    }

    val destQuickOptions = remember(autoDest, session.destinationSuggestions) {
        buildList {
            if (autoDest.isNotBlank()) add(autoDest)
            addAll(session.destinationSuggestions)
            add("집 (성수동 자택)")
            add("회사")
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
    }

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .widthIn(max = 1880.dp)
                .heightIn(max = 1040.dp),
            shape = RoundedCornerShape(hmi.cardCornerRadius + 14.dp),
            color = colors.surface,
            border = BorderStroke(1.5.dp, colors.surfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hmi.panelPadding + 4.dp, vertical = hmi.panelPadding)
            ) {
                // =========================================================================
                // 1. AAOS Popup Header: Trigger Mode + Live GPS/Navi Summary
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isTrunkAutoDetected || isEditMode) colors.primaryContainer else colors.surfaceSecondary
                                    )
                                    .border(
                                        1.dp,
                                        if (isTrunkAutoDetected || isEditMode) colors.primary else colors.surfaceBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = when {
                                        isEditMode -> "✏️ 화물 정보 수정"
                                        isTrunkAutoDetected -> "🚘 트렁크 닫힘 자동 감지"
                                        else -> "➕ 화물 직접 등록"
                                    },
                                    color = if (isTrunkAutoDetected || isEditMode) colors.primary else colors.textPrimary,
                                    fontSize = hmi.captionFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceSecondary)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                val originLabel = originPlace.ifBlank { "현위치 확인 중" }
                                val destLabel = if (keepInCar) "상시 보관" else targetPlace.ifBlank { autoDest }
                                Text(
                                    text = "📍 $originLabel ➔ 🏁 $destLabel",
                                    color = colors.textSecondary,
                                    fontSize = hmi.captionFontSize,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = if (isEditMode) "트렁크 적재 화물 수정" else "트렁크 적재 화물 등록",
                            color = colors.textPrimary,
                            fontSize = hmi.headlineFontSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // GPS & 내비 위치 재동기화 버튼
                        OutlinedButton(
                            onClick = {
                                originEditedByUser = false
                                targetEditedByUser = false
                                onRefreshLocationContext()
                            },
                            shape = RoundedCornerShape(hmi.chipCornerRadius),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.defaultMinSize(minHeight = hmi.minTouchTarget)
                        ) {
                            Text(
                                text = "📡 위치·내비 동기화",
                                color = colors.textPrimary,
                                fontSize = hmi.bodyFontSize,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 닫기 버튼
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = hmi.minTouchTarget, minHeight = hmi.minTouchTarget)
                                .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                .background(colors.surfaceSecondary)
                                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                                .clickable(onClick = {
                                    if (voiceState.active) onStopVoice()
                                    onSkip()
                                })
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = colors.textPrimary,
                                fontSize = hmi.titleFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // =========================================================================
                // 2. AAOS Parked Safety Notice or Live Voice Guidance Banner
                // =========================================================================
                if (isDrivingRestricted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .background(colors.accentAmberContainer)
                            .border(1.dp, colors.accentAmber, RoundedCornerShape(hmi.chipCornerRadius))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "⚠️", fontSize = hmi.iconMediumSize)
                        Text(
                            text = "AAOS 주행 안전 규격: 주행 중(D단) 키보드 입력은 제한됩니다. 음성으로 말하거나 P단 정차 후 등록하세요.",
                            color = colors.accentAmber,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (voiceState.active || !voiceState.lastError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .background(
                                if (!voiceState.lastError.isNullOrBlank() && !voiceState.active) {
                                    colors.accentRedContainer
                                } else {
                                    colors.primaryContainer
                                }
                            )
                            .border(
                                1.dp,
                                if (!voiceState.lastError.isNullOrBlank() && !voiceState.active) {
                                    colors.accentRed
                                } else {
                                    colors.primary
                                },
                                RoundedCornerShape(hmi.chipCornerRadius)
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (voiceState.listening) "🟢" else "🎙️",
                                fontSize = hmi.iconMediumSize
                            )
                            Text(
                                text = voiceState.lastError?.takeIf { !voiceState.active }
                                    ?: "${voiceState.question?.label ?: "음성"} · ${voiceState.status}",
                                color = colors.textPrimary,
                                fontSize = hmi.bodyFontSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (voiceState.active && voiceState.needsQuantityChoice) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..5).forEach { count ->
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.primary, RoundedCornerShape(10.dp))
                                            .clickable { onSelectVoiceQuantity(count) }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${count}개",
                                            color = colors.primary,
                                            fontSize = hmi.bodyFontSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else if (voiceState.active) {
                            OutlinedButton(
                                onClick = onStopVoice,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                            ) {
                                Text(
                                    text = "음성 중지",
                                    color = colors.textPrimary,
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =========================================================================
                // 3. Main 2-Column Cockpit Workspace (Presets/Voice Left + Structured Form Right)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(hmi.sectionSpacing)
                ) {
                    // [Left Column 38%] Presets + GPS/Location Recommendations + Voice Trigger
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(hmi.cardCornerRadius))
                            .background(colors.background)
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
                            .padding(hmi.panelPadding)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "원터치 프리셋 채우기",
                            color = colors.textPrimary,
                            fontSize = hmi.titleFontSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "선택하면 우측 폼이 자동 입력됩니다. 출발지/도착지는 현재 GPS·내비에 맞춰 유지됩니다.",
                            color = colors.textSecondary,
                            fontSize = hmi.captionFontSize
                        )

                        POPUP_CARGO_PRESETS.forEach { preset ->
                            val isSelected = selectedPresetKey == preset.key
                            val isGpsRecommended = session.suggestedPresetKey == preset.key
                            Surface(
                                shape = RoundedCornerShape(hmi.chipCornerRadius),
                                color = when {
                                    isSelected -> colors.primaryContainer
                                    else -> colors.surface
                                },
                                border = BorderStroke(
                                    width = if (isSelected || isGpsRecommended) 1.5.dp else 1.dp,
                                    color = when {
                                        isSelected -> colors.primary
                                        isGpsRecommended -> colors.accentGreen
                                        else -> colors.surfaceBorder
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = hmi.minTouchTarget + 6.dp)
                                    .clickable(enabled = !isDrivingRestricted) {
                                        applyPresetToForm(preset, false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(text = preset.icon, fontSize = hmi.iconLargeSize)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = preset.title,
                                                color = colors.textPrimary,
                                                fontSize = hmi.subheadFontSize,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isGpsRecommended) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(colors.accentGreenContainer)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "위치 추천",
                                                        color = colors.accentGreen,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = preset.subtitle,
                                            color = colors.textTertiary,
                                            fontSize = hmi.captionFontSize,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.primary.copy(alpha = 0.14f))
                                            .clickable(enabled = !isDrivingRestricted) {
                                                applyPresetToForm(preset, true)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "바로 등록",
                                            color = colors.primary,
                                            fontSize = hmi.captionFontSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 음성 입력 보조 카드
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = hmi.minTouchTarget)
                                .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                .background(if (voiceState.active) colors.primaryContainer else colors.surfaceSecondary)
                                .border(
                                    1.dp,
                                    if (voiceState.active) colors.primary else colors.surfaceBorder,
                                    RoundedCornerShape(hmi.chipCornerRadius)
                                )
                                .clickable {
                                    if (voiceState.active) {
                                        onStopVoice()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onStartVoice()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (voiceState.active) "🟢" else "🎙️",
                                    fontSize = hmi.iconLargeSize
                                )
                                Column {
                                    Text(
                                        text = if (voiceState.active) "음성 인식 중 (눌러서 중단)" else "음성으로 한번에 입력",
                                        color = colors.textPrimary,
                                        fontSize = hmi.subheadFontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "예: \"생수 2묶음, 이마트에서 집으로\"",
                                        color = colors.textTertiary,
                                        fontSize = hmi.captionFontSize
                                    )
                                }
                            }
                        }
                    }

                    // [Right Column 62%] Structured Cargo Fields (Origin, Destination, Item, Qty, Purpose, Tags)
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(hmi.cardCornerRadius))
                            .background(colors.background)
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
                            .padding(hmi.panelPadding)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. 출발지 (내비 출발지 > 현재 GPS/지도 위치 자동 최적화)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "출발지 (${session.originSourceLabel})",
                                    color = colors.textSecondary,
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (session.originAddress.isNotBlank()) {
                                    Text(
                                        text = session.originAddress,
                                        color = colors.textTertiary,
                                        fontSize = hmi.captionFontSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 360.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = originPlace,
                                onValueChange = {
                                    originPlace = it
                                    originEditedByUser = true
                                },
                                enabled = !isDrivingRestricted,
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = "내비 출발지 또는 GPS 현위치 자동 입력",
                                        fontSize = hmi.bodyFontSize
                                    )
                                },
                                textStyle = TextStyle(
                                    fontFamily = PleosTheme.fontFamily,
                                    fontSize = hmi.subheadFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                ),
                                colors = aaOsTextFieldColors(),
                                shape = RoundedCornerShape(hmi.chipCornerRadius),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                originQuickOptions.forEach { candidate ->
                                    PopupSelectChip(
                                        label = if (candidate == autoOrigin && autoOrigin.isNotBlank()) {
                                            "📍 $candidate (자동)"
                                        } else {
                                            candidate
                                        },
                                        selected = originPlace == candidate,
                                        enabled = !isDrivingRestricted,
                                        onClick = {
                                            originPlace = candidate
                                            originEditedByUser = candidate != autoOrigin
                                        }
                                    )
                                }
                            }
                        }

                        // 2. 도착지 (내비 지정 목적지 자동 사용 + 상시보관 토글)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "도착지 / 내릴 장소 (내비 목적지 자동)",
                                    color = colors.textSecondary,
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                                PopupSelectChip(
                                    label = if (keepInCar) "🔒 차에 계속 보관 중" else "차에 계속 보관할 짐",
                                    selected = keepInCar,
                                    enabled = !isDrivingRestricted,
                                    onClick = { keepInCar = !keepInCar }
                                )
                            }

                            OutlinedTextField(
                                value = if (keepInCar) "차량 상시 보관 (목적지 미지정)" else targetPlace,
                                onValueChange = {
                                    targetPlace = it
                                    targetEditedByUser = true
                                },
                                enabled = !isDrivingRestricted && !keepInCar,
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = "내비 목적지: $autoDest",
                                        fontSize = hmi.bodyFontSize
                                    )
                                },
                                textStyle = TextStyle(
                                    fontFamily = PleosTheme.fontFamily,
                                    fontSize = hmi.subheadFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                ),
                                colors = aaOsTextFieldColors(),
                                shape = RoundedCornerShape(hmi.chipCornerRadius),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!keepInCar) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    destQuickOptions.forEach { candidate ->
                                        PopupSelectChip(
                                            label = if (candidate == autoDest) {
                                                "🏁 내비: $candidate"
                                            } else {
                                                candidate
                                            },
                                            selected = targetPlace == candidate,
                                            enabled = !isDrivingRestricted,
                                            onClick = {
                                                targetPlace = candidate
                                                targetEditedByUser = candidate != autoDest
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. 품목명 & 보관 특성 태그
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "실은 짐 / 품목명 *",
                                color = colors.textSecondary,
                                fontSize = hmi.bodyFontSize,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = {
                                    itemName = it
                                    showValidationError = false
                                },
                                enabled = !isDrivingRestricted,
                                isError = showValidationError && itemName.isBlank(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = "예: 생수 2묶음, 아기 유아차, 캠핑 쿨러",
                                        fontSize = hmi.bodyFontSize
                                    )
                                },
                                textStyle = TextStyle(
                                    fontFamily = PleosTheme.fontFamily,
                                    fontSize = hmi.subheadFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                                colors = aaOsTextFieldColors(),
                                shape = RoundedCornerShape(hmi.chipCornerRadius),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (showValidationError && itemName.isBlank()) {
                                Text(
                                    text = "품목 이름을 입력하거나 좌측 프리셋을 선택해 주세요.",
                                    color = colors.accentRed,
                                    fontSize = hmi.captionFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PopupSelectChip(
                                    label = if (isPerishable) "❄️ 신선/냉동 보관 중" else "❄️ 신선/냉동 식품",
                                    selected = isPerishable,
                                    enabled = !isDrivingRestricted,
                                    onClick = { isPerishable = !isPerishable }
                                )
                                PopupSelectChip(
                                    label = if (countAsBundle) "🛒 쇼핑 묶음 (${quantity}묶음)" else "🛒 묶음으로 묶기",
                                    selected = countAsBundle,
                                    enabled = !isDrivingRestricted,
                                    onClick = { countAsBundle = !countAsBundle }
                                )
                            }
                        }

                        // 4. 수량 스테퍼 & 사용 목적
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "수량",
                                    color = colors.textSecondary,
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = hmi.minTouchTarget, minHeight = hmi.minTouchTarget)
                                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                            .background(colors.surfaceSecondary)
                                            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                                            .clickable(enabled = !isDrivingRestricted && quantity > 1) {
                                                quantity = (quantity - 1).coerceAtLeast(1)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "−",
                                            color = if (quantity > 1) colors.textPrimary else colors.textTertiary,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 96.dp, minHeight = hmi.minTouchTarget)
                                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                            .background(colors.surface)
                                            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$quantity${if (countAsBundle) "묶음" else "개"}",
                                            color = colors.textPrimary,
                                            fontSize = hmi.subheadFontSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = hmi.minTouchTarget, minHeight = hmi.minTouchTarget)
                                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                            .background(colors.surfaceSecondary)
                                            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                                            .clickable(enabled = !isDrivingRestricted && quantity < 99) {
                                                quantity = (quantity + 1).coerceAtMost(99)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+",
                                            color = colors.textPrimary,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "용도 / 메모 (선택)",
                                    color = colors.textSecondary,
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = purpose,
                                    onValueChange = { purpose = it },
                                    enabled = !isDrivingRestricted,
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            text = "예: 본가 전달, 파손주의, 저녁 식사",
                                            fontSize = hmi.bodyFontSize
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = PleosTheme.fontFamily,
                                        fontSize = hmi.bodyFontSize,
                                        color = colors.textPrimary
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                                    colors = aaOsTextFieldColors(),
                                    shape = RoundedCornerShape(hmi.chipCornerRadius),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("장보기", "파손주의", "선물/전달", "가족 나들이", "업무 물품").forEach { tag ->
                                        PopupSelectChip(
                                            label = tag,
                                            selected = purpose == tag,
                                            enabled = !isDrivingRestricted,
                                            onClick = { purpose = if (purpose == tag) "" else tag }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =========================================================================
                // 4. Pinned Bottom AAOS CTA Bar (Min height 76dp+)
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (voiceState.active) onStopVoice()
                            onSkip()
                        },
                        shape = RoundedCornerShape(hmi.chipCornerRadius),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        modifier = Modifier
                            .weight(0.38f)
                            .defaultMinSize(minHeight = hmi.minTouchTarget + 4.dp)
                    ) {
                        Text(
                            text = if (isTrunkAutoDetected) "새로 실은 짐 없음 (닫기)" else "취소 (닫기)",
                            color = colors.textPrimary,
                            fontSize = hmi.subheadFontSize,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val trimmed = itemName.trim()
                            if (trimmed.isBlank()) {
                                showValidationError = true
                            } else {
                                keyboardController?.hide()
                                if (voiceState.active) onStopVoice()
                                onConfirm(
                                    trimmed,
                                    quantity,
                                    purpose.trim(),
                                    originPlace.trim().ifBlank { null },
                                    if (keepInCar) null else targetPlace.trim().ifBlank { autoDest },
                                    countAsBundle,
                                    isPerishable,
                                    keepInCar
                                )
                            }
                        },
                        enabled = !isDrivingRestricted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(hmi.chipCornerRadius),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                        modifier = Modifier
                            .weight(0.62f)
                            .defaultMinSize(minHeight = hmi.minTouchTarget + 4.dp)
                    ) {
                        Text(
                            text = when {
                                isEditMode -> "✅ 화물 정보 수정 완료"
                                keepInCar -> "🔒 상시 보관 화물로 등록 완료"
                                else -> "✅ '${targetPlace.ifBlank { autoDest }}' 도착 하차 알림 등록"
                            },
                            fontSize = hmi.subheadFontSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupSelectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(hmi.chipCornerRadius))
            .background(if (selected) colors.primaryContainer else colors.surfaceSecondary)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary else colors.surfaceBorder,
                shape = RoundedCornerShape(hmi.chipCornerRadius)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.primary else colors.textPrimary,
            fontSize = hmi.captionFontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun aaOsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PleosTheme.colors.primary,
    unfocusedBorderColor = PleosTheme.colors.surfaceBorder,
    focusedTextColor = PleosTheme.colors.textPrimary,
    unfocusedTextColor = PleosTheme.colors.textPrimary,
    focusedContainerColor = PleosTheme.colors.surface,
    unfocusedContainerColor = PleosTheme.colors.surface,
    cursorColor = PleosTheme.colors.primary
)

@Preview(name = "Cargo Registration Dialog Dark (2560x1440)", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun CargoRegistrationDialogPreviewDark() {
    PleosTheme(darkTheme = true) {
        CargoRegistrationDialog(
            session = TripSession(
                originName = "이마트 성수점",
                originAddress = "서울 성동구 뚝섬로 379",
                originSourceLabel = "GPS · 지도 장소 매칭",
                destinationName = "집 (성수동 자택)",
                originSuggestions = listOf("이마트 성수점", "회사"),
                destinationSuggestions = listOf("집 (성수동 자택)", "부모님댁"),
                suggestedPresetKey = VehicleLocationResolver.PRESET_MART,
                pendingLoadingPromptRequestId = "load-1"
            ),
            voiceState = VoiceInputUiState(),
            onRefreshLocationContext = {},
            onConfirm = { _, _, _, _, _, _, _, _ -> },
            onSkip = {},
            onStartVoice = {},
            onStopVoice = {},
            onSelectVoiceQuantity = {}
        )
    }
}

@Preview(name = "Cargo Registration Dialog Light (1024x600)", widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
private fun CargoRegistrationDialogPreviewLight() {
    PleosTheme(darkTheme = false) {
        CargoRegistrationDialog(
            session = TripSession(
                originName = "스타필드 하남",
                originSourceLabel = "내비 출발지",
                destinationName = "분당 본가",
                pendingLoadingPromptRequestId = "manual-1"
            ),
            voiceState = VoiceInputUiState(),
            onRefreshLocationContext = {},
            onConfirm = { _, _, _, _, _, _, _, _ -> },
            onSkip = {},
            onStartVoice = {},
            onStopVoice = {},
            onSelectVoiceQuantity = {}
        )
    }
}
