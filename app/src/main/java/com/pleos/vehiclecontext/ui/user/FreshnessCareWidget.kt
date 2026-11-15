package com.pleos.vehiclecontext.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pleos.vehiclecontext.ui.model.FreshnessCareStatus
import com.pleos.vehiclecontext.ui.model.FreshnessRiskLevel
import com.pleos.vehiclecontext.ui.theme.PleosTheme
import java.util.Locale

/**
 * 신선식품 스마트 케어 & 공조 제어 위젯 (Depth 2):
 * - 실시간 트렁크/실내 온도 게이지
 * - 신선식품 변질 위험도 예측 및 잔여 골든타임 모니터링
 * - [❄️ 실내 급속 냉방 가동] 폐루프 차량 공조 제어 버튼
 * - 충분한 글자 크기(15sp+) 및 라이트/다크 테마 완전 대응
 */
@Composable
fun FreshnessCareWidget(
    freshness: FreshnessCareStatus,
    cabinTempCelsius: Float,
    onActivateCooling: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    val tempColor = when {
        cabinTempCelsius >= 30.0f -> colors.accentRed
        cabinTempCelsius >= 24.0f -> colors.accentAmber
        else -> colors.primary
    }

    val riskColor = when (freshness.riskLevel) {
        FreshnessRiskLevel.SAFE -> colors.accentGreen
        FreshnessRiskLevel.WARNING -> colors.accentAmber
        FreshnessRiskLevel.CRITICAL -> colors.accentRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(hmi.cardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(hmi.cardPadding),
            verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
        ) {
            // Header Row: Title, Temp Badge, Risk Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.7f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.accentGreenContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "FRESHNESS",
                            color = colors.accentGreen,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "신선식품 스마트 케어",
                        color = colors.textPrimary,
                        fontSize = hmi.subheadFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cabin Temperature Badge
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceSecondary, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.KOREA, "🌡️ %.1f°C", cabinTempCelsius),
                            color = tempColor,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Risk Level Pill
                    Box(
                        modifier = Modifier
                            .background(
                                when (freshness.riskLevel) {
                                    FreshnessRiskLevel.SAFE -> colors.accentGreenContainer
                                    FreshnessRiskLevel.WARNING -> colors.accentAmberContainer
                                    FreshnessRiskLevel.CRITICAL -> colors.accentRedContainer
                                },
                                RoundedCornerShape(hmi.chipCornerRadius * 0.65f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = freshness.riskLevel.label,
                            color = riskColor,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Body: Golden Time Visualizer or Idle State
            if (!freshness.hasPerishableCargo) {
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
                        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.5f)
                    ) {
                        Text(text = "❄️", fontSize = hmi.heroIconSize)
                        Text(
                            text = "현재 적재된 변질 주의 물품이 없습니다",
                            color = colors.textPrimary,
                            fontSize = hmi.subheadFontSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.KOREA, "트렁크 실내 온도 %.1f°C 정상 유지 중", cabinTempCelsius),
                            color = colors.textTertiary,
                            fontSize = hmi.captionFontSize
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
                        .padding(hmi.cardPadding),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Item Label & Remaining Big Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "감시 대상 품목",
                                color = colors.textTertiary,
                                fontSize = hmi.badgeFontSize,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = freshness.itemName,
                                color = colors.textPrimary,
                                fontSize = hmi.subheadFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${freshness.remainingMinutes}분 남음",
                                color = riskColor,
                                fontSize = hmi.headlineFontSize,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "골든타임 ${freshness.limitMinutes}분 중 ${freshness.elapsedMinutes}분 경과",
                                color = colors.textTertiary,
                                fontSize = hmi.badgeFontSize
                            )
                        }
                    }

                    // Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { freshness.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = riskColor,
                            trackColor = colors.surfaceBorder,
                            strokeCap = StrokeCap.Round
                        )
                    }

                    // Action Button: Climate Control
                    Button(
                        onClick = onActivateCooling,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(hmi.chipCornerRadius),
                        contentPadding = PaddingValues(horizontal = hmi.cardPadding, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = hmi.minTouchTarget)
                    ) {
                        Text(
                            text = "❄️ 실내 급속 냉방 가동 (20.0℃ 목표)",
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (다크 & 라이트 모드)
// =========================================================================

@Preview(name = "Freshness Care Dark (500x300)", widthDp = 500, heightDp = 300, showBackground = true)
@Composable
fun FreshnessCareWidgetPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummyStatus = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "이마트 한우 등심 & 신선우유",
            currentTempCelsius = 26.5f,
            elapsedMinutes = 35,
            limitMinutes = 60,
            remainingMinutes = 25,
            progress = 0.58f,
            riskLevel = FreshnessRiskLevel.WARNING
        )
        FreshnessCareWidget(
            freshness = dummyStatus,
            cabinTempCelsius = 26.5f
        )
    }
}

@Preview(name = "Freshness Care Light (500x300)", widthDp = 500, heightDp = 300, showBackground = true)
@Composable
fun FreshnessCareWidgetPreviewLight() {
    PleosTheme(darkTheme = false) {
        val dummyStatus = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "이마트 한우 등심 & 신선우유",
            currentTempCelsius = 26.5f,
            elapsedMinutes = 35,
            limitMinutes = 60,
            remainingMinutes = 25,
            progress = 0.58f,
            riskLevel = FreshnessRiskLevel.WARNING
        )
        FreshnessCareWidget(
            freshness = dummyStatus,
            cabinTempCelsius = 26.5f
        )
    }
}
