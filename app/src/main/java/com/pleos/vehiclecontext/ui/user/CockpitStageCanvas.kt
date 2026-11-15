package com.pleos.vehiclecontext.ui.user

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.ui.model.CargoItemPresentation
import com.pleos.vehiclecontext.ui.model.FreshnessCareStatus
import com.pleos.vehiclecontext.ui.model.FreshnessRiskLevel
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 중앙 일체형 트렁크 캔버스 스테이지 (Seamless Cockpit Stage) - 와이드 HMI 최적화
 * - 차량용 대화면(2560x1440)에 맞게 큼직하고 시원한 화물 히어로 카드와 상태 요약 배치
 * - 운전자 80cm 거리에서도 즉각 인지 가능한 고시인성 카드 레이아웃
 * - 신선식품 온도 및 실시간 골든타임 앵커 칩 대형화
 */
@Composable
fun CockpitStageCanvas(
    session: TripSession,
    cargoItems: List<CargoItemPresentation>,
    freshness: FreshnessCareStatus,
    onNavigateToCargoList: () -> Unit,
    onNavigateToFreshness: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val isTrunkOpen = DoorZone.TRUNK in session.openDoors

    val doorColor by animateColorAsState(
        targetValue = if (isTrunkOpen) colors.accentRed else colors.accentGreen,
        label = "DoorColorAnim"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = hmi.panelPadding, vertical = hmi.sectionSpacing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =================================================================
            // R1: Stage Info & Context Header
            // =================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tailgate State & Active Items Counter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
                ) {
                    Box(
                        modifier = Modifier
                            .height(hmi.chipHeight)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .background(doorColor.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
                            .border(1.5.dp, doorColor.copy(alpha = 0.5f), RoundedCornerShape(hmi.chipCornerRadius))
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(doorColor, CircleShape)
                            )
                            Text(
                                text = if (isTrunkOpen) "테일게이트 열림" else "테일게이트 닫힘 (안전)",
                                color = doorColor,
                                fontSize = hmi.bodyFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "적재 화물 ${cargoItems.size}건 보관 중",
                        color = colors.textPrimary,
                        fontSize = hmi.subheadFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Freshness Ambient Chip
                if (freshness.hasPerishableCargo && !hmi.isCompactHeader) {
                    Box(
                        modifier = Modifier
                            .height(hmi.chipHeight)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .background(colors.surface)
                            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(hmi.chipCornerRadius))
                            .clickable { onNavigateToFreshness() }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "❄️", fontSize = hmi.iconSmallSize)
                            Text(
                                text = "${freshness.itemName} · ${freshness.remainingMinutes}분 남음",
                                color = colors.primary,
                                fontSize = hmi.bodyFontSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ">",
                                color = colors.textTertiary,
                                fontSize = hmi.bodyFontSize
                            )
                        }
                    }
                }
            }

            // =================================================================
            // R2: Center Wide Stage (불필요한 조잡한 차량 트렁크 그래픽 제거, 화물/상태 정보 집중)
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = hmi.itemSpacing),
                contentAlignment = Alignment.Center
            ) {
                if (cargoItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(hmi.cardCornerRadius))
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
                            .padding(horizontal = hmi.panelPadding, vertical = hmi.sectionSpacing * 1.5f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
                    ) {
                        Text(
                            text = "트렁크가 비어 있습니다",
                            color = colors.textPrimary,
                            fontSize = hmi.headlineFontSize,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (hmi.isCompactHeader) {
                                "하단 마이크·프리셋으로 화물을 등록하세요."
                            } else {
                                "출발 전 짐을 싣거나 하단 마이크·프리셋으로 화물을 등록하세요.\n목적지 도착 시 잊지 않도록 하차 전에 미리 알려드립니다."
                            },
                            color = colors.textSecondary,
                            fontSize = hmi.bodyFontSize,
                            lineHeight = hmi.bodyLineHeight,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    StageCargoDeckColumn(
                        cargoItems = cargoItems,
                        onNavigateToCargoList = onNavigateToCargoList,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun StageCargoDeckColumn(
    cargoItems: List<CargoItemPresentation>,
    onNavigateToCargoList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRUNK CARGO STAGE",
                color = colors.textTertiary,
                fontSize = hmi.badgeFontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "보관함 전체 보기 >",
                color = colors.primary,
                fontSize = hmi.bodyFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToCargoList() }
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(cargoItems, key = { it.id }) { item ->
                StageCargoCard(
                    item = item,
                    onClick = onNavigateToCargoList
                )
            }
        }
    }
}

/**
 * 차량용 HMI 토큰 규격에 맞춰 스케일링되는 화물 히어로 카드
 */
@Composable
private fun StageCargoCard(
    item: CargoItemPresentation,
    onClick: () -> Unit
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Box(
        modifier = Modifier
            .size(width = hmi.cargoCardWidth, height = hmi.cargoCardHeight)
            .clip(RoundedCornerShape(hmi.cardCornerRadius))
            .background(colors.surface)
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
            .clickable(onClick = onClick)
            .padding(hmi.cardPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(hmi.minTouchTarget)
                        .clip(RoundedCornerShape(hmi.chipCornerRadius))
                        .background(colors.surfaceSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = item.icon, fontSize = hmi.iconLargeSize)
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (item.isPerishable) colors.accentGreenContainer else colors.primaryContainer,
                            RoundedCornerShape(hmi.chipCornerRadius / 2 + 4.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = item.categoryBadge,
                        color = if (item.isPerishable) colors.accentGreen else colors.primary,
                        fontSize = hmi.badgeFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = item.name,
                    color = colors.textPrimary,
                    fontSize = (hmi.titleFontSize.value * 1.12f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${item.locationNote} · ${item.loadTimeFormatted}",
                    color = colors.textSecondary,
                    fontSize = hmi.bodyFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "Stage Canvas - Dark", widthDp = 1200, heightDp = 500)
@Composable
fun CockpitStageCanvasPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품)", "신선식품", "🛒", "트렁크 중앙", "14:22", true),
            CargoItemPresentation("2", "투썸 딸기 케이크", "파손주의", "🎂", "트렁크 우측", "14:35", true)
        )
        val dummyFreshness = FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = "신선식품 3종",
            currentTempCelsius = 21.0f,
            elapsedMinutes = 20,
            limitMinutes = 60,
            remainingMinutes = 40,
            progress = 0.33f,
            riskLevel = FreshnessRiskLevel.SAFE
        )
        CockpitStageCanvas(
            session = TripSession(),
            cargoItems = dummyItems,
            freshness = dummyFreshness,
            onNavigateToCargoList = {},
            onNavigateToFreshness = {}
        )
    }
}
