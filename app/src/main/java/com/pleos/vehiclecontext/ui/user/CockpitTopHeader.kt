package com.pleos.vehiclecontext.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 모던 차량용 콕핏 상단 미니멀 글로벌 정보 헤더 (Top Status Header)
 * - 상단 모든 칩(목적지, 온도, 하차 알림 토글)의 크기와 높이(34dp)를 완벽하게 일체화
 * - 뚱뚱한 스위치 제거 ➔ 온도 칩과 동일한 세련된 모빌리티 원터치 토글 알약 칩 탑재
 */
@Composable
fun CockpitTopHeader(
    session: TripSession,
    isArrivalAlertEnabled: Boolean = true,
    onToggleArrivalAlert: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hmi.topHeaderHeight)
            .background(colors.surface)
    ) {
        // Bottom Subtle Divider Line (거슬리는 사방 네모 상자를 없애고 하단 1px 경계선만 미니멀하게 배치)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.surfaceBorderSubtle)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hmi.panelPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Clean Destination Indicator (차량 디스플레이 순정 타이포그래피)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = hmi.itemSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
            ) {
                Box(
                    modifier = Modifier
                        .height(hmi.chipHeight)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "목적지",
                        color = colors.primary,
                        fontSize = hmi.badgeFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = session.destinationName,
                    color = colors.textPrimary,
                    fontSize = hmi.titleFontSize,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Seamless Header Pills (온도 칩과 알림 토글 칩)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Temperature Pill
                Box(
                    modifier = Modifier
                        .height(hmi.chipHeight)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.surfaceSecondary)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🌡️ ${session.cabinTempCelsius}℃",
                        color = colors.textPrimary,
                        fontSize = hmi.bodyFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Arrival Alert Toggle Button
                val toggleBg = if (isArrivalAlertEnabled) colors.primaryContainer else colors.surfaceSecondary
                val toggleTextColor = if (isArrivalAlertEnabled) colors.primary else colors.textTertiary

                Box(
                    modifier = Modifier
                        .height(hmi.chipHeight)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(toggleBg)
                        .clickable { onToggleArrivalAlert(!isArrivalAlertEnabled) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isArrivalAlertEnabled) "🔔" else "🔕",
                            fontSize = hmi.iconSmallSize
                        )
                        Text(
                            text = if (hmi.isCompactHeader) {
                                if (isArrivalAlertEnabled) "알림 ON" else "알림 OFF"
                            } else {
                                if (isArrivalAlertEnabled) "하차 알림 ON" else "하차 알림 OFF"
                            },
                            color = toggleTextColor,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Cockpit Top Header - Dark", widthDp = 940, heightDp = 52)
@Composable
fun CockpitTopHeaderPreviewDark() {
    PleosTheme(darkTheme = true) {
        CockpitTopHeader(
            session = TripSession(destinationName = "우리 집 (서초 푸르지오)"),
            isArrivalAlertEnabled = true
        )
    }
}

@Preview(name = "Cockpit Top Header - Light", widthDp = 940, heightDp = 52)
@Composable
fun CockpitTopHeaderPreviewLight() {
    PleosTheme(darkTheme = false) {
        CockpitTopHeader(
            session = TripSession(destinationName = "우리 집 (서초 푸르지오)"),
            isArrivalAlertEnabled = false
        )
    }
}
