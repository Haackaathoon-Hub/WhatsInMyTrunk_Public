package com.pleos.vehiclecontext.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * =========================================================================
 * 🎛️ [차량 HMI 표준 마스터 배율] HMI_MASTER_SCALE = 1.0f
 * =========================================================================
 * - 1.00f : 차량(Pleos 160 DPI) 탑승 젊은 성인 기준 순정 표준 규격 (SSOT 기본값)
 * - 1.15f ~ 1.30f : 시니어 / 주행 고시인성 확대 모드
 *
 * 💡 모든 UI 컴포넌트는 생숫자(.sp/.dp) 대신 PleosTheme.hmi 토큰을 참조하며,
 *    Pleos 160 DPI(80cm 주행 시청거리)에서 순정 클러스터와 동일한 시인성을 확보합니다.
 */
const val HMI_MASTER_SCALE: Float = 1.0f

/**
 * 현대차 Pleos / AAOS 차량용 적응형 디스플레이 등급 (Vehicle Display Class)
 */
enum class VehicleDisplayClass {
    ULTRA_WIDE,      // 2560x1440 광폭 파노라믹 디스플레이 단독/메인 윈도우 (>= 1450dp)
    STANDARD_WIDE,   // 12.3인치 표준 규격 or 2/3 분할 윈도우 (960dp ~ 1449dp)
    COMPACT_SPLIT    // 1/3 내비게이션 분할 화면 or 사이드 미니 위젯 (< 960dp)
}

/**
 * 차량 수직계열화 표준 HMI 디자인 토큰 (Vehicle HMI Metrics)
 * - 타이포그래피 위계, 아이콘, 터치 타겟, 바/카드 규격, 적응형 레이아웃 모드를 단일 체계로 통합
 */
data class VehicleHmiMetrics(
    val displayClass: VehicleDisplayClass,
    val scaleFactor: Float,

    // 1. 타이포그래피 위계
    val displayFontSize: TextUnit,
    val headlineFontSize: TextUnit,
    val titleFontSize: TextUnit,
    val subheadFontSize: TextUnit,
    val bodyFontSize: TextUnit,
    val captionFontSize: TextUnit,
    val badgeFontSize: TextUnit,
    val bodyLineHeight: TextUnit,

    // 2. 아이콘 규격
    val heroIconSize: TextUnit,
    val iconLargeSize: TextUnit,
    val iconMediumSize: TextUnit,
    val iconSmallSize: TextUnit,

    // 3. 글로벌 크롬 & 네비게이션 규격
    val topBarHeight: Dp,
    val topHeaderHeight: Dp,
    val navRailWidth: Dp,
    val navRailItemHeight: Dp,
    val actionDeckHeight: Dp,
    val sidePanelWidth: Dp,

    // 4. 카드·칩·터치 타겟 & 간격 규격
    val minTouchTarget: Dp,
    val chipHeight: Dp,
    val cargoCardWidth: Dp,
    val cargoCardHeight: Dp,
    val vehicleTwinWidth: Dp,
    val cardCornerRadius: Dp,
    val chipCornerRadius: Dp,
    val cardPadding: Dp,
    val panelPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,

    // 5. 적응형 레이아웃 전환 플래그
    val isSideBySideStage: Boolean,
    val isCompactHeader: Boolean
)

private fun scaledDp(base: Float, scale: Float, minFloor: Float = 0f): Dp =
    maxOf(base * scale, minFloor).dp

private fun scaledSp(base: Float, scale: Float, minFloor: Float = 16f): TextUnit =
    maxOf(base * scale, minFloor).sp

private fun buildScaledHmiMetrics(
    displayClass: VehicleDisplayClass,
    effectiveScale: Float,
    isSideBySideStage: Boolean,
    isCompactHeader: Boolean
): VehicleHmiMetrics {
    val s = effectiveScale.coerceAtLeast(0.78f)
    return VehicleHmiMetrics(
        displayClass = displayClass,
        scaleFactor = s,

        displayFontSize = scaledSp(44f, s, minFloor = 32f),
        headlineFontSize = scaledSp(36f, s, minFloor = 26f),
        titleFontSize = scaledSp(30f, s, minFloor = 23f),
        subheadFontSize = scaledSp(27f, s, minFloor = 21f),
        bodyFontSize = scaledSp(24f, s, minFloor = 19f),
        captionFontSize = scaledSp(21f, s, minFloor = 17f),
        badgeFontSize = scaledSp(19f, s, minFloor = 15.5f),
        bodyLineHeight = scaledSp(34f, s, minFloor = 26f),

        heroIconSize = scaledSp(56f, s, minFloor = 42f),
        iconLargeSize = scaledSp(40f, s, minFloor = 30f),
        iconMediumSize = scaledSp(30f, s, minFloor = 24f),
        iconSmallSize = scaledSp(24f, s, minFloor = 19f),

        topBarHeight = scaledDp(104f, s, minFloor = 88f),
        topHeaderHeight = scaledDp(88f, s, minFloor = 76f),
        navRailWidth = scaledDp(168f, s, minFloor = 140f),
        navRailItemHeight = scaledDp(116f, s, minFloor = 96f),
        actionDeckHeight = scaledDp(142f, s, minFloor = 120f),
        sidePanelWidth = scaledDp(500f, s, minFloor = 400f),

        minTouchTarget = scaledDp(62f, s, minFloor = 52f),
        chipHeight = scaledDp(56f, s, minFloor = 46f),
        cargoCardWidth = scaledDp(540f, s, minFloor = 380f),
        cargoCardHeight = scaledDp(280f, s, minFloor = 220f),
        vehicleTwinWidth = scaledDp(320f, s, minFloor = 220f),
        cardCornerRadius = scaledDp(22f, s, minFloor = 16f),
        chipCornerRadius = scaledDp(14f, s, minFloor = 10f),
        cardPadding = scaledDp(24f, s, minFloor = 16f),
        panelPadding = scaledDp(24f, s, minFloor = 16f),
        itemSpacing = scaledDp(16f, s, minFloor = 10f),
        sectionSpacing = scaledDp(20f, s, minFloor = 14f),

        isSideBySideStage = isSideBySideStage,
        isCompactHeader = isCompactHeader
    )
}

/**
 * [모드 1] ULTRA_WIDE: 젊은 성인 기준 차량용 1.0f 순정 콕핏 표준 규격
 */
fun buildUltraWideHmiMetrics(masterScale: Float = HMI_MASTER_SCALE): VehicleHmiMetrics =
    buildScaledHmiMetrics(
        displayClass = VehicleDisplayClass.ULTRA_WIDE,
        effectiveScale = masterScale,
        isSideBySideStage = true,
        isCompactHeader = false
    )

/**
 * [모드 2] STANDARD_WIDE: 12.3인치 표준 디스플레이 or 2/3 분할 화면 (시인성 유지)
 */
fun buildStandardWideHmiMetrics(masterScale: Float = HMI_MASTER_SCALE): VehicleHmiMetrics =
    buildScaledHmiMetrics(
        displayClass = VehicleDisplayClass.STANDARD_WIDE,
        effectiveScale = masterScale * 0.94f,
        isSideBySideStage = true,
        isCompactHeader = false
    )

/**
 * [모드 3] COMPACT_SPLIT: 1/3 분할 화면 or 사이드 미니 위젯 (Safety Floor + 수직 스택 전환)
 */
fun buildCompactSplitHmiMetrics(masterScale: Float = HMI_MASTER_SCALE): VehicleHmiMetrics =
    buildScaledHmiMetrics(
        displayClass = VehicleDisplayClass.COMPACT_SPLIT,
        effectiveScale = masterScale * 0.86f,
        isSideBySideStage = false,
        isCompactHeader = true
    )

val LocalVehicleHmiInstance = staticCompositionLocalOf { buildUltraWideHmiMetrics() }

/**
 * 화면 너비(Dp)를 기준으로 적절한 디스플레이 등급과 HMI 토큰을 실시간 계산
 */
fun resolveVehicleHmiMetrics(
    screenWidthDp: Int,
    masterScale: Float = HMI_MASTER_SCALE
): VehicleHmiMetrics = when {
    screenWidthDp >= 1450 -> buildUltraWideHmiMetrics(masterScale)
    screenWidthDp >= 960 -> buildStandardWideHmiMetrics(masterScale)
    else -> buildCompactSplitHmiMetrics(masterScale)
}

/**
 * 현재 디스플레이 해상도(Dp)에 따라 적응형 HMI 규격을 실시간 자동 계산
 */
@Composable
fun calculateVehicleHmiMetrics(): VehicleHmiMetrics {
    val configuration = LocalConfiguration.current
    return resolveVehicleHmiMetrics(configuration.screenWidthDp, HMI_MASTER_SCALE)
}
