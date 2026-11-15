package com.pleos.vehiclecontext.ui.user

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.voice.VoiceInputUiState
import com.pleos.vehiclecontext.gleo.GleoDraft
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 모던 차량용 콕핏 하단 고정 플로팅 제어 덱 (Floating Action Deck)
 * - 슬림 88dp 높이로 화면 잘림(Clipping)을 원천 방지
 * - 알람 본질 집중: 잡다한 시스템 문구를 제거하고 오직 "트렁크 짐 챙기기"에만 100% 집중
 * - 평상시: [🎙️ 음성 화물 등록] 원터치 마이크 바 및 4대 퀵 프리셋
 */
@Composable
fun CockpitFloatingActionDeck(
    activePrompt: ContextAction?,
    onExecuteControl: (VehicleControlCommand) -> Unit,
    onAcknowledgePrompt: () -> Unit,
    onApplyPresetMart: () -> Unit,
    onApplyPresetCake: () -> Unit,
    onApplyPresetStroller: () -> Unit,
    onApplyPresetGolfBag: () -> Unit,
    onClearTrunk: () -> Unit,
    onSubmitVoiceNote: (String) -> Unit,
    onOpenManualAdd: () -> Unit = {},
    voiceState: VoiceInputUiState = VoiceInputUiState(),
    gleoDraft: GleoDraft? = null,
    onStartVoiceRegistration: (() -> Unit)? = null,
    onStopVoiceRegistration: () -> Unit = {},
    onSelectVoiceQuantity: (Int) -> Unit = {},
    onConfirmGleo: (String) -> Unit = {},
    onCancelGleo: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceRegistration?.invoke()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onSubmitVoiceNote(spoken)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hmi.actionDeckHeight)
            .background(colors.surface)
    ) {
        // Top Subtle Divider Line
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.surfaceBorderSubtle)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hmi.panelPadding, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val deckMode = when {
                gleoDraft != null -> 2
                activePrompt != null -> 1
                else -> 0
            }
            AnimatedContent(
                targetState = deckMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "ActionDeckTransition"
            ) { mode ->
                when {
                    mode == 2 && gleoDraft != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = hmi.itemSpacing),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🤖 ${gleoDraft.title} 요청",
                                    color = colors.primary,
                                    fontSize = hmi.subheadFontSize,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = gleoDraft.details,
                                    color = colors.textPrimary,
                                    fontSize = hmi.bodyFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onConfirmGleo(gleoDraft.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(hmi.chipCornerRadius),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = hmi.minTouchTarget)
                                ) {
                                    Text("확인 저장", fontSize = hmi.bodyFontSize, fontWeight = FontWeight.Black)
                                }
                                Button(
                                    onClick = { onCancelGleo(gleoDraft.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.surfaceSecondary,
                                        contentColor = colors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(hmi.chipCornerRadius),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = hmi.minTouchTarget)
                                        .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                                ) {
                                    Text("취소", fontSize = hmi.bodyFontSize, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    mode == 1 && activePrompt != null -> {
                        // =========================================================
                        // Mode A: Focus Alert - 트렁크 짐 챙기기 본질에만 100% 집중
                        // =========================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = hmi.itemSpacing),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🔔 트렁크에 실린 짐을 잊지 말고 챙기세요!",
                                    color = colors.accentAmber,
                                    fontSize = hmi.subheadFontSize,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = activePrompt.bodyMessage,
                                        color = colors.textPrimary,
                                        fontSize = hmi.bodyFontSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (!hmi.isCompactHeader) {
                                        Text(
                                            text = "· 차에 계속 보관할 짐인가요?",
                                            color = colors.primary,
                                            fontSize = hmi.captionFontSize,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                onExecuteControl(VehicleControlCommand.KEEP_IN_TRUNK_PERMANENTLY)
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onExecuteControl(VehicleControlCommand.OPEN_TRUNK) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(hmi.chipCornerRadius),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = hmi.minTouchTarget)
                                ) {
                                    Text(
                                        text = "🚗 트렁크 열기",
                                        fontSize = hmi.bodyFontSize,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Button(
                                    onClick = onAcknowledgePrompt,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.surfaceSecondary,
                                        contentColor = colors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(hmi.chipCornerRadius),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = hmi.minTouchTarget)
                                        .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                                ) {
                                    Text(
                                        text = "짐 꺼냄 (확인)",
                                        fontSize = hmi.bodyFontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // =========================================================
                        // Mode B: Standby Quick Voice STT & 4-Tap Presets Bar
                        // =========================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Voice Mic Bar
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .defaultMinSize(minHeight = hmi.minTouchTarget)
                                    .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                    .background(if (voiceState.active) colors.primaryContainer else colors.surfaceSecondary)
                                    .border(
                                        1.dp,
                                        if (voiceState.active) colors.primary else Color.Transparent,
                                        RoundedCornerShape(hmi.chipCornerRadius)
                                    )
                                    .clickable {
                                        if (voiceState.active) {
                                            onStopVoiceRegistration()
                                        } else if (onStartVoiceRegistration != null) {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                onStartVoiceRegistration()
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        } else {
                                            runCatching {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(
                                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                    )
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "적재한 화물을 말씀하세요")
                                                }
                                                speechLauncher.launch(intent)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = if (voiceState.listening) "🟢" else "🎙️", fontSize = hmi.iconLargeSize)
                                Column {
                                    Text(
                                        text = when {
                                            voiceState.active -> "${voiceState.question?.label ?: "음성"} 입력 중 (중단)"
                                            hmi.isCompactHeader -> "음성 등록"
                                            else -> "말해서 화물 등록"
                                        },
                                        color = colors.textPrimary,
                                        fontSize = hmi.subheadFontSize,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!hmi.isCompactHeader) {
                                        Text(
                                            text = if (voiceState.active) voiceState.status else "AI가 신선식품/파손주의 자동 분류",
                                            color = if (voiceState.active) colors.primary else colors.textTertiary,
                                            fontSize = hmi.captionFontSize,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (voiceState.active && voiceState.needsQuantityChoice) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    (1..5).forEach { count ->
                                        Box(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = hmi.minTouchTarget, minHeight = hmi.minTouchTarget)
                                                .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                                .background(colors.primaryContainer)
                                                .border(1.dp, colors.primary, RoundedCornerShape(hmi.chipCornerRadius))
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
                            } else {
                                // ➕ 직접 추가 (AAOS 팝업 열기)
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = hmi.minTouchTarget)
                                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                        .background(colors.primaryContainer)
                                        .border(1.5.dp, colors.primary, RoundedCornerShape(hmi.chipCornerRadius))
                                        .clickable(onClick = onOpenManualAdd)
                                        .padding(horizontal = if (hmi.isCompactHeader) 14.dp else 20.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "➕",
                                            color = colors.primary,
                                            fontSize = hmi.iconMediumSize
                                        )
                                        Text(
                                            text = "직접 추가",
                                            color = colors.primary,
                                            fontSize = hmi.bodyFontSize,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // 4-Preset Quick Chips (기존 1탭 즉시 적재 유지)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing / 2 + 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DeckPresetChip(icon = "🛒", label = "마트", onClick = onApplyPresetMart)
                                    DeckPresetChip(icon = "🎂", label = "케이크", onClick = onApplyPresetCake)
                                    DeckPresetChip(icon = "👶", label = "유아차", onClick = onApplyPresetStroller)
                                    DeckPresetChip(icon = "⛳", label = "골프백", onClick = onApplyPresetGolfBag)
                                }

                                // Clear Trunk
                                if (!hmi.isCompactHeader) {
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = hmi.minTouchTarget)
                                            .clickable(onClick = onClearTrunk)
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🗑️ 비우기",
                                            color = colors.textTertiary,
                                            fontSize = hmi.bodyFontSize,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckPresetChip(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = hmi.minTouchTarget)
            .clip(RoundedCornerShape(hmi.chipCornerRadius))
            .background(colors.surfaceSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = if (hmi.isCompactHeader) 14.dp else 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = icon, fontSize = hmi.iconMediumSize)
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = hmi.bodyFontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "Floating Action Deck - Standby Dark", widthDp = 940, heightDp = 88)
@Composable
fun CockpitFloatingActionDeckPreviewDark() {
    PleosTheme(darkTheme = true) {
        CockpitFloatingActionDeck(
            activePrompt = null,
            onExecuteControl = {},
            onAcknowledgePrompt = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Preview(name = "Floating Action Deck - Prompt Light", widthDp = 940, heightDp = 88)
@Composable
fun CockpitFloatingActionDeckPreviewLight() {
    PleosTheme(darkTheme = false) {
        val dummyPrompt = ContextAction(
            scenarioId = "PROMPT_TRUNK_ARRIVAL",
            scenarioTitle = "트렁크 하차 케어",
            headline = "트렁크에 실린 짐을 잊지 말고 챙기세요!",
            bodyMessage = "출발 전 실으신 [이마트 장본 짐]이 보관되어 있습니다.",
            contextReasonBadge = "도착 + P단 주차 감지",
            signalOrigin = com.pleos.vehiclecontext.core.model.SignalOrigin.REAL_SDK,
            voiceTtsMessage = "목적지에 도착했습니다.",
            primaryAckLabel = "확인 완료"
        )
        CockpitFloatingActionDeck(
            activePrompt = dummyPrompt,
            onExecuteControl = {},
            onAcknowledgePrompt = {},
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}
