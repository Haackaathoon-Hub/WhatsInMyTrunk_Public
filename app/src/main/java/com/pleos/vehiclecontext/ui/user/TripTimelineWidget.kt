package com.pleos.vehiclecontext.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
import com.pleos.vehiclecontext.core.model.TripTimelineStep
import com.pleos.vehiclecontext.ui.theme.PleosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 여정 기록 & 인과관계 타임라인 상세 위젯 (Depth 3):
 * - 트렁크 보관함(CargoLiveOverviewWidget)과 동일한 상하 리스트 및 카드 규격으로 통일
 * - 페이지당 2단 대형 행 + 우측 세로 페이징 레일(▲/▼) 제공
 */
@Composable
fun TripTimelineWidget(
    currentPhase: TripPhase,
    timelineSteps: List<TripTimelineStep>,
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
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(hmi.cardPadding),
            verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
        ) {
            // Header Row: CargoLiveOverviewWidget과 완전히 동일한 규격
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
                            .background(colors.primaryContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "TIMELINE",
                            color = colors.primary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "여정 & 인과관계 로그",
                        color = colors.textPrimary,
                        fontSize = hmi.subheadFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceSecondary, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${timelineSteps.size}건 기록됨",
                            color = colors.textSecondary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(colors.accentAmberContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                        .border(1.dp, colors.accentAmber.copy(alpha = 0.4f), RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${currentPhase.stepNumber}. ${currentPhase.title}",
                        color = colors.accentAmber,
                        fontSize = hmi.badgeFontSize,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Body: Vertical Stepped Paging List (CargoLiveOverviewWidget과 동일)
            if (timelineSteps.isEmpty()) {
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
                        Text(text = "🧭", fontSize = hmi.heroIconSize)
                        Text(
                            text = "아직 기록된 여정 로그가 없습니다",
                            color = colors.textPrimary,
                            fontSize = hmi.subheadFontSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "트렁크를 개폐하거나 주행을 시작하면 인과관계 타임라인이 자동 생성됩니다",
                            color = colors.textTertiary,
                            fontSize = hmi.captionFontSize
                        )
                    }
                }
            } else {
                val itemsPerPage = 2
                val totalPages = maxOf(1, (timelineSteps.size + itemsPerPage - 1) / itemsPerPage)
                var currentPage by remember { mutableStateOf(0) }
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageSteps = timelineSteps.drop(safePage * itemsPerPage).take(itemsPerPage)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.85f)
                    ) {
                        pageSteps.forEach { step ->
                            TimelineStepRow(
                                step = step,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (totalPages > 1) {
                        VerticalSteppedPagingRail(
                            currentPage = safePage,
                            totalPages = totalPages,
                            onPrevPage = { currentPage = safePage - 1 },
                            onNextPage = { currentPage = safePage + 1 }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineStepRow(
    step: TripTimelineStep,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    val formattedTime = runCatching { timeFormat.format(Date(step.timestampMs)) }.getOrDefault("")
    val icon = when {
        step.isHighlight -> "🔔"
        step.title.contains("주행") -> "🚘"
        step.title.contains("적재") -> "📦"
        step.title.contains("목적지") || step.title.contains("경로") -> "📍"
        else -> "📝"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(hmi.cardCornerRadius * 0.75f))
            .background(if (step.isHighlight) colors.primaryContainer.copy(alpha = 0.45f) else colors.surfaceSecondary)
            .border(
                width = 1.2.dp,
                color = if (step.isHighlight) colors.primary.copy(alpha = 0.5f) else colors.surfaceBorderSubtle,
                shape = RoundedCornerShape(hmi.cardCornerRadius * 0.75f)
            )
            .padding(horizontal = hmi.cardPadding, vertical = hmi.itemSpacing),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(hmi.minTouchTarget + 12.dp)
                    .background(colors.surface, RoundedCornerShape(hmi.chipCornerRadius))
                    .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = hmi.iconLargeSize)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.title,
                        color = colors.textPrimary,
                        fontSize = hmi.titleFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                if (step.isHighlight) colors.primaryContainer else colors.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = step.phaseLabel,
                            color = if (step.isHighlight) colors.primary else colors.textSecondary,
                            fontSize = hmi.captionFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${step.detail} · ${step.origin.displayName}",
                    color = colors.textSecondary,
                    fontSize = hmi.bodyFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (formattedTime.isNotBlank()) {
            Box(
                modifier = Modifier
                    .heightIn(min = hmi.minTouchTarget * 0.85f)
                    .clip(RoundedCornerShape(hmi.chipCornerRadius))
                    .background(colors.surface)
                    .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedTime,
                    color = colors.textSecondary,
                    fontSize = hmi.bodyFontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (다크 & 라이트 모드)
// =========================================================================

@Preview(name = "Trip Timeline Dark (600x240)", widthDp = 600, heightDp = 240, showBackground = true)
@Composable
fun TripTimelineWidgetPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummySteps = listOf(
            TripTimelineStep(
                phaseLabel = "출발 전",
                title = "이마트 장보기 적재 완료",
                detail = "신선식품 3종 감지 (21.0℃)",
                origin = SignalOrigin.REAL_SDK,
                isHighlight = false
            ),
            TripTimelineStep(
                phaseLabel = "주행 중",
                title = "고속 주행 및 실내 온도 유지",
                detail = "냉방 22℃ 정상 가동 중",
                origin = SignalOrigin.REAL_SDK,
                isHighlight = false
            ),
            TripTimelineStep(
                phaseLabel = "도착 하차",
                title = "목적지 도착 및 P단 주차",
                detail = "스마트 하차 알림 발동",
                origin = SignalOrigin.REAL_SDK,
                isHighlight = true
            )
        )
        TripTimelineWidget(
            currentPhase = TripPhase.ARRIVED_PARKED,
            timelineSteps = dummySteps
        )
    }
}

@Preview(name = "Trip Timeline Light (600x240)", widthDp = 600, heightDp = 240, showBackground = true)
@Composable
fun TripTimelineWidgetPreviewLight() {
    PleosTheme(darkTheme = false) {
        val dummySteps = listOf(
            TripTimelineStep(
                phaseLabel = "도착 하차",
                title = "목적지 도착 및 P단 주차",
                detail = "스마트 하차 알림 발동",
                origin = SignalOrigin.REAL_SDK,
                isHighlight = true
            )
        )
        TripTimelineWidget(
            currentPhase = TripPhase.ARRIVED_PARKED,
            timelineSteps = dummySteps
        )
    }
}
