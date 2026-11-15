package com.pleos.vehiclecontext.ui.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.core.settings.AlertSensitivity
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.settings.ThemeMode
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 우측 와이드 스플릿 사이드 패널 모드:
 * - SETTINGS: 앱 및 차량 연동 설정 (알림 감도, 급속 냉방)
 * - USER_ACCOUNT: Pleos Connect 사용자 계정 및 디지털 키 동기화 상태
 */
enum class CockpitSidePanelMode {
    SETTINGS,
    USER_ACCOUNT
}

/**
 * 현대차 ccNC / 제네시스 스타일의 우측 와이드 스플릿 사이드 패널 (Split Side Panel)
 * - 화면 중앙을 가리는 모달 팝업 대신 대시보드 우측에서 스르륵 열리는 순정 차량용 HMI 패널
 * - 설정(⚙️) 및 사용자 프로필(👤) 탭 시 동일하게 스플릿 뷰로 매끄럽게 전환
 */
@Composable
fun CockpitSplitSidePanel(
    isOpen: Boolean,
    mode: CockpitSidePanelMode,
    onClose: () -> Unit,
    settings: AppSettings = AppSettings(),
    onUpdateSettings: (AppSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(250)) + fadeIn(tween(250)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(hmi.sidePanelWidth)
                .fillMaxHeight()
                .background(colors.surface)
                .border(width = 1.dp, color = colors.surfaceBorder)
                .padding(hmi.panelPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Panel Header & Close Button
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(hmi.sectionSpacing)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.75f)
                        ) {
                            Text(
                                text = if (mode == CockpitSidePanelMode.SETTINGS) "⚙️" else "👤",
                                fontSize = hmi.iconMediumSize
                            )
                            Text(
                                text = if (mode == CockpitSidePanelMode.SETTINGS) "차량 연동 설정" else "사용자 계정 프로필",
                                color = colors.textPrimary,
                                fontSize = hmi.titleFontSize,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Close Button (✕)
                        Box(
                            modifier = Modifier
                                .size(hmi.minTouchTarget)
                                .clip(CircleShape)
                                .background(colors.surfaceSecondary)
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = colors.textSecondary,
                                fontSize = hmi.iconSmallSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Content according to mode
                    when (mode) {
                        CockpitSidePanelMode.SETTINGS -> {
                            // =============================================
                            // [Mode A] Settings Content
                            // =============================================
                            Column(verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)) {
                                // 0. 화면 테마 (자동/주간/야간)
                                Column(verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.5f)) {
                                    Text(
                                        text = "화면 테마 모드",
                                        color = colors.textPrimary,
                                        fontSize = hmi.bodyFontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.5f)
                                    ) {
                                        listOf(
                                            ThemeMode.AUTO to "시간별 자동",
                                            ThemeMode.LIGHT to "주간",
                                            ThemeMode.DARK to "야간"
                                        ).forEach { (themeOption, label) ->
                                            val isSelected = settings.themeMode == themeOption
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = hmi.minTouchTarget)
                                                    .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceSecondary)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) colors.primary else colors.surfaceBorderSubtle,
                                                        RoundedCornerShape(hmi.chipCornerRadius)
                                                    )
                                                    .clickable {
                                                        onUpdateSettings(settings.copy(themeMode = themeOption))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = hmi.itemSpacing * 0.5f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) colors.primary else colors.textSecondary,
                                                    fontSize = hmi.badgeFontSize,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // 1. 하차 알림 감도
                                Column(verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.5f)) {
                                    Text(
                                        text = "하차 알림 발동 시점",
                                        color = colors.textPrimary,
                                        fontSize = hmi.bodyFontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.7f)
                                    ) {
                                        listOf(
                                            AlertSensitivity.PARK_STANDARD to "표준 (P단 주차)",
                                            AlertSensitivity.APPROACH_200M to "민감 (목적지 200m 전)"
                                        ).forEach { (sensitivity, option) ->
                                            val isSelected = settings.alertSensitivity == sensitivity
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = hmi.minTouchTarget)
                                                    .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceSecondary)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) colors.primary else colors.surfaceBorderSubtle,
                                                        RoundedCornerShape(hmi.chipCornerRadius)
                                                    )
                                                    .clickable {
                                                        onUpdateSettings(settings.copy(alertSensitivity = sensitivity))
                                                    }
                                                    .padding(horizontal = hmi.itemSpacing * 0.7f, vertical = hmi.itemSpacing * 0.6f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = option,
                                                    color = if (isSelected) colors.primary else colors.textSecondary,
                                                    fontSize = hmi.badgeFontSize,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. 트렁크 적재 자동 음성 입력
                                SettingToggleRow(
                                    title = "트렁크 닫힘 시 자동 음성 입력",
                                    description = "적재 감지 후 음성이 자동으로 실행되어 짐을 기록합니다",
                                    checked = settings.autoVoiceOnTrunkClose,
                                    onCheckedChange = {
                                        onUpdateSettings(settings.copy(autoVoiceOnTrunkClose = it))
                                    }
                                )

                                // 3. 음성 등록 시 사용 목적 묻기
                                SettingToggleRow(
                                    title = "음성 등록 시 사용 목적 묻기",
                                    description = "끄면 짐 이름 · 수량 · 내릴 장소만 빠르게 묻습니다",
                                    checked = settings.askPurposeOnVoice,
                                    onCheckedChange = {
                                        onUpdateSettings(settings.copy(askPurposeOnVoice = it))
                                    }
                                )

                                // 4. 쇼핑 품목 자동 묶음 등록
                                SettingToggleRow(
                                    title = "쇼핑 품목 자동 묶음 등록",
                                    description = "마트/쇼핑 목적지에서는 장보기 짐을 1묶음으로 요약",
                                    checked = settings.autoShoppingBundle,
                                    onCheckedChange = {
                                        onUpdateSettings(settings.copy(autoShoppingBundle = it))
                                    }
                                )

                                // 5. 신선식품 자동 급속 냉방 토글
                                SettingToggleRow(
                                    title = "신선식품 변질 방지 급속 냉방",
                                    description = "트렁크 25℃ 이상 시 20℃ 목표 자동 공조",
                                    checked = settings.autoCoolingEnabled,
                                    onCheckedChange = {
                                        onUpdateSettings(settings.copy(autoCoolingEnabled = it))
                                    }
                                )

                                // 6. 스마트폰 푸시 동시 브리핑 토글
                                SettingToggleRow(
                                    title = "스마트폰 동시 브리핑",
                                    description = "하차 후 트렁크 잔여 화물 폰 푸시 알림",
                                    checked = settings.smartphoneSyncEnabled,
                                    onCheckedChange = {
                                        onUpdateSettings(settings.copy(smartphoneSyncEnabled = it))
                                    }
                                )
                            }
                        }

                        CockpitSidePanelMode.USER_ACCOUNT -> {
                            // =============================================
                            // [Mode B] User Profile Account Content
                            // =============================================
                            Column(verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)) {
                                // Profile Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(hmi.cardCornerRadius))
                                        .background(colors.surfaceSecondary)
                                        .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
                                        .padding(hmi.cardPadding)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(hmi.minTouchTarget + 12.dp)
                                                .clip(CircleShape)
                                                .background(colors.primaryContainer)
                                                .border(2.dp, colors.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "👤", fontSize = hmi.iconLargeSize)
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "운전자 1",
                                                    color = colors.textPrimary,
                                                    fontSize = hmi.subheadFontSize,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .background(colors.accentGreenContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.6f))
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "연동 완료",
                                                        color = colors.accentGreen,
                                                        fontSize = hmi.badgeFontSize,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "driver.demo@players.example.com",
                                                color = colors.textSecondary,
                                                fontSize = hmi.captionFontSize
                                            )
                                        }
                                    }
                                }

                                // Vehicle Sync Status
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(hmi.cardCornerRadius))
                                        .background(colors.surfaceSecondary)
                                        .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
                                        .padding(hmi.cardPadding),
                                    verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.65f)
                                ) {
                                    Text(
                                        text = "연동 차량 및 디지털 키",
                                        color = colors.textPrimary,
                                        fontSize = hmi.bodyFontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                    AccountDetailRow("등록 차량", "현대 아이오닉 5 (2026)")
                                    AccountDetailRow("디지털 키 2", "활성화됨 (NFC/UWB 동기화)")
                                    AccountDetailRow("하차 브리핑", "차량 스피커 + 스마트폰 연동")
                                }
                            }
                        }
                    }
                }

                // Bottom: Platform Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.surfaceSecondary)
                        .padding(hmi.itemSpacing)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Team Players · Pleos Connect System",
                            color = colors.textSecondary,
                            fontSize = hmi.captionFontSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "실시간 차량 맥락 인텔리전스 폐루프 제어가 안전하게 동작 중입니다.",
                            color = colors.textTertiary,
                            fontSize = hmi.badgeFontSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetailRow(label: String, value: String) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textTertiary, fontSize = hmi.badgeFontSize)
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = hmi.captionFontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = hmi.minTouchTarget)
            .clip(RoundedCornerShape(hmi.chipCornerRadius))
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = hmi.itemSpacing, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = hmi.bodyFontSize,
                lineHeight = (hmi.bodyFontSize.value * 1.3f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                color = colors.textTertiary,
                fontSize = hmi.badgeFontSize,
                lineHeight = (hmi.badgeFontSize.value * 1.35f).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier.widthIn(min = 58.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.textTertiary,
                    uncheckedTrackColor = colors.surfaceBorder
                )
            )
        }
    }
}

@Preview(name = "Split Side Panel - Settings", widthDp = 360, heightDp = 500)
@Composable
fun CockpitSplitSidePanelPreviewSettings() {
    PleosTheme(darkTheme = true) {
        CockpitSplitSidePanel(
            isOpen = true,
            mode = CockpitSidePanelMode.SETTINGS,
            onClose = {}
        )
    }
}

@Preview(name = "Split Side Panel - User Account", widthDp = 360, heightDp = 500)
@Composable
fun CockpitSplitSidePanelPreviewAccount() {
    PleosTheme(darkTheme = true) {
        CockpitSplitSidePanel(
            isOpen = true,
            mode = CockpitSidePanelMode.USER_ACCOUNT,
            onClose = {}
        )
    }
}
