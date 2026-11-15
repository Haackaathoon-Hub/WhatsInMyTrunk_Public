package com.pleos.vehiclecontext.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.R
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 모든 차량 알림(트렁크 적재 감지, 하차 전 트렁크 짐 알림, 직접 추가/확인 후 갱신 상태)의
 * 규격과 디자인을 하나로 통일하는 공통 팝업 스펙. 내용만 교체하여 재활용합니다.
 */
data class VehicleAlertPopupSpec(
    val appName: String = "WHAT'S IN MY TRUNK?",
    val appTagline: String = "PLEOS CONTEXT SMART ALERT",
    val statusBadge: String,
    val headline: String,
    val itemChips: List<String> = emptyList(),
    val metaLine: String? = null,
    val subtitle: String,
    val isWarningTone: Boolean = true,
    val primaryButtonLabel: String? = null,
    val secondaryButtonLabel: String = "확인할게요",
    val dismissLabel: String = "닫기",
    val tertiaryLinkLabel: String? = null
)

/**
 * 통일된 AAOS 고시인성 대형 팝업 알림 컴포넌트.
 * 공식 앱 로고(ic_launcher)와 앱 이름, 확대된 타이포/칩/터치 타깃을 적용하고
 * [VehicleAlertPopupSpec]의 내용만 바꿔 적재 감지 / 하차 알림 / 확인 후 트렁크 제어에 재사용합니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedVehicleAlertPopup(
    spec: VehicleAlertPopupSpec,
    onPrimaryClick: (() -> Unit)? = null,
    onSecondaryClick: () -> Unit,
    onDismiss: () -> Unit,
    onTertiaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = PleosTheme.colors
    val hmi = PleosTheme.hmi
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val popupMinHeight = minOf(860.dp, (screenHeightDp * 0.86f).dp.coerceAtLeast(430.dp))
    val popupMaxHeight = (screenHeightDp * 0.90f).dp.coerceAtLeast(popupMinHeight)
    val headlineSize = (hmi.displayFontSize.value * 1.12f).sp
    val subtitleSize = (hmi.titleFontSize.value * 1.02f).sp
    val ctaFontSize = (hmi.titleFontSize.value * 0.96f).sp
    val ctaHeight = maxOf(hmi.minTouchTarget * 1.7f, 108.dp)
    val outerCorner = hmi.cardCornerRadius + 26.dp
    val actionShape = RoundedCornerShape(50)
    val chipShape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // 바깥 영역 오터치로 닫히지 않도록 유지
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
            shape = RoundedCornerShape(outerCorner),
            modifier = Modifier
                .padding(hmi.panelPadding)
                .fillMaxWidth(0.78f)
                .widthIn(min = 520.dp, max = 880.dp)
                .heightIn(min = popupMinHeight, max = popupMaxHeight)
                .border(
                    width = 1.8.dp,
                    color = if (spec.isWarningTone) colors.accentAmber.copy(alpha = 0.55f) else colors.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(outerCorner)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = popupMinHeight)
                    .padding(horizontal = hmi.cardPadding * 1.9f, vertical = hmi.cardPadding * 2.1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. 상단 브랜드 헤더: 공식 앱 로고 + 앱 이름 + 둥근 닫기 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceSecondary)
                                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher),
                                contentDescription = "${spec.appName} 로고",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = spec.appName,
                                color = colors.textPrimary,
                                fontSize = hmi.titleFontSize,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = spec.appTagline,
                                color = colors.textTertiary,
                                fontSize = hmi.captionFontSize,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = actionShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 64.dp)
                            .clip(actionShape)
                            .background(colors.surfaceSecondary.copy(alpha = 0.75f), actionShape)
                            .border(1.dp, colors.surfaceBorderSubtle, actionShape)
                    ) {
                        Text(
                            text = spec.dismissLabel,
                            color = colors.textSecondary,
                            fontSize = hmi.subheadFontSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // 2. 세로 2배 높이에 맞춘 넉넉한 중앙 상태 배지 + 메시지 + 품목 칩 영역
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(
                                if (spec.isWarningTone) colors.accentAmberContainer else colors.primaryContainer
                            )
                            .border(
                                1.2.dp,
                                if (spec.isWarningTone) colors.accentAmber.copy(alpha = 0.45f) else colors.primary.copy(alpha = 0.4f),
                                chipShape
                            )
                            .padding(horizontal = 20.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = spec.statusBadge,
                            color = if (spec.isWarningTone) colors.accentAmber else colors.primary,
                            fontSize = hmi.bodyFontSize,
                            lineHeight = (hmi.bodyFontSize.value * 1.25f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = spec.headline,
                            color = colors.textPrimary,
                            fontSize = headlineSize,
                            lineHeight = (headlineSize.value * 1.24f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = spec.subtitle,
                            color = colors.textSecondary,
                            fontSize = subtitleSize,
                            lineHeight = (subtitleSize.value * 1.38f).sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!spec.metaLine.isNullOrBlank()) {
                            Text(
                                text = spec.metaLine,
                                color = colors.textTertiary,
                                fontSize = hmi.bodyFontSize,
                                lineHeight = (hmi.bodyFontSize.value * 1.35f).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (spec.itemChips.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            spec.itemChips.take(4).forEach { chip ->
                                Box(
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .background(colors.surfaceSecondary)
                                        .border(1.2.dp, colors.surfaceBorderSubtle, chipShape)
                                        .padding(horizontal = 24.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        text = chip,
                                        color = colors.textPrimary,
                                        fontSize = hmi.subheadFontSize,
                                        lineHeight = (hmi.subheadFontSize.value * 1.25f).sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 하단 대형 둥근 AAOS 액션 버튼 및 보조 링크 영역
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!spec.primaryButtonLabel.isNullOrBlank() && onPrimaryClick != null) {
                            Button(
                                onClick = onPrimaryClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = Color.White
                                ),
                                shape = actionShape,
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 22.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = ctaHeight)
                            ) {
                                Text(
                                    text = spec.primaryButtonLabel,
                                    color = Color.White,
                                    fontSize = ctaFontSize,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = onSecondaryClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (spec.primaryButtonLabel == null) colors.primary else colors.surfaceSecondary,
                                contentColor = if (spec.primaryButtonLabel == null) Color.White else colors.textPrimary
                            ),
                            shape = actionShape,
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = ctaHeight)
                                .then(
                                    if (spec.primaryButtonLabel != null) {
                                        Modifier.border(
                                            width = 1.6.dp,
                                            color = colors.surfaceBorder,
                                            shape = actionShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = spec.secondaryButtonLabel,
                                color = if (spec.primaryButtonLabel == null) Color.White else colors.textPrimary,
                                fontSize = ctaFontSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!spec.tertiaryLinkLabel.isNullOrBlank() && onTertiaryClick != null) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "${spec.tertiaryLinkLabel} →",
                                color = colors.primary,
                                fontSize = hmi.subheadFontSize,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(actionShape)
                                    .clickable { onTertiaryClick() }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 하차 전/주행 알림 오버레이 카드 ([UnifiedVehicleAlertPopup] 규격을 재활용).
 */
@Composable
fun PreExitOverlayContent(
    prompt: ContextAction,
    session: TripSession,
    onAcknowledge: () -> Unit,
    onExecuteControl: (VehicleControlCommand) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rawPrimary = prompt.availableControls.firstOrNull()
    val effectivePrimary = if (rawPrimary == VehicleControlCommand.OPEN_TRUNK && DoorZone.TRUNK in session.openDoors) {
        VehicleControlCommand.CLOSE_TRUNK
    } else {
        rawPrimary
    }
    val secondaryControl = prompt.availableControls.drop(1).firstOrNull()

    val spec = remember(prompt, session, effectivePrimary) {
        buildPreExitAlertSpec(
            prompt = prompt,
            session = session,
            primaryButtonLabel = effectivePrimary?.buttonLabel,
            tertiaryLinkLabel = secondaryControl?.buttonLabel
        )
    }

    UnifiedVehicleAlertPopup(
        spec = spec,
        onPrimaryClick = effectivePrimary?.let { cmd -> { onExecuteControl(cmd) } },
        onSecondaryClick = onAcknowledge,
        onDismiss = onDismiss,
        onTertiaryClick = secondaryControl?.let { cmd -> { onExecuteControl(cmd) } },
        modifier = modifier
    )
}

/**
 * 하차 전 짐 알림 스펙 빌더
 */
fun buildPreExitAlertSpec(
    prompt: ContextAction,
    session: TripSession,
    primaryButtonLabel: String? = prompt.availableControls.firstOrNull()?.buttonLabel,
    tertiaryLinkLabel: String? = prompt.availableControls.drop(1).firstOrNull()?.buttonLabel
): VehicleAlertPopupSpec {
    val activeItems = session.cargoInventory
        .filter { it.zone == DoorZone.TRUNK && !it.isPermanent && !it.isUnloaded }
        .map { "${it.name} (${it.amountLabel()})" }
        .distinct()
        .ifEmpty {
            session.aiCargoMemory?.inferredItems
                ?.map { it.substringBefore(" (").trim() }
                ?.distinct()
                ?: emptyList()
        }

    val displayChips = if (activeItems.size <= 4) {
        activeItems
    } else {
        activeItems.take(3) + "+${activeItems.size - 3}건 더보기"
    }

    val gearLabel = if (session.currentGear == GearState.PARK) "P단 주차" else session.currentGear.label
    val trunkStatus = if (DoorZone.TRUNK in session.openDoors) "테일게이트 열림" else "테일게이트 닫힘"

    return when (prompt.scenarioId) {
        "trunk_reminder_mvp" -> {
            val hasPerishable = session.aiCargoMemory?.isTemperatureSensitive == true ||
                session.cargoInventory.any { it.isPerishable && !it.isUnloaded }
            VehicleAlertPopupSpec(
                statusBadge = "${session.destinationName} · $gearLabel",
                headline = "내리기 전 트렁크 짐을 확인하세요",
                itemChips = displayChips,
                subtitle = if (hasPerishable) {
                    "$trunkStatus · 신선 보관 물품이 트렁크에 있습니다."
                } else {
                    "$trunkStatus · 하차 전 트렁크 짐을 챙겨주세요."
                },
                isWarningTone = true,
                primaryButtonLabel = primaryButtonLabel,
                secondaryButtonLabel = prompt.primaryAckLabel,
                dismissLabel = prompt.dismissLabel,
                tertiaryLinkLabel = tertiaryLinkLabel
            )
        }
        "destination_reverse_reminder" -> VehicleAlertPopupSpec(
            statusBadge = "${session.destinationName} · $gearLabel",
            headline = "상시 보관 물품을 꺼낼까요?",
            itemChips = session.permanentTrunkNotes.take(3).toList(),
            subtitle = "$trunkStatus · 목적지에 맞는 상시 보관 물품이 있습니다.",
            isWarningTone = false,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
        "cargo_temperature_guard" -> VehicleAlertPopupSpec(
            statusBadge = "실내 ${String.format("%.1f", session.cabinTempCelsius)}°C 주의",
            headline = "신선식품 온도 주의",
            itemChips = displayChips,
            subtitle = "공조 급속 냉방을 켜 적재물을 보호할까요?",
            isWarningTone = true,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
        "autonomous_cargo_comfort_profile" -> VehicleAlertPopupSpec(
            statusBadge = "자율주행 적재 보호",
            headline = "Comfort 모드로 전환할까요?",
            itemChips = displayChips,
            subtitle = "가감속과 차선 변경을 부드럽게 제어합니다.",
            isWarningTone = false,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
        "rear_seat_item_guard" -> VehicleAlertPopupSpec(
            statusBadge = "${session.destinationName} · $gearLabel",
            headline = "뒷좌석 소지품을 확인하세요",
            subtitle = "2열 도어 개폐 후 미수거 물품이 감지되었습니다.",
            isWarningTone = true,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
        "exit_integrity_guard" -> VehicleAlertPopupSpec(
            statusBadge = "$gearLabel · 하차 보안",
            headline = "열린 창문 ${session.openWindowsCount}개를 닫을까요?",
            subtitle = "하차 전 열려 있는 창문을 한 번에 닫습니다.",
            isWarningTone = true,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
        "autonomous_valet_dropoff_guard" -> VehicleAlertPopupSpec(
            statusBadge = "자율발렛(AVP) 대기",
            headline = "출발 전 짐을 먼저 꺼내세요",
            itemChips = displayChips,
            subtitle = "트렁크 수거 완료 후 발렛 주차를 진행합니다.",
            isWarningTone = true,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
        else -> VehicleAlertPopupSpec(
            statusBadge = "${session.destinationName} · $gearLabel",
            headline = prompt.headline,
            itemChips = displayChips,
            subtitle = "$trunkStatus · ${prompt.bodyMessage.lineSequence().firstOrNull().orEmpty()}",
            isWarningTone = true,
            primaryButtonLabel = primaryButtonLabel,
            secondaryButtonLabel = prompt.primaryAckLabel,
            dismissLabel = prompt.dismissLabel,
            tertiaryLinkLabel = tertiaryLinkLabel
        )
    }
}

/**
 * 트렁크 적재 감지 시 동일 규격 팝업에 내용만 교체해 사용하는 스펙 빌더
 */
fun buildTrunkLoadingDetectedSpec(session: TripSession): VehicleAlertPopupSpec {
    val originPlace = session.originName.trim().ifBlank {
        session.originPoi.takeIf { it != PoiCategory.UNKNOWN }?.displayName ?: "현위치"
    }
    val destPlace = session.destinationName.trim().ifBlank { "목적지 미정" }
    val recentTag = session.latestTrunkUsage?.cargoTag?.displayName ?: "트렁크 적재"
    val chips = listOf(
        "📍 $originPlace → $destPlace",
        "📦 $recentTag"
    )
    return VehicleAlertPopupSpec(
        statusBadge = "트렁크 적재 감지 · 닫힘 완료",
        headline = "트렁크에 짐이 실렸나요?",
        itemChips = chips,
        subtitle = "도착 후 P단 주차 시 하차 알림을 자동으로 띄워드려요.",
        isWarningTone = false,
        primaryButtonLabel = "➕ 품목 직접 추가",
        secondaryButtonLabel = "확인했어요 (닫기)",
        dismissLabel = "새로 실은 짐 없음"
    )
}

/**
 * '직접 추가' 후 트렁크를 자동 개방하지 않고 선택적 열기/닫기 제어를 제안하는 통일 규격 팝업 스펙 빌더
 */
fun buildCargoConfirmedPopupSpec(
    itemName: String,
    targetDestination: String,
    session: TripSession
): VehicleAlertPopupSpec {
    val trunkOpen = DoorZone.TRUNK in session.openDoors
    val destLabel = targetDestination.ifBlank { session.destinationName.ifBlank { "상시 보관" } }
    return VehicleAlertPopupSpec(
        statusBadge = if (trunkOpen) "등록 완료 · 트렁크 열림" else "등록 완료 · 트렁크 닫힘",
        headline = "화물 등록을 완료했어요",
        itemChips = listOf(
            "📦 $itemName",
            "🏁 $destLabel"
        ),
        subtitle = if (trunkOpen) {
            "적재가 끝나면 아래 버튼이나 물리 버튼으로 닫을 수 있어요."
        } else {
            "트렁크를 열지 않았어요. 필요 시 아래 버튼으로 열 수 있어요."
        },
        isWarningTone = false,
        primaryButtonLabel = if (trunkOpen) "🔒 트렁크 닫기" else "🚗 트렁크 열기",
        secondaryButtonLabel = "확인 (완료)",
        dismissLabel = "닫기"
    )
}
