package com.pleos.vehiclecontext.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.ui.theme.PleosTheme

private val TwinBg = Color(0xFF0B101B)
private val BodyFill = Color(0xFF1E293B)
private val BodyStroke = Color(0xFF475569)
private val GlassFill = Color(0xFF0F172A)
private val NeonCyan = Color(0xFF38BDF8)
private val NeonAmber = Color(0xFFF59E0B)
private val NeonPurple = Color(0xFFA855F7)
private val NeonGreen = Color(0xFF10B981)
private val NeonRed = Color(0xFFEF4444)

/**
 * 실제 차량용 클러스터/IVI 스타일의 Top-Down 디지털 트윈 그래픽 컴포넌트.
 * Jetpack Compose Canvas로 차량 외형, 윈드실드, 1열/2열 시트 공간, 트렁크 베이, 창문,
 * 그리고 자율주행(AD/AVP) 센서 쉴드와 트렁크/2열 적재 하이라이트를 실시간 렌더링합니다.
 */
@Composable
fun VehicleDigitalTwinCard(
    session: TripSession,
    modifier: Modifier = Modifier
) {
    val hmi = PleosTheme.hmi
    val isTrunkActive = session.hasActiveTrunkUsage
    val isTrunkOpenNow = DoorZone.TRUNK in session.openDoors
    val isRearSeatItemActive = session.hasUnoccupiedRearSeatLoading
    val hasOpenWindows = session.openWindowsCount > 0
    val isAutonomousActive = session.autonomousMode != AutonomousDrivingMode.MANUAL

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TwinBg, RoundedCornerShape(hmi.cardCornerRadius))
            .border(1.dp, Color(0xFF26354D), RoundedCornerShape(hmi.cardCornerRadius))
            .padding(hmi.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(hmi.itemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 좌측: Canvas 기반 차량 탑뷰 디지털 트윈 렌더링
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(230.dp)
                .background(Color(0xFF080C14), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val carW = size.width * 0.54f
                val carH = size.height * 0.78f
                val left = cx - carW / 2f
                val top = cy - carH / 2f
                val right = cx + carW / 2f
                val bottom = cy + carH / 2f

                // 1. 자율주행(AD/AVP) 활성화 시 주변 레이더 쉴드 링 렌더링
                if (isAutonomousActive) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NeonPurple.copy(alpha = 0.25f),
                                NeonCyan.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = size.width * 0.52f
                        ),
                        center = Offset(cx, cy),
                        radius = size.width * 0.52f
                    )
                    drawArc(
                        color = NeonPurple.copy(alpha = 0.7f),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(cx - carW * 0.78f, top - 16f),
                        size = Size(carW * 1.56f, carW * 0.9f),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }

                // 2. 타이어 4개 렌더링
                val wheelW = 8f
                val wheelH = 26f
                val wheelColor = Color(0xFF334155)
                // 전륜 좌/우
                drawRoundRect(
                    color = wheelColor,
                    topLeft = Offset(left - wheelW + 2f, top + carH * 0.16f),
                    size = Size(wheelW, wheelH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = wheelColor,
                    topLeft = Offset(right - 2f, top + carH * 0.16f),
                    size = Size(wheelW, wheelH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // 후륜 좌/우
                drawRoundRect(
                    color = wheelColor,
                    topLeft = Offset(left - wheelW + 2f, top + carH * 0.68f),
                    size = Size(wheelW, wheelH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = wheelColor,
                    topLeft = Offset(right - 2f, top + carH * 0.68f),
                    size = Size(wheelW, wheelH),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // 3. 차체 메인 실루엣
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF263349), BodyFill, Color(0xFF172030))
                    ),
                    topLeft = Offset(left, top),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(28f, 28f)
                )
                drawRoundRect(
                    color = BodyStroke,
                    topLeft = Offset(left, top),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(28f, 28f),
                    style = Stroke(width = 2.5f)
                )

                // 4. 전방 헤드램프 시그니처 라이트 바 (현대 심리스 호라이즌 램프 스타일)
                drawLine(
                    color = NeonCyan,
                    start = Offset(left + 12f, top + 5f),
                    end = Offset(right - 12f, top + 5f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )

                // 5. 프론트 윈드실드 (앞유리)
                val windshieldPath = Path().apply {
                    moveTo(left + 10f, top + carH * 0.18f)
                    lineTo(right - 10f, top + carH * 0.18f)
                    lineTo(right - 7f, top + carH * 0.30f)
                    lineTo(left + 7f, top + carH * 0.30f)
                    close()
                }
                drawPath(path = windshieldPath, color = GlassFill)

                // 6. 실내 캐빈 (1열 + 2열 루프 글래스)
                val cabinTop = top + carH * 0.31f
                val cabinHeight = carH * 0.40f
                drawRoundRect(
                    color = GlassFill,
                    topLeft = Offset(left + 7f, cabinTop),
                    size = Size(carW - 14f, cabinHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )

                // 실내 고온 주의(26℃ 이상) 또는 급속 냉방(20.5℃ 이하) 시각화
                if (session.cabinTempCelsius >= 26.0f) {
                    drawRoundRect(
                        color = NeonRed.copy(alpha = 0.28f),
                        topLeft = Offset(left + 7f, cabinTop),
                        size = Size(carW - 14f, cabinHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = NeonRed.copy(alpha = 0.7f),
                        topLeft = Offset(left + 7f, cabinTop),
                        size = Size(carW - 14f, cabinHeight),
                        cornerRadius = CornerRadius(10f, 10f),
                        style = Stroke(width = 2f)
                    )
                } else if (session.cabinTempCelsius <= 20.5f) {
                    drawRoundRect(
                        color = NeonCyan.copy(alpha = 0.22f),
                        topLeft = Offset(left + 7f, cabinTop),
                        size = Size(carW - 14f, cabinHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.75f),
                        start = Offset(left + 12f, cabinTop + cabinHeight * 0.35f),
                        end = Offset(right - 12f, cabinTop + cabinHeight * 0.35f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.75f),
                        start = Offset(left + 12f, cabinTop + cabinHeight * 0.65f),
                        end = Offset(right - 12f, cabinTop + cabinHeight * 0.65f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }

                // 2열 뒷좌석 소지품 감지 시 하이라이트
                val rearSeatTop = top + carH * 0.51f
                val rearSeatHeight = carH * 0.19f
                if (isRearSeatItemActive) {
                    drawRoundRect(
                        color = NeonCyan.copy(alpha = 0.35f),
                        topLeft = Offset(left + 9f, rearSeatTop),
                        size = Size(carW - 18f, rearSeatHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = NeonCyan,
                        topLeft = Offset(left + 9f, rearSeatTop),
                        size = Size(carW - 18f, rearSeatHeight),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(width = 2f)
                    )
                }

                // 창문 열림 감지 시 좌/우 윈도우 라인 경고 표시
                if (hasOpenWindows) {
                    drawLine(
                        color = NeonRed,
                        start = Offset(left + 3f, cabinTop + 6f),
                        end = Offset(left + 3f, cabinTop + cabinHeight - 6f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = NeonRed,
                        start = Offset(right - 3f, cabinTop + 6f),
                        end = Offset(right - 3f, cabinTop + cabinHeight - 6f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }

                // 7. ★ 트렁크 적재 영역 (Rear Cargo Bay) 하이라이트
                val trunkTop = top + carH * 0.73f
                val trunkHeight = carH * 0.23f
                val trunkFillColor = when {
                    isTrunkOpenNow -> NeonRed.copy(alpha = 0.55f)
                    isTrunkActive -> NeonAmber.copy(alpha = 0.45f)
                    else -> Color(0xFF141D2B)
                }
                val trunkStrokeColor = when {
                    isTrunkOpenNow -> NeonRed
                    isTrunkActive -> NeonAmber
                    else -> Color(0xFF334155)
                }

                if (isTrunkActive || isTrunkOpenNow) {
                    // 트렁크 주변 글로우 효과
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                trunkStrokeColor.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            center = Offset(cx, trunkTop + trunkHeight / 2f),
                            radius = carW * 0.65f
                        ),
                        center = Offset(cx, trunkTop + trunkHeight / 2f),
                        radius = carW * 0.65f
                    )
                }

                drawRoundRect(
                    color = trunkFillColor,
                    topLeft = Offset(left + 8f, trunkTop),
                    size = Size(carW - 16f, trunkHeight),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = trunkStrokeColor,
                    topLeft = Offset(left + 8f, trunkTop),
                    size = Size(carW - 16f, trunkHeight),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = if (isTrunkActive || isTrunkOpenNow) 3f else 1.5f)
                )

                // 후방 테일램프 바
                drawLine(
                    color = if (isTrunkActive || isTrunkOpenNow) NeonAmber else Color(0xFFEF4444).copy(alpha = 0.7f),
                    start = Offset(left + 12f, bottom - 4f),
                    end = Offset(right - 12f, bottom - 4f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 우측: 차량 공간별 센서 감지 상태 요약 인디케이터
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(hmi.itemSpacing * 0.65f)
        ) {
            Text(
                text = "차량 공간 디지털 트윈 (실시간 센서 매핑)",
                color = NeonCyan,
                fontSize = hmi.captionFontSize,
                fontWeight = FontWeight.ExtraBold
            )

            ZoneStatusRow(
                dotColor = when {
                    isTrunkOpenNow -> NeonRed
                    isTrunkActive -> NeonAmber
                    else -> Color(0xFF64748B)
                },
                zoneTitle = "트렁크 적재 공간 (Vehicle SDK Door)",
                statusText = when {
                    isTrunkOpenNow -> "현재 트렁크 열림 상태"
                    isTrunkActive -> {
                        val poi = session.latestTrunkUsage?.openedAtPoi?.displayName ?: "출발지"
                        val aiItems = session.aiCargoMemory?.inferredItems?.joinToString(", ")
                        if (!aiItems.isNullOrBlank()) {
                            "적재 감지됨 ($poi) · AI: $aiItems"
                        } else {
                            "이번 이동 개폐 이력 기록됨 ($poi) — 하차 전 안내 예약"
                        }
                    }
                    else -> "이번 이동 개폐 이력 없음 (하차 안내 생략)"
                }
            )

            ZoneStatusRow(
                dotColor = if (isRearSeatItemActive) NeonCyan else Color(0xFF64748B),
                zoneTitle = "2열 실내 공간 (도어 + 시트 착석 센서)",
                statusText = if (isRearSeatItemActive) {
                    "2열 도어 개폐 후 미착석 → 뒷좌석 소지품 적재 추론"
                } else {
                    "소지품 적재 이상 없음"
                }
            )

            ZoneStatusRow(
                dotColor = when {
                    hasOpenWindows || session.cabinTempCelsius >= 26.0f -> NeonRed
                    session.cabinTempCelsius <= 20.5f -> NeonCyan
                    else -> NeonGreen
                },
                zoneTitle = "창문·실내 공조 환경 (${session.cabinTempCelsius}°C) 및 자율주행",
                statusText = buildString {
                    append(if (hasOpenWindows) "열린 창문 ${session.openWindowsCount}개 감지 · " else "창문 모두 닫힘 · ")
                    append(
                        when {
                            session.cabinTempCelsius >= 26.0f -> "고온 주의(${session.cabinTempCelsius}°C) · "
                            session.cabinTempCelsius <= 20.5f -> "급속 냉방 중(${session.cabinTempCelsius}°C) · "
                            else -> "적정 온도 · "
                        }
                    )
                    append("${session.autonomousMode.displayName} (${session.autonomousProfile.displayName})")
                }
            )

            ZoneStatusRow(
                dotColor = if (session.permanentTrunkNotes.isNotEmpty()) NeonGreen else Color(0xFF64748B),
                zoneTitle = "트렁크 상시 보관 인벤토리 (${session.permanentTrunkNotes.size}개 등록)",
                statusText = if (session.permanentTrunkNotes.isNotEmpty()) {
                    "상시 보관 중: ${session.permanentTrunkNotes.joinToString(", ")} (도착지 역제안 연동)"
                } else {
                    "등록된 상시 보관 품목 없음 (하단 인벤토리 칩에서 관리)"
                }
            )
        }
    }
}

@Composable
private fun ZoneStatusRow(
    dotColor: Color,
    zoneTitle: String,
    statusText: String
) {
    val hmi = PleosTheme.hmi
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Column {
            Text(
                text = zoneTitle,
                color = Color(0xFFF8FAFC),
                fontSize = hmi.badgeFontSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = statusText,
                color = Color(0xFF94A3B8),
                fontSize = hmi.badgeFontSize,
                lineHeight = hmi.bodyLineHeight * 0.85f
            )
        }
    }
}

