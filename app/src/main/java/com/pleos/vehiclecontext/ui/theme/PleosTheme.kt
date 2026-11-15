package com.pleos.vehiclecontext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pleos.vehiclecontext.R

/**
 * 한/영 혼용 시인성과 차량 디스플레이(AAOS) 가독성을 극대화한 Pretendard 폰트 패밀리
 */
val PleosFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private fun TextStyle.asPleosStyle(
    weight: FontWeight = fontWeight ?: FontWeight.Normal,
    letterSp: Float = -0.2f
): TextStyle = copy(
    fontFamily = PleosFontFamily,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    letterSpacing = letterSp.sp
)

private val BaseTypography = Typography()

val PleosTypography = Typography(
    displayLarge = BaseTypography.displayLarge.asPleosStyle(FontWeight.Bold, -0.4f),
    displayMedium = BaseTypography.displayMedium.asPleosStyle(FontWeight.Bold, -0.3f),
    displaySmall = BaseTypography.displaySmall.asPleosStyle(FontWeight.Bold, -0.2f),
    headlineLarge = BaseTypography.headlineLarge.asPleosStyle(FontWeight.Bold, -0.2f),
    headlineMedium = BaseTypography.headlineMedium.asPleosStyle(FontWeight.Bold, -0.2f),
    headlineSmall = BaseTypography.headlineSmall.asPleosStyle(FontWeight.SemiBold, -0.15f),
    titleLarge = BaseTypography.titleLarge.asPleosStyle(FontWeight.Bold, -0.15f),
    titleMedium = BaseTypography.titleMedium.asPleosStyle(FontWeight.SemiBold, -0.1f),
    titleSmall = BaseTypography.titleSmall.asPleosStyle(FontWeight.SemiBold, -0.1f),
    bodyLarge = BaseTypography.bodyLarge.asPleosStyle(FontWeight.Normal, -0.1f),
    bodyMedium = BaseTypography.bodyMedium.asPleosStyle(FontWeight.Normal, -0.1f),
    bodySmall = BaseTypography.bodySmall.asPleosStyle(FontWeight.Normal, 0f),
    labelLarge = BaseTypography.labelLarge.asPleosStyle(FontWeight.SemiBold, 0f),
    labelMedium = BaseTypography.labelMedium.asPleosStyle(FontWeight.Medium, 0f),
    labelSmall = BaseTypography.labelSmall.asPleosStyle(FontWeight.Medium, 0.1f)
)

/**
 * 현대차 Pleos / ccNC 및 프리미엄 차량용 HMI 표준 Semantic Color 시스템
 */
data class PleosColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceBorder: Color,
    val surfaceBorderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val accentGreen: Color,
    val accentGreenContainer: Color,
    val accentAmber: Color,
    val accentAmberContainer: Color,
    val accentRed: Color,
    val accentRedContainer: Color,
    val accentPurple: Color,
    val accentPurpleContainer: Color,
    val chipBackground: Color,
    val chipBorder: Color
)

// 🌙 다크 테마: 야간 주행 시 눈부심을 최소화한 딥 오닉스/슬레이트 팔레트
val PleosDarkColors = PleosColors(
    isDark = true,
    background = Color(0xFF0C0F17),
    surface = Color(0xFF141923),
    surfaceSecondary = Color(0xFF1B2230),
    surfaceBorder = Color(0xFF273247),
    surfaceBorderSubtle = Color(0xFF1F2839),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    primary = Color(0xFF38BDF8), // 현대 액티브 일렉트릭 블루
    primaryContainer = Color(0x330284C7),
    onPrimary = Color(0xFFFFFFFF),
    accentGreen = Color(0xFF10B981), // 에메랄드 안전
    accentGreenContainer = Color(0x33059669),
    accentAmber = Color(0xFFF59E0B), // 웜 앰버 골드 알림
    accentAmberContainer = Color(0x33D97706),
    accentRed = Color(0xFFEF4444), // 크림슨 경고
    accentRedContainer = Color(0x33DC2626),
    accentPurple = Color(0xFFA855F7),
    accentPurpleContainer = Color(0x337C3AED),
    chipBackground = Color(0xFF18202E),
    chipBorder = Color(0xFF26334A)
)

// ☀️ 라이트 테마: 주간 주행 시 직사광선/반사광에서도 시인성이 탁월한 퓨어 화이트 & 쿨 슬레이트 팔레트
val PleosLightColors = PleosColors(
    isDark = false,
    background = Color(0xFFF3F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFF8FAFC),
    surfaceBorder = Color(0xFFE2E8F0),
    surfaceBorderSubtle = Color(0xFFEDF2F7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    primary = Color(0xFF0284C7), // 현대 딥 스카이 블루
    primaryContainer = Color(0xFFE0F2FE),
    onPrimary = Color(0xFFFFFFFF),
    accentGreen = Color(0xFF059669),
    accentGreenContainer = Color(0xFFD1FAE5),
    accentAmber = Color(0xFFD97706),
    accentAmberContainer = Color(0xFFFEF3C7),
    accentRed = Color(0xFFDC2626),
    accentRedContainer = Color(0xFFFEE2E2),
    accentPurple = Color(0xFF7C3AED),
    accentPurpleContainer = Color(0xFFEDE9FE),
    chipBackground = Color(0xFFF1F5F9),
    chipBorder = Color(0xFFCBD5E1)
)

private val LocalPleosColorsInstance = staticCompositionLocalOf { PleosDarkColors }

// Material 3 ColorScheme 매핑
private val MaterialDarkColorScheme: ColorScheme = darkColorScheme(
    primary = PleosDarkColors.primary,
    onPrimary = Color.Black,
    primaryContainer = PleosDarkColors.primaryContainer,
    onPrimaryContainer = Color.White,
    background = PleosDarkColors.background,
    surface = PleosDarkColors.surface,
    onBackground = PleosDarkColors.textPrimary,
    onSurface = PleosDarkColors.textPrimary,
    outline = PleosDarkColors.surfaceBorder
)

private val MaterialLightColorScheme: ColorScheme = lightColorScheme(
    primary = PleosLightColors.primary,
    onPrimary = Color.White,
    primaryContainer = PleosLightColors.primaryContainer,
    onPrimaryContainer = PleosLightColors.textPrimary,
    background = PleosLightColors.background,
    surface = PleosLightColors.surface,
    onBackground = PleosLightColors.textPrimary,
    onSurface = PleosLightColors.textPrimary,
    outline = PleosLightColors.surfaceBorder
)

/**
 * 현대차 Pleos 통일 테마 프로바이더
 */
@Composable
fun PleosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) PleosDarkColors else PleosLightColors
    val m3Colors = if (darkTheme) MaterialDarkColorScheme else MaterialLightColorScheme
    val hmiMetrics = calculateVehicleHmiMetrics()

    CompositionLocalProvider(
        LocalPleosColorsInstance provides colors,
        LocalVehicleHmiInstance provides hmiMetrics
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            typography = PleosTypography,
            content = content
        )
    }
}

/**
 * UI 어디서든 [PleosTheme.colors] 및 [PleosTheme.hmi]로
 * 현재 테마 색상 및 적응형 차량 규격 토큰에 접근
 */
object PleosTheme {
    val fontFamily: FontFamily = PleosFontFamily

    val colors: PleosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosColorsInstance.current

    val hmi: VehicleHmiMetrics
        @Composable
        @ReadOnlyComposable
        get() = LocalVehicleHmiInstance.current
}
