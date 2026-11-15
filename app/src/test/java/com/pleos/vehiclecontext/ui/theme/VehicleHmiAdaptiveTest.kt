package com.pleos.vehiclecontext.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 차량 수직계열화 HMI 디자인 토큰(VehicleHmiMetrics) 적응형 분기 및 안전 하한선 검증 테스트
 */
class VehicleHmiAdaptiveTest {

    @Test
    fun `마스터 배율 기본값은 차량 내 젊은 성인 표준 1_0f로 고정된다`() {
        assertEquals(1.0f, HMI_MASTER_SCALE, 0.0001f)

        val baseline = resolveVehicleHmiMetrics(screenWidthDp = 1682)
        assertEquals(VehicleDisplayClass.ULTRA_WIDE, baseline.displayClass)
        assertEquals(1.0f, baseline.scaleFactor, 0.0001f)
        assertEquals(24f, baseline.bodyFontSize.value, 0.01f)
        assertEquals(30f, baseline.titleFontSize.value, 0.01f)
        assertEquals(62f, baseline.minTouchTarget.value, 0.01f)
    }

    @Test
    fun `디스플레이 폭 브레이크포인트에 따라 3단계 등급과 적응형 플래그가 전환된다`() {
        val ultraWide = resolveVehicleHmiMetrics(screenWidthDp = 1450)
        assertEquals(VehicleDisplayClass.ULTRA_WIDE, ultraWide.displayClass)
        assertTrue(ultraWide.isSideBySideStage)
        assertFalse(ultraWide.isCompactHeader)

        val standardWide = resolveVehicleHmiMetrics(screenWidthDp = 1200)
        assertEquals(VehicleDisplayClass.STANDARD_WIDE, standardWide.displayClass)
        assertTrue(standardWide.isSideBySideStage)
        assertFalse(standardWide.isCompactHeader)

        val compactSplit = resolveVehicleHmiMetrics(screenWidthDp = 959)
        assertEquals(VehicleDisplayClass.COMPACT_SPLIT, compactSplit.displayClass)
        assertFalse(compactSplit.isSideBySideStage)
        assertTrue(compactSplit.isCompactHeader)
    }

    @Test
    fun `작은 분할 화면이나 낮은 배율에서도 차량용 최소 시인성 및 48dp 터치 하한선을 보장한다`() {
        val cases = listOf(
            resolveVehicleHmiMetrics(screenWidthDp = 1682, masterScale = 0.5f),
            resolveVehicleHmiMetrics(screenWidthDp = 1100, masterScale = 0.5f),
            resolveVehicleHmiMetrics(screenWidthDp = 720, masterScale = 0.5f)
        )

        cases.forEach { metrics ->
            assertTrue(
                "Expected minTouchTarget >= 48dp in ${metrics.displayClass}, was ${metrics.minTouchTarget.value}",
                metrics.minTouchTarget.value >= 48f
            )
            assertTrue(
                "Expected bodyFontSize >= 19sp in ${metrics.displayClass}, was ${metrics.bodyFontSize.value}",
                metrics.bodyFontSize.value >= 19f
            )
            assertTrue(
                "Expected captionFontSize >= 17sp in ${metrics.displayClass}, was ${metrics.captionFontSize.value}",
                metrics.captionFontSize.value >= 17f
            )
            assertTrue(
                "Expected badgeFontSize >= 15.5sp in ${metrics.displayClass}, was ${metrics.badgeFontSize.value}",
                metrics.badgeFontSize.value >= 15.5f
            )
        }
    }

    @Test
    fun `고시인성 마스터 배율 확대 시 타이포그래피와 터치 타겟이 비례 확장된다`() {
        val standard = resolveVehicleHmiMetrics(screenWidthDp = 1682, masterScale = 1.0f)
        val enlarged = resolveVehicleHmiMetrics(screenWidthDp = 1682, masterScale = 1.25f)

        assertTrue(enlarged.bodyFontSize.value > standard.bodyFontSize.value)
        assertTrue(enlarged.titleFontSize.value > standard.titleFontSize.value)
        assertTrue(enlarged.minTouchTarget.value > standard.minTouchTarget.value)
    }
}
