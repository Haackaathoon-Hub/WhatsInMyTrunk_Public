package com.pleos.vehiclecontext

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.engine.InMemorySessionPersistence
import com.pleos.vehiclecontext.core.model.AiCargoMemory
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ClosureUsageRecord
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.signal.MockSignalProvider
import com.pleos.vehiclecontext.core.signal.PleosSignalProvider
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousCargoComfortScenario
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousValetDropoffScenario
import com.pleos.vehiclecontext.scenarios.cabin.CargoTemperatureGuardScenario
import com.pleos.vehiclecontext.scenarios.cabin.RearSeatItemScenario
import com.pleos.vehiclecontext.scenarios.driving.CargoSafeDrivingScenario
import com.pleos.vehiclecontext.scenarios.exit.ExitIntegrityScenario
import com.pleos.vehiclecontext.scenarios.trunk.DestinationReverseReminderScenario
import com.pleos.vehiclecontext.scenarios.trunk.TrunkReminderScenario
import com.pleos.vehiclecontext.ui.MainViewModel
import com.pleos.vehiclecontext.ui.model.FreshnessRiskLevel
import com.pleos.vehiclecontext.ui.model.UserScreenDataMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Milestone 1 Empirical Stress & State Synchronization Test Suite
 *
 * Designed by empirical_challenger to rigorously verify:
 * 1. Rapid repeated preset invocations & alternation sequences
 * 2. State synchronization consistency between session, trunkCargoItems, and freshnessStatus
 * 3. Edge cases: empty cargo, zero/negative time diffs, extreme temperatures (-20°C to 60°C)
 * 4. Concurrent signal processing and preset execution
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Milestone1EmpiricalStressTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var aiService: PleosAiContextAgent
    private lateinit var engine: TripContextEngine
    private lateinit var pleosSignalProvider: PleosSignalProvider
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        persistence = InMemorySessionPersistence()
        actionDispatcher = ActionDispatcher()
        aiService = PleosAiContextAgent()

        val plugins = listOf(
            AutonomousValetDropoffScenario(),
            AutonomousCargoComfortScenario(),
            CargoSafeDrivingScenario(),
            DestinationReverseReminderScenario(),
            CargoTemperatureGuardScenario(),
            TrunkReminderScenario(),
            RearSeatItemScenario(),
            ExitIntegrityScenario()
        )

        engine = TripContextEngine(
            plugins = plugins,
            persistence = persistence,
            actionDispatcher = actionDispatcher,
            aiService = aiService
        )

        pleosSignalProvider = PleosSignalProvider()
        mockSignalProvider = MockSignalProvider()

        CoroutineScope(testDispatcher).launch {
            merge(pleosSignalProvider.signals, mockSignalProvider.signals).collect { signal ->
                engine.processSignal(signal)
            }
        }

        viewModel = MainViewModel(
            pleosSignalProvider = pleosSignalProvider,
            mockSignalProvider = mockSignalProvider,
            engine = engine,
            actionDispatcher = actionDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Rapid Repeated Invocations & Alternation
    // =========================================================================

    @Test
    fun `stress01 - Rapid Repeated Preset Invocations maintain consistent state without memory exhaustion`() {
        val iterations = 50
        for (i in 1..iterations) {
            viewModel.applyCargoPresetMartGroceries()
        }

        val session = viewModel.session.value
        assertTrue("트렁크 사용 플래그 활성화", session.hasActiveTrunkUsage)
        assertNotNull("AI 적재 메모리 존재", session.aiCargoMemory)
        assertTrue("온도 민감 플래그 유지", session.aiCargoMemory?.isTemperatureSensitive == true)

        val items = viewModel.trunkCargoItems.value
        assertEquals("AI 메모리 기반 2개 아이템 표시", 2, items.size)
        assertTrue("신선식품 항목 포함", items.any { it.isPerishable })

        val freshness = viewModel.freshnessStatus.value
        assertTrue("신선식품 감시 활성", freshness.hasPerishableCargo)
        assertEquals(FreshnessRiskLevel.SAFE, freshness.riskLevel)

        // 단일 비우기 수행 시 완벽히 클린 상태로 회복
        viewModel.clearTrunkCargo()
        assertTrue("비우기 후 목록 공백", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("비우기 후 신선식품 감시 비활성", viewModel.freshnessStatus.value.hasPerishableCargo)
        assertEquals(FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
    }

    @Test
    fun `stress02 - Alternating Preset Lifecycle (Mart - Cake - Stroller - GolfBag - Clear)`() {
        // Step 1: Mart
        viewModel.applyCargoPresetMartGroceries()
        assertTrue("Mart: 신선식품 감시 활성", viewModel.freshnessStatus.value.hasPerishableCargo)
        assertTrue("Mart: 신선식품 아이템 존재", viewModel.trunkCargoItems.value.any { it.isPerishable })

        // Step 2: Cake
        viewModel.applyCargoPresetCake()
        assertTrue("Cake: 파손주의 케이크 포함", viewModel.trunkCargoItems.value.any { it.name.contains("케이크") })
        assertEquals(CargoTag.FRAGILE, viewModel.session.value.latestTrunkUsage?.cargoTag)

        // Step 3: Stroller
        viewModel.applyCargoPresetStroller()
        assertTrue("Stroller: 유모차 포함", viewModel.trunkCargoItems.value.any { it.name.contains("유모차") })

        // Step 4: GolfBag
        viewModel.applyCargoPresetGolfBag()
        assertTrue("GolfBag: 골프백 상시 보관 등록", "골프백" in viewModel.session.value.permanentTrunkNotes)
        assertTrue("GolfBag: 골프백 아이템 노출", viewModel.trunkCargoItems.value.any { it.name.contains("골프백") })

        // Step 5: Clear
        viewModel.clearTrunkCargo()
        assertTrue("Clear: 트렁크 목록 비어있음", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("Clear: 신선식품 감시 비활성", viewModel.freshnessStatus.value.hasPerishableCargo)
        assertTrue("Clear: 상시 보관 비워짐", viewModel.session.value.permanentTrunkNotes.isEmpty())
        assertNull("Clear: AI 메모리 해제", viewModel.session.value.aiCargoMemory)
    }

    @Test
    fun `stress03 - High Frequency Rapid Alternation Loop (20 cycles of 5 actions = 100 actions)`() {
        val cycles = 20
        for (i in 1..cycles) {
            viewModel.applyCargoPresetMartGroceries()
            viewModel.applyCargoPresetCake()
            viewModel.applyCargoPresetStroller()
            viewModel.applyCargoPresetGolfBag()
            viewModel.clearTrunkCargo()
        }

        // 100회 빠른 순환 후 종단 무결성 검증
        val finalSession = viewModel.session.value
        assertEquals(0, finalSession.closureHistory.count { it.zone == DoorZone.TRUNK })
        assertTrue(finalSession.permanentTrunkNotes.isEmpty())
        assertNull(finalSession.aiCargoMemory)
        assertTrue(viewModel.trunkCargoItems.value.isEmpty())
        assertFalse(viewModel.freshnessStatus.value.hasPerishableCargo)
        assertEquals(FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
    }

    // =========================================================================
    // 2. Edge Cases: Empty, Zero/Negative Elapsed Time, Massive Elapsed Time
    // =========================================================================

    @Test
    fun `edge01 - Completely Empty Session yields Strict Safe Fallback Defaults`() {
        val emptySession = TripSession()
        val cargoItems = UserScreenDataMapper.toCargoItemList(emptySession)
        val freshness = UserScreenDataMapper.toFreshnessCareStatus(emptySession)

        assertTrue("화물 목록은 완전히 비어있어야 함", cargoItems.isEmpty())
        assertFalse("부패성 화물 없음", freshness.hasPerishableCargo)
        assertEquals("신선/냉동 물품 없음", freshness.itemName)
        assertEquals(22.0f, freshness.currentTempCelsius, 0.01f)
        assertEquals(0, freshness.elapsedMinutes)
        assertEquals(60, freshness.limitMinutes)
        assertEquals(60, freshness.remainingMinutes)
        assertEquals(1.0f, freshness.progress, 0.01f)
        assertEquals(0.0f, freshness.elapsedProgress, 0.01f)
        assertEquals(FreshnessRiskLevel.SAFE, freshness.riskLevel)
    }

    @Test
    fun `edge02 - Zero and Negative Elapsed Time (Clock Skew Invariance)`() {
        val loadTimeMs = 5_000_000L
        val session = TripSession(
            cabinTempCelsius = 22.0f,
            closureHistory = listOf(
                ClosureUsageRecord(
                    zone = DoorZone.TRUNK,
                    openedAtPoi = PoiCategory.MART_SHOPPING,
                    cargoTag = CargoTag.FRESH_FOOD,
                    origin = SignalOrigin.REAL_SDK,
                    closedAtTimestampMs = loadTimeMs
                )
            )
        )

        // 1. 정확히 0ms 경과 (적재 즉시)
        val statusZero = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTimeMs)
        assertEquals(0, statusZero.elapsedMinutes)
        assertEquals(60, statusZero.remainingMinutes)
        assertEquals(1.0f, statusZero.progress, 0.01f)
        assertEquals(0.0f, statusZero.elapsedProgress, 0.01f)
        assertEquals(FreshnessRiskLevel.SAFE, statusZero.riskLevel)

        // 2. 음수 시간차 (시스템 시계 역행 또는 미래 타임스탬프)
        val statusSkew = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTimeMs - 300_000L)
        assertEquals("음수 시간차는 0분으로 보정되어야 함", 0, statusSkew.elapsedMinutes)
        assertEquals("남은 시간은 한계치 전체(60분)로 보존되어야 함", 60, statusSkew.remainingMinutes)
        assertEquals(1.0f, statusSkew.progress, 0.01f)
        assertEquals(0.0f, statusSkew.elapsedProgress, 0.01f)
        assertEquals(FreshnessRiskLevel.SAFE, statusSkew.riskLevel)
    }

    @Test
    fun `edge03 - Massive Elapsed Time (24 Hours Overflow Clamping)`() {
        val loadTimeMs = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 22.0f,
            closureHistory = listOf(
                ClosureUsageRecord(
                    zone = DoorZone.TRUNK,
                    openedAtPoi = PoiCategory.MART_SHOPPING,
                    cargoTag = CargoTag.FRESH_FOOD,
                    origin = SignalOrigin.REAL_SDK,
                    closedAtTimestampMs = loadTimeMs
                )
            )
        )

        val past24Hours = loadTimeMs + (24 * 60 * 60_000L)
        val status24h = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = past24Hours)

        assertEquals(1440, status24h.elapsedMinutes)
        assertEquals("남은 시간은 0분 미만으로 떨어지지 않고 0으로 고정", 0, status24h.remainingMinutes)
        assertEquals("진행률은 0.0 고정", 0.0f, status24h.progress, 0.01f)
        assertEquals("경과 진행률은 1.0 고정", 1.0f, status24h.elapsedProgress, 0.01f)
        assertEquals(FreshnessRiskLevel.CRITICAL, status24h.riskLevel)
    }

    // =========================================================================
    // 3. Extreme Temperature Boundaries (-20°C to 60°C)
    // =========================================================================

    @Test
    fun `temp01 - Sub-Zero Freezing Temperatures (-40°C and -20°C) map to Cool bracket`() {
        val baseTime = 10_000_000L
        fun createSession(temp: Float) = TripSession(
            cabinTempCelsius = temp,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, baseTime)
            )
        )

        val statusMinus40 = UserScreenDataMapper.toFreshnessCareStatus(createSession(-40.0f), currentTimeMs = baseTime)
        assertEquals(90, statusMinus40.limitMinutes)
        assertEquals(FreshnessRiskLevel.SAFE, statusMinus40.riskLevel)
        assertEquals(-40.0f, statusMinus40.currentTempCelsius, 0.01f)

        val statusMinus20 = UserScreenDataMapper.toFreshnessCareStatus(createSession(-20.0f), currentTimeMs = baseTime)
        assertEquals(90, statusMinus20.limitMinutes)
        assertEquals(FreshnessRiskLevel.SAFE, statusMinus20.riskLevel)
    }

    @Test
    fun `temp02 - Extreme Scorching Heat (60°C) triggers Immediate Critical even at zero elapsed`() {
        val baseTime = 10_000_000L
        val session60 = TripSession(
            cabinTempCelsius = 60.0f,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, baseTime)
            )
        )

        val status60 = UserScreenDataMapper.toFreshnessCareStatus(session60, currentTimeMs = baseTime)
        assertEquals("혹서기 한계 시간 15분", 15, status60.limitMinutes)
        assertEquals("적재 즉시(0분)라도 32도 이상이므로 즉시 CRITICAL", FreshnessRiskLevel.CRITICAL, status60.riskLevel)
        assertEquals(60.0f, status60.currentTempCelsius, 0.01f)
    }

    @Test
    fun `temp03 - Precise Temperature Threshold Boundary Tests`() {
        val baseTime = 1_000_000L
        fun sessionAt(temp: Float) = TripSession(
            cabinTempCelsius = temp,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, baseTime)
            )
        )

        // 19.9°C vs 20.0°C (90분 vs 60분)
        assertEquals(90, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(19.9f), baseTime).limitMinutes)
        assertEquals(60, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(20.0f), baseTime).limitMinutes)

        // 24.9°C vs 25.0°C (60분 vs 45분)
        assertEquals(60, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(24.9f), baseTime).limitMinutes)
        assertEquals(45, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(25.0f), baseTime).limitMinutes)

        // 25.9°C (SAFE) vs 26.0°C (WARNING 조기 경보)
        assertEquals(FreshnessRiskLevel.SAFE, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(25.9f), baseTime).riskLevel)
        assertEquals(FreshnessRiskLevel.WARNING, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(26.0f), baseTime).riskLevel)

        // 27.9°C vs 28.0°C (45분 vs 30분)
        assertEquals(45, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(27.9f), baseTime).limitMinutes)
        assertEquals(30, UserScreenDataMapper.toFreshnessCareStatus(sessionAt(28.0f), baseTime).limitMinutes)

        // 31.9°C (WARNING) vs 32.0°C (CRITICAL & 15분)
        val s319 = UserScreenDataMapper.toFreshnessCareStatus(sessionAt(31.9f), baseTime)
        assertEquals(30, s319.limitMinutes)
        assertEquals(FreshnessRiskLevel.WARNING, s319.riskLevel)

        val s320 = UserScreenDataMapper.toFreshnessCareStatus(sessionAt(32.0f), baseTime)
        assertEquals(15, s320.limitMinutes)
        assertEquals(FreshnessRiskLevel.CRITICAL, s320.riskLevel)
    }

    // =========================================================================
    // 4. State Synchronization & Reactive Flow
    // =========================================================================

    @Test
    fun `sync01 - Direct Engine Signal Injections reactively update ViewModel StateFlows`() {
        // 엔진에 직접 실내 온도 갱신 신호 주입 (35°C)
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 35.0f
            )
        )

        // 엔진에 직접 신선식품 적재 신호 주입
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                cargoTag = CargoTag.FRESH_FOOD
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                cargoTag = CargoTag.FRESH_FOOD
            )
        )

        // ViewModel의 StateFlow가 자동으로 즉시 동기화되었는지 확인
        val items = viewModel.trunkCargoItems.value
        assertEquals("직접 주입된 트렁크 적재 기록 1건 반영", 1, items.size)
        assertEquals("신선/냉동 식재료", items[0].name)
        assertTrue(items[0].isPerishable)

        val freshness = viewModel.freshnessStatus.value
        assertTrue(freshness.hasPerishableCargo)
        assertEquals(35.0f, freshness.currentTempCelsius, 0.01f)
        assertEquals(15, freshness.limitMinutes)
        assertEquals(FreshnessRiskLevel.CRITICAL, freshness.riskLevel)
    }

    @Test
    fun `sync02 - Clear Trunk Cargo dismisses active prompt and wipes cargo state`() {
        // Demo 1 실행하여 도착 및 알림 팝업 생성
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull("도착 후 알림 팝업 활성화", viewModel.activePrompt.value)
        assertEquals(TrunkReminderScenario.ID, viewModel.activePrompt.value?.scenarioId)
        assertTrue(viewModel.trunkCargoItems.value.isNotEmpty())

        // clearTrunkCargo 호출 시 알림 팝업 dismiss 및 목록 정리 검증
        viewModel.clearTrunkCargo()
        assertNull("트렁크 비우기 시 알림 팝업 dismiss 처리됨", viewModel.activePrompt.value)
        assertTrue("트렁크 목록 비워짐", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("신선식품 감시 해제", viewModel.freshnessStatus.value.hasPerishableCargo)
    }

    // =========================================================================
    // 5. Robustness & Parsing Resiliency
    // =========================================================================

    @Test
    fun `robust01 - Resilient Handling of Empty and Malformed AI Inferred Items`() {
        val malformedSession = TripSession(
            aiCargoMemory = AiCargoMemory(
                rawUserUtterance = "테스트 발화",
                inferredItems = listOf("", "   ", "특이물품 (세부사항 없음)", "???"),
                inferredPrimaryTag = CargoTag.GENERAL,
                isTemperatureSensitive = false,
                requiresSoftAutonomousProfile = false,
                aiBriefingSummary = "테스트 요약"
            )
        )

        val items = UserScreenDataMapper.toCargoItemList(malformedSession)
        assertEquals(4, items.size)
        // 공백 및 미지정 품목도 크래시 없이 기본 "일반화물" / "📦"로 폴백 처리됨
        assertEquals("일반화물", items[0].categoryBadge)
        assertEquals("📦", items[0].icon)
        assertEquals("특이물품", items[2].name)
    }

    @Test
    fun `robust02 - Concurrent Signal and Preset Execution Thread Safety`() = runTest {
        // 비동기 작업 30개를 동시에 발송하여 동시성 레이스 컨디션 및 크래시 여부 검증
        val jobs = (1..30).map { index ->
            async(Dispatchers.Default) {
                if (index % 4 == 0) {
                    viewModel.applyCargoPresetMartGroceries()
                } else if (index % 4 == 1) {
                    viewModel.applyCargoPresetCake()
                } else if (index % 4 == 2) {
                    viewModel.applyCargoPresetGolfBag()
                } else {
                    viewModel.clearTrunkCargo()
                }
            }
        }
        jobs.awaitAll()

        // 종료 후 일관된 상태 유지 검증
        val session = viewModel.session.value
        assertNotNull(session)
        val items = viewModel.trunkCargoItems.value
        assertNotNull(items)
        val freshness = viewModel.freshnessStatus.value
        assertNotNull(freshness)
    }
}
