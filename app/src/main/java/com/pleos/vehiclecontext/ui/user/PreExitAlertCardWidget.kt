package com.pleos.vehiclecontext.ui.user

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 운전자 핵심 Hero 카드: 목적지 스마트 하차 브리핑 & 폐루프 차량 제어 위젯
 * - 목적지 자동 감지 및 P단 주차 시 지능형 하차 알림 팝업
 * - [🚗 트렁크 열기] 대형 물리 제어 버튼 (48dp+ 터치 타깃, 한글 텍스트 클리핑 방지)
 * - 라이트/다크 시스템 테마 완전 대응
 */
@Composable
fun PreExitAlertCardWidget(
    session: TripSession,
    isArrivalAlertEnabled: Boolean,
    onToggleArrivalAlert: (Boolean) -> Unit,
    activePrompt: ContextAction?,
    onAcknowledgePrompt: () -> Unit,
    onExecuteControl: (VehicleControlCommand) -> Unit = {},
    onNavigateToCargoList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

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
            // Header Row: Card Title & Integrity Status Badge
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
                            .background(colors.primaryContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "SMART ALERT",
                            color = colors.primary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "하차 전 스마트 알림",
                        color = colors.textPrimary,
                        fontSize = hmi.subheadFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Window Integrity Badge
                if (session.openWindowsCount == 0) {
                    Box(
                        modifier = Modifier
                            .background(colors.accentGreenContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .border(1.dp, colors.accentGreen.copy(alpha = 0.3f), RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🪟 창문 전체 닫힘",
                            color = colors.accentGreen,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(colors.accentAmberContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .border(1.dp, colors.accentAmber.copy(alpha = 0.5f), RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚠️ 창문 ${session.openWindowsCount}개 열림",
                            color = colors.accentAmber,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sub-Card: Destination & Alert Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceSecondary, RoundedCornerShape(hmi.chipCornerRadius))
                    .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
                    .padding(horizontal = hmi.cardPadding, vertical = hmi.itemSpacing * 0.75f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📍 목적지:",
                            color = colors.textSecondary,
                            fontSize = hmi.captionFontSize,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = session.destinationName,
                            color = colors.textPrimary,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "목적지 도착 및 P단 변속 시 하차 브리핑 자동 발동",
                        color = colors.textTertiary,
                        fontSize = hmi.badgeFontSize
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArrivalAlertEnabled) "알림 ON" else "알림 OFF",
                        color = if (isArrivalAlertEnabled) colors.primary else colors.textTertiary,
                        fontSize = hmi.captionFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = isArrivalAlertEnabled,
                        onCheckedChange = onToggleArrivalAlert,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.surfaceBorder
                        )
                    )
                }
            }

            // Body Area: Active Arrival Prompt or Standby Status
            if (activePrompt != null) {
                // Active Alert Card: Sophisticated Amber/Blue Tinted Surface (No tacky thick borders)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.accentAmberContainer.copy(alpha = if (colors.isDark) 0.35f else 0.45f))
                        .border(1.dp, colors.accentAmber.copy(alpha = 0.5f), RoundedCornerShape(hmi.chipCornerRadius))
                        .padding(hmi.cardPadding),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ ${activePrompt.headline}",
                                color = colors.accentAmber,
                                fontSize = hmi.subheadFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Box(
                                modifier = Modifier
                                    .background(colors.surface.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = activePrompt.scenarioTitle,
                                    color = colors.textSecondary,
                                    fontSize = hmi.badgeFontSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = activePrompt.bodyMessage,
                            color = colors.textPrimary,
                            fontSize = hmi.bodyFontSize,
                            lineHeight = hmi.bodyLineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Action Buttons Row: Guaranteed minTouchTarget height, zero clipping
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.75f)
                        ) {
                            // Primary Control: Open or Close Trunk based on current state
                            val isTrunkOpen = com.pleos.vehiclecontext.core.model.DoorZone.TRUNK in session.openDoors
                            Button(
                                onClick = {
                                    onExecuteControl(
                                        if (isTrunkOpen) VehicleControlCommand.CLOSE_TRUNK else VehicleControlCommand.OPEN_TRUNK
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(hmi.chipCornerRadius),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .defaultMinSize(minHeight = hmi.minTouchTarget)
                            ) {
                                Text(
                                    text = if (isTrunkOpen) "🔒 트렁크 닫기" else "🚗 트렁크 열기",
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // Secondary Control: Dismiss / Acknowledge
                            Button(
                                onClick = onAcknowledgePrompt,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.surface,
                                    contentColor = colors.textPrimary
                                ),
                                shape = RoundedCornerShape(hmi.chipCornerRadius),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = hmi.minTouchTarget)
                                    .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                            ) {
                                Text(
                                    text = "확인 완료",
                                    fontSize = hmi.bodyFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Learning feedback option
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = hmi.minTouchTarget)
                                .clickable { onExecuteControl(VehicleControlCommand.KEEP_IN_TRUNK_PERMANENTLY) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📦 꺼내지 않고 차에 계속 보관할 짐인가요?",
                                color = colors.textSecondary,
                                fontSize = hmi.badgeFontSize,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Standby State: Clean and calm waiting state with shortcut to inventory
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.surfaceSecondary, RoundedCornerShape(hmi.chipCornerRadius))
                        .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
                        .padding(hmi.cardPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.65f)
                    ) {
                        Text(
                            text = if (isArrivalAlertEnabled) "🅿️ 목적지 도착 및 P단 변속 시 알림 대기 중" else "🔕 하차 알림이 꺼져 있습니다",
                            color = if (isArrivalAlertEnabled) colors.textPrimary else colors.textTertiary,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "불필요한 중복 알림 방지 및 주행 중 방해 금지 모드가 적용됩니다",
                            color = colors.textTertiary,
                            fontSize = hmi.badgeFontSize
                        )

                        Box(
                            modifier = Modifier
                                .heightIn(min = hmi.minTouchTarget)
                                .clip(RoundedCornerShape(hmi.chipCornerRadius * 0.75f))
                                .clickable { onNavigateToCargoList() }
                                .background(colors.primaryContainer)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📦 트렁크 보관함 상세 보기 >",
                                color = colors.primary,
                                fontSize = hmi.captionFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (다크 모드 & 라이트 모드 각각 제공)
// =========================================================================

@Preview(name = "Pre-Exit Card - Active Prompt Dark (480x260)", widthDp = 480, heightDp = 260, showBackground = true)
@Composable
fun PreExitAlertCardWidgetPreviewDark() {
    PleosTheme(darkTheme = true) {
        val session = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            openWindowsCount = 0
        )
        val dummyPrompt = ContextAction(
            scenarioId = "PROMPT_TRUNK_ARRIVAL",
            scenarioTitle = "트렁크 하차 케어",
            headline = "목적지 도착! 트렁크 확인 안내",
            bodyMessage = "출발 전 실으신 [이마트 장본 짐]을 잊지 말고 챙기세요!",
            contextReasonBadge = "도착 + P단 주차 감지",
            signalOrigin = com.pleos.vehiclecontext.core.model.SignalOrigin.REAL_SDK,
            voiceTtsMessage = "목적지에 도착했습니다.",
            primaryAckLabel = "확인할게요"
        )
        PreExitAlertCardWidget(
            session = session,
            isArrivalAlertEnabled = true,
            onToggleArrivalAlert = {},
            activePrompt = dummyPrompt,
            onAcknowledgePrompt = {}
        )
    }
}

@Preview(name = "Pre-Exit Card - Active Prompt Light (480x260)", widthDp = 480, heightDp = 260, showBackground = true)
@Composable
fun PreExitAlertCardWidgetPreviewLight() {
    PleosTheme(darkTheme = false) {
        val session = TripSession(
            destinationName = "우리 집 (서초 푸르지오)",
            openWindowsCount = 0
        )
        val dummyPrompt = ContextAction(
            scenarioId = "PROMPT_TRUNK_ARRIVAL",
            scenarioTitle = "트렁크 하차 케어",
            headline = "목적지 도착! 트렁크 확인 안내",
            bodyMessage = "출발 전 실으신 [이마트 장본 짐]을 잊지 말고 챙기세요!",
            contextReasonBadge = "도착 + P단 주차 감지",
            signalOrigin = com.pleos.vehiclecontext.core.model.SignalOrigin.REAL_SDK,
            voiceTtsMessage = "목적지에 도착했습니다.",
            primaryAckLabel = "확인할게요"
        )
        PreExitAlertCardWidget(
            session = session,
            isArrivalAlertEnabled = true,
            onToggleArrivalAlert = {},
            activePrompt = dummyPrompt,
            onAcknowledgePrompt = {}
        )
    }
}
