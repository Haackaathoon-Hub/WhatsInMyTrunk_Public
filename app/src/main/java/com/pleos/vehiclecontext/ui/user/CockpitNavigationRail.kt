package com.pleos.vehiclecontext.ui.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.R
import com.pleos.vehiclecontext.ui.model.UserSubTab
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 현대차 ccNC / 제네시스 스타일의 좌측 글로벌 네비게이션 레일 (Navigation Rail)
 * - 강제 클리핑 해제: 적재 수량 팝업 배지가 버튼 경계에 잘리지 않고 100% 온전하게 노출
 * - 여유로운 버튼 간격 (spacedBy 18dp) 및 정중앙 축선 완벽 정렬
 * - 상단: 공식 앱 로고 엠블럼 (터치 시 앱 버전 다이얼로그)
 * - 하단: 설정(⚙️) 및 사용자 프로필(👤) ➔ 우측 스플릿 사이드 패널 연동
 */
@Composable
fun CockpitNavigationRail(
    selectedTab: UserSubTab,
    onTabSelected: (UserSubTab) -> Unit,
    cargoCount: Int,
    isSettingsOpen: Boolean = false,
    isUserAccountOpen: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenUserAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    var showVersionDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(hmi.navRailWidth)
            .fillMaxHeight()
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        // Subtle Right Vertical Border (사방 네모 상자를 없애고 우측 1px 분리선만 세련되게 배치)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.surfaceBorderSubtle)
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // [Top] Official App Brand Logo Emblem (Tap to view App Version)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showVersionDialog = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(hmi.minTouchTarget)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "WHAT'S IN MY TRUNK 로고",
                        modifier = Modifier
                            .size(hmi.minTouchTarget - 8.dp)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius - 2.dp))
                    )
                }

                Text(
                    text = "TRUNK",
                    color = colors.primary,
                    fontSize = hmi.badgeFontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            // [Center] Main Navigation Tab Items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserSubTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val badgeCount = if (tab == UserSubTab.CARGO_LIST && cargoCount > 0) cargoCount else null

                    RailTabItem(
                        tab = tab,
                        isSelected = isSelected,
                        badgeCount = badgeCount,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }

            // [Bottom] Settings & User Profile Account Section (Split Panel Triggers)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
            ) {
                HorizontalDivider(
                    color = colors.surfaceBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                // Settings Button (⚙️)
                Box(
                    modifier = Modifier
                        .size(hmi.minTouchTarget)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(if (isSettingsOpen) colors.primaryContainer else colors.surfaceSecondary)
                        .border(
                            width = 1.dp,
                            color = if (isSettingsOpen) colors.primary else colors.surfaceBorderSubtle,
                            shape = RoundedCornerShape(hmi.chipCornerRadius)
                        )
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙️", fontSize = hmi.iconMediumSize)
                }

                // User Account Profile Avatar (👤 - Tap to view Split User Profile)
                Box(
                    modifier = Modifier
                        .size(hmi.minTouchTarget)
                        .clip(CircleShape)
                        .background(if (isUserAccountOpen) colors.primaryContainer else colors.surfaceSecondary)
                        .border(
                            width = 1.5.dp,
                            color = if (isUserAccountOpen) colors.primary else colors.surfaceBorderSubtle,
                            shape = CircleShape
                        )
                        .clickable(onClick = onOpenUserAccount),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👤", fontSize = hmi.iconMediumSize)

                    // Connected Sync Status Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(colors.accentGreen, CircleShape)
                            .border(2.dp, colors.surface, CircleShape)
                    )
                }
            }
        }
    }

    // =====================================================================
    // App Version Dialog (Logo Emblem Tap)
    // =====================================================================
    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            confirmButton = {
                Button(
                    onClick = { showVersionDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text(
                        text = "확인",
                        color = Color.White,
                        fontSize = hmi.bodyFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "📦", fontSize = hmi.iconMediumSize)
                    Text(
                        text = "WHAT'S IN MY TRUNK?",
                        color = colors.textPrimary,
                        fontSize = hmi.titleFontSize,
                        fontWeight = FontWeight.Black
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "버전: v1.0.0-pleos (Release Build 2026.11)",
                        color = colors.textPrimary,
                        fontSize = hmi.bodyFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "현대자동차 Pleos Connect App Development Challenge 출품작",
                        color = colors.textSecondary,
                        fontSize = hmi.captionFontSize
                    )
                    Text(
                        text = "개발팀: Players\nE2E LLM 컨텍스트 인텔리전스 차량용 HMI",
                        color = colors.textTertiary,
                        fontSize = hmi.captionFontSize,
                        lineHeight = hmi.bodyLineHeight
                    )
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(hmi.cardCornerRadius + 18.dp)
        )
    }
}

@Composable
private fun RailTabItem(
    tab: UserSubTab,
    isSelected: Boolean,
    badgeCount: Int?,
    onClick: () -> Unit
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val badgeText = when {
        badgeCount == null || badgeCount <= 0 -> null
        badgeCount > 99 -> "99+"
        else -> badgeCount.toString()
    }

    // NO ROOT CLIP: 배지가 버튼 경계 밖으로 걸쳐도 절대 잘리지 않음!
    // 거슬리는 딱딱한 네모 상자 테두리를 제거하고 유려한 18dp 캡슐 앰비언트 하이라이트 적용
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(hmi.navRailItemHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(
                color = if (isSelected) colors.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Selection Left Active Bar (현대차 ccNC 시그니처 세티클 인디케이터)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(5.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(colors.primary)
            )
        }

        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = tab.icon,
                    fontSize = hmi.iconLargeSize,
                    textAlign = TextAlign.Center
                )

                // 수십 개 및 99+ 완벽 대응 앰비언트 배지 (클리핑 0%, 폰트 패딩 처짐 방지)
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 16.dp, y = (-6).dp)
                            .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                            .background(colors.primary, RoundedCornerShape(13.dp))
                            .border(1.5.dp, colors.surface, RoundedCornerShape(13.dp))
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = PleosTheme.fontFamily,
                                fontFeatureSettings = "tnum",
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                lineHeight = hmi.badgeFontSize
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tab.label,
                color = if (isSelected) colors.primary else colors.textTertiary,
                fontSize = hmi.captionFontSize,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Preview(name = "Cockpit Rail - Dark (Normal 2 items)", widthDp = 92, heightDp = 600)
@Composable
fun CockpitNavigationRailPreviewDark() {
    PleosTheme(darkTheme = true) {
        CockpitNavigationRail(
            selectedTab = UserSubTab.DASHBOARD,
            onTabSelected = {},
            cargoCount = 2,
            isSettingsOpen = false,
            isUserAccountOpen = false
        )
    }
}

@Preview(name = "Cockpit Rail - Light (High 99+ items)", widthDp = 92, heightDp = 600)
@Composable
fun CockpitNavigationRailPreviewLight() {
    PleosTheme(darkTheme = false) {
        CockpitNavigationRail(
            selectedTab = UserSubTab.CARGO_LIST,
            onTabSelected = {},
            cargoCount = 125,
            isSettingsOpen = true,
            isUserAccountOpen = false
        )
    }
}
