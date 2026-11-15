package com.pleos.vehiclecontext.ui.user

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pleos.vehiclecontext.core.voice.VoiceInputUiState
import com.pleos.vehiclecontext.ui.model.FreshnessCareStatus
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 운전자 핵심 Action 카드: 퀵 화물 관리 & 원터치 AI 음성 등록 위젯
 * - 현대차 ccNC 스타일의 정제된 글래스 서피스
 * - 4대 원터치 프리셋 (마트 장보기, 케이크·와인, 유아차, 골프백)
 * - 신선도 쾌속 모니터링 칩 및 서브 뎁스 네비게이션 연동
 * - 충분한 글자 크기(15sp+) 및 52dp+ 대형 터치 타깃
 */
@Composable
fun QuickCargoActionsWidget(
    onApplyPresetMart: () -> Unit,
    onApplyPresetCake: () -> Unit,
    onApplyPresetStroller: () -> Unit,
    onApplyPresetGolfBag: () -> Unit,
    onClearTrunk: () -> Unit,
    onSubmitVoiceNote: (String) -> Unit = {},
    freshness: FreshnessCareStatus? = null,
    onNavigateToFreshness: () -> Unit = {},
    voiceState: VoiceInputUiState = VoiceInputUiState(),
    onStartGuidedVoice: (() -> Unit)? = null,
    onStopVoice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartGuidedVoice?.invoke()
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

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(hmi.cardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.cardCornerRadius))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(hmi.cardPadding),
            verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
        ) {
            // Header Row: Title & Action Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.6f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.accentAmberContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "QUICK ACTIONS",
                            color = colors.accentAmber,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "화물 등록 & 프리셋",
                        color = colors.textPrimary,
                        fontSize = hmi.subheadFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Freshness quick chip if available
                if (freshness != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = hmi.minTouchTarget * 0.85f)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius * 0.7f))
                            .clickable { onNavigateToFreshness() }
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius * 0.7f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "❄️ ${"%.1f".format(freshness.currentTempCelsius)}℃ 케어 중 >",
                            color = colors.primary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Primary Voice Assistant Mic Button (Refined Luxury Surface)
            Button(
                onClick = {
                    if (voiceState.active) {
                        onStopVoice()
                    } else if (onStartGuidedVoice != null) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onStartGuidedVoice()
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
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "적재한 화물을 말씀하세요 (예: 케이크, 마트 장보기)")
                            }
                            speechLauncher.launch(intent)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (voiceState.active) colors.primaryContainer else colors.surfaceSecondary,
                    contentColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(hmi.chipCornerRadius),
                contentPadding = PaddingValues(horizontal = hmi.itemSpacing, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = hmi.minTouchTarget)
                    .border(
                        1.5.dp,
                        if (voiceState.active) colors.primary else colors.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(hmi.chipCornerRadius)
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.85f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.primaryContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.75f))
                            .padding(8.dp)
                    ) {
                        Text(text = if (voiceState.listening) "🟢" else "🎙️", fontSize = hmi.iconMediumSize)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (voiceState.active) {
                                "${voiceState.question?.label ?: "음성"} 입력 진행 중"
                            } else {
                                "음성으로 화물 등록 (말해서 짐 추가)"
                            },
                            color = colors.textPrimary,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (voiceState.active) {
                                voiceState.status
                            } else {
                                "항목·개수·목적지를 한 번에 말하면 못 알아들은 정보만 다시 물어봅니다"
                            },
                            color = if (voiceState.active) colors.primary else colors.textTertiary,
                            fontSize = hmi.badgeFontSize
                        )
                    }
                    Text(
                        text = if (voiceState.active) "중단 ✕" else "말하기 >",
                        color = colors.primary,
                        fontSize = hmi.captionFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 1-Tap Presets 2x2 Grid (Generous touch targets & legible fonts)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.65f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.65f)
                ) {
                    RichPresetButton(
                        icon = "🛒",
                        title = "마트 장보기",
                        tag = "신선식품",
                        tagColor = colors.accentGreen,
                        onClick = onApplyPresetMart,
                        modifier = Modifier.weight(1f)
                    )
                    RichPresetButton(
                        icon = "🎂",
                        title = "케이크·와인",
                        tag = "파손주의",
                        tagColor = colors.accentPurple,
                        onClick = onApplyPresetCake,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.65f)
                ) {
                    RichPresetButton(
                        icon = "👶",
                        title = "유아차",
                        tag = "동승자 케어",
                        tagColor = colors.primary,
                        onClick = onApplyPresetStroller,
                        modifier = Modifier.weight(1f)
                    )
                    RichPresetButton(
                        icon = "⛳",
                        title = "골프백",
                        tag = "상시보관",
                        tagColor = colors.accentAmber,
                        onClick = onApplyPresetGolfBag,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Sub Action: Clear Trunk or Manage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = hmi.minTouchTarget)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .clickable { onClearTrunk() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🗑️ 트렁크 비우기 (Clear Trunk)",
                        color = colors.textTertiary,
                        fontSize = hmi.badgeFontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RichPresetButton(
    icon: String,
    title: String,
    tag: String,
    tagColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(hmi.chipCornerRadius))
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
            .clickable { onClick() }
            .padding(horizontal = hmi.itemSpacing, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.7f)
        ) {
            Text(text = icon, fontSize = hmi.iconMediumSize)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = hmi.bodyFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = tag,
                    color = tagColor,
                    fontSize = hmi.badgeFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (다크 & 라이트 모드)
// =========================================================================

@Preview(name = "Quick Actions Dark (520x280)", widthDp = 520, heightDp = 280, showBackground = true)
@Composable
fun QuickCargoActionsWidgetPreviewDark() {
    PleosTheme(darkTheme = true) {
        QuickCargoActionsWidget(
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}

@Preview(name = "Quick Actions Light (520x280)", widthDp = 520, heightDp = 280, showBackground = true)
@Composable
fun QuickCargoActionsWidgetPreviewLight() {
    PleosTheme(darkTheme = false) {
        QuickCargoActionsWidget(
            onApplyPresetMart = {},
            onApplyPresetCake = {},
            onApplyPresetStroller = {},
            onApplyPresetGolfBag = {},
            onClearTrunk = {},
            onSubmitVoiceNote = {}
        )
    }
}
