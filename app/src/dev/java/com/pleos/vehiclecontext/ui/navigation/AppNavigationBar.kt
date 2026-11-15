package com.pleos.vehiclecontext.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.AutonomousDrivingProfile
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.ui.model.NavigationTab
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * AAOS 탑 내비게이션 바:
 * - 브랜드 헤더: "WHAT'S IN MY TRUNK?" + "Pleos AI Context · AAOS"
 * - 모드 세그먼트 전환: "🚗 운전자 모드" (기본) vs "🛠️ 개발자 콘솔"
 * - 실시간 차량 상태 알약 배지: VHAL 연결, 변속기 기어(P/D/R/N), 자율주행 모드/프로파일
 * - 라이트/다크 시스템 테마 완전 대응
 */
@Composable
fun AppNavigationBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    currentGear: GearState,
    autonomousMode: AutonomousDrivingMode,
    autonomousProfile: AutonomousDrivingProfile = AutonomousDrivingProfile.NORMAL,
    isVhalConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(hmi.topBarHeight),
        color = colors.surface,
        shadowElevation = if (colors.isDark) 0.dp else 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.surfaceBorder)
                .padding(horizontal = hmi.panelPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BrandHeaderSection()

            NavigationTabSection(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )

            VehicleStatusPillsSection(
                currentGear = currentGear,
                autonomousMode = autonomousMode,
                autonomousProfile = autonomousProfile,
                isVhalConnected = isVhalConnected
            )
        }
    }
}

@Composable
private fun BrandHeaderSection(modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
    ) {
        Box(
            modifier = Modifier
                .size(hmi.minTouchTarget)
                .background(colors.primaryContainer, RoundedCornerShape(hmi.chipCornerRadius))
                .border(1.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(hmi.chipCornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "📦", fontSize = hmi.iconMediumSize)
        }
        if (!hmi.isCompactHeader) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "WHAT'S IN MY TRUNK?",
                    color = colors.textPrimary,
                    fontSize = hmi.titleFontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pleos AI Context",
                        color = colors.primary,
                        fontSize = hmi.captionFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(colors.textTertiary, CircleShape)
                    )
                    Text(
                        text = "AAOS",
                        color = colors.textTertiary,
                        fontSize = hmi.captionFontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationTabSection(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Row(
        modifier = modifier
            .background(colors.surfaceSecondary, RoundedCornerShape(hmi.chipCornerRadius))
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationTabButton(
            tab = NavigationTab.GENERAL_USER,
            isSelected = selectedTab == NavigationTab.GENERAL_USER,
            onClick = { onTabSelected(NavigationTab.GENERAL_USER) }
        )
        NavigationTabButton(
            tab = NavigationTab.DEVELOPER_CONSOLE,
            isSelected = selectedTab == NavigationTab.DEVELOPER_CONSOLE,
            onClick = { onTabSelected(NavigationTab.DEVELOPER_CONSOLE) }
        )
    }
}

@Composable
private fun NavigationTabButton(
    tab: NavigationTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val containerBg = if (isSelected) colors.primary else Color.Transparent

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = if (hmi.isCompactHeader) 120.dp else 170.dp,
                minHeight = hmi.minTouchTarget
            )
            .clip(RoundedCornerShape(hmi.chipCornerRadius))
            .background(containerBg, RoundedCornerShape(hmi.chipCornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = if (hmi.isCompactHeader) 14.dp else 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = tab.icon, fontSize = hmi.iconSmallSize)
            Text(
                text = tab.label,
                color = if (isSelected) Color.White else colors.textSecondary,
                fontSize = hmi.subheadFontSize,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun VehicleStatusPillsSection(
    currentGear: GearState,
    autonomousMode: AutonomousDrivingMode,
    autonomousProfile: AutonomousDrivingProfile,
    isVhalConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // VHAL Pill (Compact 모드에서는 공간 확보를 위해 기어와 함께 간소화)
        if (!hmi.isCompactHeader) {
            val dotColor = if (isVhalConnected) colors.accentGreen else colors.accentAmber
            val vhalLabel = if (isVhalConnected) "VHAL Active" else "VHAL Mock"
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = hmi.chipHeight)
                    .background(dotColor.copy(alpha = 0.12f), RoundedCornerShape(hmi.chipCornerRadius))
                    .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(hmi.chipCornerRadius))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor, CircleShape)
                    )
                    Text(
                        text = vhalLabel,
                        color = dotColor,
                        fontSize = hmi.badgeFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gear Pill
        val (gearColor, gearIcon) = when (currentGear) {
            GearState.PARK -> Pair(colors.accentGreen, "🅿️")
            GearState.DRIVE -> Pair(colors.primary, "▶️")
            GearState.REVERSE -> Pair(colors.accentAmber, "◀️")
            GearState.NEUTRAL -> Pair(colors.textSecondary, "⏸️")
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = hmi.chipHeight)
                .background(gearColor.copy(alpha = 0.14f), RoundedCornerShape(hmi.chipCornerRadius))
                .border(1.dp, gearColor.copy(alpha = 0.45f), RoundedCornerShape(hmi.chipCornerRadius))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = gearIcon, fontSize = hmi.iconSmallSize)
                Text(
                    text = "기어: ${currentGear.label.take(1)}",
                    color = gearColor,
                    fontSize = hmi.badgeFontSize,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Autonomous Mode Pill
        val modeColor = when (autonomousMode) {
            AutonomousDrivingMode.MANUAL -> colors.textSecondary
            AutonomousDrivingMode.SMART_CRUISE_ADAS -> colors.primary
            AutonomousDrivingMode.HIGHWAY_PILOT_L3 -> colors.accentPurple
            AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF -> colors.accentAmber
        }
        val modeShortLabel = when (autonomousMode) {
            AutonomousDrivingMode.MANUAL -> "수동 운전"
            AutonomousDrivingMode.SMART_CRUISE_ADAS -> "ADAS Lv.2"
            AutonomousDrivingMode.HIGHWAY_PILOT_L3 -> "자율주행 Lv.3"
            AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF -> "AVP 발렛"
        }
        val adLabel = if (autonomousProfile == AutonomousDrivingProfile.CARGO_COMFORT_GUARD && !hmi.isCompactHeader) {
            "$modeShortLabel · 화물보호"
        } else {
            modeShortLabel
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = hmi.chipHeight)
                .background(modeColor.copy(alpha = 0.14f), RoundedCornerShape(hmi.chipCornerRadius))
                .border(1.dp, modeColor.copy(alpha = 0.45f), RoundedCornerShape(hmi.chipCornerRadius))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = adLabel,
                color = modeColor,
                fontSize = hmi.badgeFontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (다크 & 라이트)
// =========================================================================

@Preview(name = "AppNavigationBar - Dark (1280x80)", widthDp = 1280, heightDp = 80)
@Composable
fun AppNavigationBarGeneralUserPreviewDark() {
    PleosTheme(darkTheme = true) {
        AppNavigationBar(
            selectedTab = NavigationTab.GENERAL_USER,
            onTabSelected = {},
            currentGear = GearState.PARK,
            autonomousMode = AutonomousDrivingMode.MANUAL,
            autonomousProfile = AutonomousDrivingProfile.NORMAL,
            isVhalConnected = true
        )
    }
}

@Preview(name = "AppNavigationBar - Light (1280x80)", widthDp = 1280, heightDp = 80)
@Composable
fun AppNavigationBarGeneralUserPreviewLight() {
    PleosTheme(darkTheme = false) {
        AppNavigationBar(
            selectedTab = NavigationTab.GENERAL_USER,
            onTabSelected = {},
            currentGear = GearState.DRIVE,
            autonomousMode = AutonomousDrivingMode.SMART_CRUISE_ADAS,
            autonomousProfile = AutonomousDrivingProfile.CARGO_COMFORT_GUARD,
            isVhalConnected = true
        )
    }
}
