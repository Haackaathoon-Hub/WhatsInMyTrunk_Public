package com.pleos.vehiclecontext.ui.user

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.ui.model.CargoItemPresentation
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 트렁크 보관함 상세 위젯 (Depth 1):
 * - 테일게이트 개폐 실시간 상태
 * - 현재 적재된 화물 인벤토리 목록 (시원한 카드 리스트, 충분한 폰트 크기)
 * - 라이트/다크 테마 완전 대응
 */
@Composable
fun CargoLiveOverviewWidget(
    session: TripSession,
    cargoItems: List<CargoItemPresentation>,
    onClearTrunk: () -> Unit = {},
    onUnloadItem: ((String) -> Unit)? = null,
    onConfirmUnloadStop: (() -> Unit)? = null,
    onEditItem: ((CargoItemPresentation) -> Unit)? = null,
    onDeleteItem: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val isTrunkOpen = DoorZone.TRUNK in session.openDoors

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
            // Header Row: Title, Item Count & Tailgate Status
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
                            text = "INVENTORY",
                            color = colors.primary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "트렁크 보관함",
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
                            text = "${cargoItems.size}건 보관 중",
                            color = colors.textSecondary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Tailgate Status Badge
                if (isTrunkOpen) {
                    Box(
                        modifier = Modifier
                            .background(colors.accentRedContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .border(1.dp, colors.accentRed.copy(alpha = 0.5f), RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚠️ 테일게이트 열림",
                            color = colors.accentRed,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(colors.accentGreenContainer, RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .border(1.dp, colors.accentGreen.copy(alpha = 0.3f), RoundedCornerShape(hmi.chipCornerRadius * 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🔒 트렁크 닫힘",
                            color = colors.accentGreen,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Body: Cargo Inventory List or Empty State
            if (cargoItems.isEmpty()) {
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
                        Text(text = "📦", fontSize = hmi.heroIconSize)
                        Text(
                            text = "현재 적재된 화물이 없습니다",
                            color = colors.textPrimary,
                            fontSize = hmi.subheadFontSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "음성으로 '마트 장본 거 실었어'라고 말하거나 퀵 프리셋을 탭하세요",
                            color = colors.textTertiary,
                            fontSize = hmi.captionFontSize
                        )
                    }
                }
            } else {
                val itemsPerPage = 2
                val totalPages = maxOf(1, (cargoItems.size + itemsPerPage - 1) / itemsPerPage)
                var currentPage by remember { mutableStateOf(0) }
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageItems = cargoItems.drop(safePage * itemsPerPage).take(itemsPerPage)

                // Body: Stepped Paging List (한 페이지 2단 대형 카드로 시인성 극대화)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing)
                ) {
                    // Left: Current Page Cargo List (Large high-legibility tiers)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.85f)
                    ) {
                        pageItems.forEach { item ->
                            val deleteHandler: (() -> Unit)? = when {
                                onDeleteItem != null -> {
                                    { onDeleteItem(item.id) }
                                }
                                onUnloadItem != null &&
                                    !item.id.startsWith("perm-") &&
                                    !item.id.startsWith("ai-item-") &&
                                    !item.id.startsWith("trunk-") -> {
                                    { onUnloadItem(item.id) }
                                }
                                else -> null
                            }
                            CargoItemRow(
                                item = item,
                                onEdit = onEditItem?.let { handler -> { handler(item) } },
                                onDelete = deleteHandler,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Right: Side Step Navigation Controls (공통 상하 원터치 페이징 레일)
                    if (totalPages > 1) {
                        VerticalSteppedPagingRail(
                            currentPage = safePage,
                            totalPages = totalPages,
                            onPrevPage = { currentPage = safePage - 1 },
                            onNextPage = { currentPage = safePage + 1 }
                        )
                    }
                }

                // Footer: Arrival Unload Confirm + Clear All Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (session.unloadConfirmationNeeded && onConfirmUnloadStop != null) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = hmi.minTouchTarget)
                                .clip(RoundedCornerShape(hmi.chipCornerRadius))
                                .background(colors.primaryContainer)
                                .border(1.dp, colors.primary, RoundedCornerShape(hmi.chipCornerRadius))
                                .clickable { onConfirmUnloadStop() }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓ 도착지 하차 완료 확인",
                                color = colors.primary,
                                fontSize = hmi.badgeFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = hmi.minTouchTarget)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .clickable { onClearTrunk() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🗑️ 보관함 비우기",
                            color = colors.textTertiary,
                            fontSize = hmi.badgeFontSize,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CargoItemRow(
    item: CargoItemPresentation,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val titleText = item.itemTitle.ifBlank { item.name }
    val destText = item.destinationLabel.ifBlank {
        if (item.isPermanent) "상시 보관" else "목적지 미정"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(hmi.cardCornerRadius * 0.85f))
            .background(colors.surfaceSecondary)
            .border(1.2.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.cardCornerRadius * 0.85f))
            .padding(horizontal = hmi.cardPadding, vertical = hmi.itemSpacing),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                Text(text = item.icon, fontSize = hmi.iconLargeSize)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 1행: 항목명 + 구분 배지 + 수량 배지
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleText,
                        color = colors.textPrimary,
                        fontSize = hmi.titleFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CargoElementTag(
                        label = "구분 ${item.categoryBadge}",
                        containerColor = if (item.isPerishable) colors.accentGreenContainer else colors.primaryContainer,
                        contentColor = if (item.isPerishable) colors.accentGreen else colors.primary
                    )
                    if (item.quantityLabel.isNotBlank()) {
                        CargoElementTag(
                            label = "수량 ${item.quantityLabel}",
                            containerColor = colors.surface,
                            contentColor = colors.textPrimary,
                            borderColor = colors.surfaceBorderSubtle
                        )
                    }
                }

                // 2행: 목적지 · 용도/메모 · 출발지 · 적재 시각 분리 표시
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CargoElementTag(
                        label = "🏁 목적지: $destText",
                        containerColor = colors.surface,
                        contentColor = colors.primary,
                        borderColor = colors.primary.copy(alpha = 0.35f)
                    )
                    if (item.purposeLabel.isNotBlank()) {
                        CargoElementTag(
                            label = "🏷️ ${item.purposeLabel}",
                            containerColor = colors.surface,
                            contentColor = colors.textSecondary,
                            borderColor = colors.surfaceBorderSubtle
                        )
                    }
                    if (item.originLabel.isNotBlank()) {
                        CargoElementTag(
                            label = "📍 ${item.originLabel}",
                            containerColor = colors.surface,
                            contentColor = colors.textSecondary,
                            borderColor = colors.surfaceBorderSubtle
                        )
                    }
                    Text(
                        text = "🕒 ${item.loadTimeFormatted}",
                        color = colors.textTertiary,
                        fontSize = hmi.captionFontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (onEdit != null || onDelete != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onEdit != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = hmi.minTouchTarget)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .background(colors.primaryContainer)
                            .border(1.dp, colors.primary.copy(alpha = 0.55f), RoundedCornerShape(hmi.chipCornerRadius))
                            .clickable(onClick = onEdit)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✏️ 편집",
                            color = colors.primary,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = hmi.minTouchTarget)
                            .clip(RoundedCornerShape(hmi.chipCornerRadius))
                            .background(colors.accentRedContainer)
                            .border(1.dp, colors.accentRed.copy(alpha = 0.45f), RoundedCornerShape(hmi.chipCornerRadius))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🗑️ 삭제",
                            color = colors.accentRed,
                            fontSize = hmi.bodyFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CargoElementTag(
    label: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null
) {
    val hmi = PleosTheme.hmi
    val shape = RoundedCornerShape(hmi.chipCornerRadius * 0.75f)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(containerColor)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = hmi.captionFontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 트렁크 보관함 & 여정 로그가 동일하게 사용하는 수직 원터치 페이징 레일 (▲ / 페이지 / ▼)
 */
@Composable
internal fun VerticalSteppedPagingRail(
    currentPage: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi

    Column(
        modifier = modifier
            .width(hmi.minTouchTarget + 12.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(hmi.chipCornerRadius))
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.surfaceBorderSubtle, RoundedCornerShape(hmi.chipCornerRadius))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val canPrev = currentPage > 0
        Box(
            modifier = Modifier
                .size(hmi.minTouchTarget)
                .clip(RoundedCornerShape(hmi.chipCornerRadius * 0.75f))
                .background(if (canPrev) colors.primaryContainer else Color.Transparent)
                .border(
                    1.dp,
                    if (canPrev) colors.primary.copy(alpha = 0.5f) else Color.Transparent,
                    RoundedCornerShape(hmi.chipCornerRadius * 0.75f)
                )
                .clickable(enabled = canPrev, onClick = onPrevPage),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▲",
                color = if (canPrev) colors.primary else colors.textTertiary.copy(alpha = 0.4f),
                fontSize = hmi.captionFontSize,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${currentPage + 1}",
                color = colors.primary,
                fontSize = hmi.captionFontSize,
                fontWeight = FontWeight.Black
            )
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(1.dp)
                    .background(colors.surfaceBorderSubtle)
            )
            Text(
                text = "$totalPages",
                color = colors.textTertiary,
                fontSize = hmi.badgeFontSize,
                fontWeight = FontWeight.Medium
            )
        }

        val canNext = currentPage < totalPages - 1
        Box(
            modifier = Modifier
                .size(hmi.minTouchTarget)
                .clip(RoundedCornerShape(hmi.chipCornerRadius * 0.75f))
                .background(if (canNext) colors.primaryContainer else Color.Transparent)
                .border(
                    1.dp,
                    if (canNext) colors.primary.copy(alpha = 0.5f) else Color.Transparent,
                    RoundedCornerShape(hmi.chipCornerRadius * 0.75f)
                )
                .clickable(enabled = canNext, onClick = onNextPage),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▼",
                color = if (canNext) colors.primary else colors.textTertiary.copy(alpha = 0.4f),
                fontSize = hmi.captionFontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// Jetpack Compose Previews (다크 & 라이트 모드)
// =========================================================================

@Preview(name = "Cargo Live Overview Dark (500x300)", widthDp = 500, heightDp = 300, showBackground = true)
@Composable
fun CargoLiveOverviewWidgetPreviewDark() {
    PleosTheme(darkTheme = true) {
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품 3종)", "신선식품", "🛒", "트렁크 중앙", "14:22", true),
            CargoItemPresentation("2", "투썸 딸기 케이크", "파손주의", "🎂", "트렁크 우측 네트", "14:35", true),
            CargoItemPresentation("3", "타이틀리스트 골프백", "상시보관", "⛳", "트렁크 안쪽", "어제", false)
        )
        CargoLiveOverviewWidget(
            session = TripSession(),
            cargoItems = dummyItems
        )
    }
}

@Preview(name = "Cargo Live Overview Light (500x300)", widthDp = 500, heightDp = 300, showBackground = true)
@Composable
fun CargoLiveOverviewWidgetPreviewLight() {
    PleosTheme(darkTheme = false) {
        val dummyItems = listOf(
            CargoItemPresentation("1", "이마트 장보기 (신선식품 3종)", "신선식품", "🛒", "트렁크 중앙", "14:22", true),
            CargoItemPresentation("2", "투썸 딸기 케이크", "파손주의", "🎂", "트렁크 우측 네트", "14:35", true)
        )
        CargoLiveOverviewWidget(
            session = TripSession(),
            cargoItems = dummyItems
        )
    }
}
