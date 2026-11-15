package com.pleos.vehiclecontext

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.engine.InMemorySessionPersistence
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ClosureUsageRecord
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralUserViewModelTest {

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

    @Test
    fun `test01 - Initial State has Default Values`() {
        assertTrue("기본값으로 도착 하차 알림이 활성화(true)되어 있어야 함", viewModel.isArrivalAlertEnabled.value)
        assertTrue("초기에는 트렁크 적재 물품 목록이 비어 있어야 함", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("초기에는 신선/변질 위험 물품이 없어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
        assertEquals("초기 위험도는 SAFE여야 함", FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
        assertEquals("초기 진행률은 1.0(100%)이어야 함", 1.0f, viewModel.freshnessStatus.value.progress, 0.01f)
        assertEquals("기본 캐빈 온도는 22°C여야 함", 22.0f, viewModel.freshnessStatus.value.currentTempCelsius, 0.1f)
    }

    @Test
    fun `test02 - Arrival Alert Toggle updates State Correctly`() {
        viewModel.setArrivalAlertEnabled(false)
        assertFalse("토글 해제 시 false로 변경되어야 함", viewModel.isArrivalAlertEnabled.value)

        viewModel.setArrivalAlertEnabled(true)
        assertTrue("토글 재설정 시 true로 복구되어야 함", viewModel.isArrivalAlertEnabled.value)
    }

    @Test
    fun `test03 - Preset Mart Groceries updates Session and Freshness Status`() {
        viewModel.applyCargoPresetMartGroceries()

        val session = viewModel.session.value
        assertTrue("트렁크 사용 이력이 활성화되어야 함", session.hasActiveTrunkUsage)
        assertNotNull("최근 트렁크 사용 기록이 존재해야 함", session.latestTrunkUsage)
        assertEquals("적재 태그는 FRESH_FOOD여야 함", CargoTag.FRESH_FOOD, session.latestTrunkUsage?.cargoTag)
        assertEquals("적재 POI는 MART_SHOPPING이어야 함", PoiCategory.MART_SHOPPING, session.latestTrunkUsage?.openedAtPoi)

        val aiMemory = session.aiCargoMemory
        assertNotNull("AI 적재 메모리가 생성되어야 함", aiMemory)
        assertTrue("온도 민감 물품으로 추론되어야 함", aiMemory?.isTemperatureSensitive == true)

        val items = viewModel.trunkCargoItems.value
        assertTrue("트렁크 적재 목록에 아이템이 노출되어야 함", items.isNotEmpty())
        assertTrue("신선/변질 주의 물품(isPerishable=true)이 포함되어야 함", items.any { it.isPerishable })

        val freshness = viewModel.freshnessStatus.value
        assertTrue("신선식품 감시 상태가 활성화(hasPerishableCargo=true)되어야 함", freshness.hasPerishableCargo)
        assertEquals("초기 상온(22°C)에서는 SAFE 위험도여야 함", FreshnessRiskLevel.SAFE, freshness.riskLevel)
    }

    @Test
    fun `test04 - Preset Cake updates Session with Fragile Cargo`() {
        viewModel.applyCargoPresetCake()

        val session = viewModel.session.value
        assertEquals("적재 태그는 FRAGILE이어야 함", CargoTag.FRAGILE, session.latestTrunkUsage?.cargoTag)
        assertTrue("자율주행 부드러운 프로파일 요구가 활성화되어야 함", session.aiCargoMemory?.requiresSoftAutonomousProfile == true)

        val items = viewModel.trunkCargoItems.value
        assertTrue("케이크 아이템이 목록에 포함되어야 함", items.any { it.name.contains("케이크") || it.icon == "🎂" })
        assertTrue("파손주의 뱃지가 지정되어야 함", items.any { it.categoryBadge.contains("파손주의") })
    }

    @Test
    fun `test05 - Preset Stroller updates Session with Stroller Cargo`() {
        viewModel.applyCargoPresetStroller()

        val session = viewModel.session.value
        assertEquals("도어 영역은 TRUNK여야 함", DoorZone.TRUNK, session.latestTrunkUsage?.zone)
        assertTrue("AI 메모리에 유모차 발화가 기록되어야 함", session.aiCargoMemory?.rawUserUtterance?.contains("유모차") == true)

        val items = viewModel.trunkCargoItems.value
        assertTrue("유모차 아이템이 목록에 포함되어야 함", items.any { it.name.contains("유모차") || it.icon == "👶" })
    }

    @Test
    fun `test06 - Preset Golf Bag updates Permanent Trunk Notes`() {
        viewModel.applyCargoPresetGolfBag()

        val session = viewModel.session.value
        assertTrue("상시 보관 목록에 '골프백'이 등록되어야 함", "골프백" in session.permanentTrunkNotes)

        val items = viewModel.trunkCargoItems.value
        val golfItem = items.firstOrNull { it.name.contains("골프백") || it.icon == "⛳" }
        assertNotNull("골프백 아이템이 목록에 노출되어야 함", golfItem)
        assertFalse("골프백은 부패 물품이 아니어야 함", golfItem?.isPerishable == true)
    }

    @Test
    fun `test07 - Clear Trunk Cargo empties Cargo cleanly while retaining Trip Session`() {
        viewModel.applyCargoPresetMartGroceries()
        viewModel.applyCargoPresetGolfBag()
        assertTrue("비우기 전에는 아이템이 존재해야 함", viewModel.trunkCargoItems.value.isNotEmpty())
        assertTrue("비우기 전에는 신선식품이 활성화되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)

        viewModel.clearTrunkCargo()

        val session = viewModel.session.value
        assertTrue("트렁크 개폐 이력이 정리되어야 함", session.closureHistory.none { it.zone == DoorZone.TRUNK })
        assertTrue("상시 보관 메모가 비워져야 함", session.permanentTrunkNotes.isEmpty())
        assertEquals("AI 적재 메모리가 초기화되어야 함", null, session.aiCargoMemory)

        assertTrue("트렁크 표시 목록이 비워져야 함", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("신선식품 감시가 해제되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
        assertEquals("위험도가 SAFE로 복귀해야 함", FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
    }

    @Test
    fun `test08 - Freshness Computation at 20C Standard Ambient across Elapsed Time`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 20.0f,
            closureHistory = listOf(
                ClosureUsageRecord(
                    zone = DoorZone.TRUNK,
                    openedAtPoi = PoiCategory.MART_SHOPPING,
                    cargoTag = CargoTag.FRESH_FOOD,
                    origin = SignalOrigin.REAL_SDK,
                    closedAtTimestampMs = loadTime
                )
            )
        )

        // 1. 경과 10분 -> 50분 남음 -> SAFE
        val status10m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 10 * 60_000L)
        assertTrue(status10m.hasPerishableCargo)
        assertEquals(20.0f, status10m.currentTempCelsius, 0.1f)
        assertEquals(60, status10m.limitMinutes)
        assertEquals(10, status10m.elapsedMinutes)
        assertEquals(50, status10m.remainingMinutes)
        assertEquals(FreshnessRiskLevel.SAFE, status10m.riskLevel)

        // 2. 경과 35분 -> 25분 남음 (limit/2 이하) -> WARNING
        val status35m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 35 * 60_000L)
        assertEquals(25, status35m.remainingMinutes)
        assertEquals(FreshnessRiskLevel.WARNING, status35m.riskLevel)

        // 3. 경과 58분 -> 2분 남음 (5분 이하) -> CRITICAL
        val status58m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 58 * 60_000L)
        assertEquals(2, status58m.remainingMinutes)
        assertEquals(FreshnessRiskLevel.CRITICAL, status58m.riskLevel)

        // 4. 경과 65분 -> 만료(0분 남음) -> CRITICAL 및 progress 0
        val status65m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 65 * 60_000L)
        assertEquals(0, status65m.remainingMinutes)
        assertEquals(0.0f, status65m.progress, 0.01f)
        assertEquals(FreshnessRiskLevel.CRITICAL, status65m.riskLevel)
    }

    @Test
    fun `test09 - Freshness Computation at 28C High Heat triggers Warning and Shortens Limit`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 28.0f,
            closureHistory = listOf(
                ClosureUsageRecord(
                    zone = DoorZone.TRUNK,
                    openedAtPoi = PoiCategory.MART_SHOPPING,
                    cargoTag = CargoTag.FRESH_FOOD,
                    origin = SignalOrigin.REAL_SDK,
                    closedAtTimestampMs = loadTime
                )
            )
        )

        // 28°C 고온 환경에서는 골든타임 한계가 30분으로 단축됨
        val status5m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 5 * 60_000L)
        assertEquals(30, status5m.limitMinutes)
        assertEquals(25, status5m.remainingMinutes)
        // 26°C 이상이므로 경과시간과 무관하게 즉시 WARNING 발동
        assertEquals(FreshnessRiskLevel.WARNING, status5m.riskLevel)

        // 26분 경과 시 4분 남음 -> CRITICAL
        val status26m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 26 * 60_000L)
        assertEquals(4, status26m.remainingMinutes)
        assertEquals(FreshnessRiskLevel.CRITICAL, status26m.riskLevel)
    }

    @Test
    fun `test10 - Freshness Computation at 35C Extreme Summer Heat triggers Immediate Critical`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 35.0f,
            closureHistory = listOf(
                ClosureUsageRecord(
                    zone = DoorZone.TRUNK,
                    openedAtPoi = PoiCategory.MART_SHOPPING,
                    cargoTag = CargoTag.FRESH_FOOD,
                    origin = SignalOrigin.REAL_SDK,
                    closedAtTimestampMs = loadTime
                )
            )
        )

        // 35°C 혹서기에서는 한계 15분 및 즉시 CRITICAL 발동
        val status2m = UserScreenDataMapper.toFreshnessCareStatus(session, currentTimeMs = loadTime + 2 * 60_000L)
        assertEquals(15, status2m.limitMinutes)
        assertEquals(FreshnessRiskLevel.CRITICAL, status2m.riskLevel)
        assertEquals(35.0f, status2m.currentTempCelsius, 0.1f)
    }

    @Test
    fun `test11 - Freshness Computation with No Perishable Cargo remains Safe even at High Temperature`() {
        val session = TripSession(
            cabinTempCelsius = 35.0f,
            permanentTrunkNotes = setOf("골프백", "유모차")
        )

        val status = UserScreenDataMapper.toFreshnessCareStatus(session)
        assertFalse("부패성 물품이 없어야 함", status.hasPerishableCargo)
        assertEquals("위험도는 SAFE여야 함", FreshnessRiskLevel.SAFE, status.riskLevel)
        assertEquals(1.0f, status.progress, 0.01f)
    }

    @Test
    fun `test12 - DataMapper Temperature Bracket Coverage and Progress Clamping`() {
        val baseTime = 2_000_000L

        fun statusForTemp(temp: Float): Int {
            val s = TripSession(
                cabinTempCelsius = temp,
                closureHistory = listOf(
                    ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, baseTime)
                )
            )
            return UserScreenDataMapper.toFreshnessCareStatus(s, currentTimeMs = baseTime).limitMinutes
        }

        assertEquals(90, statusForTemp(19.5f)) // Cool (<20°C)
        assertEquals(60, statusForTemp(22.0f)) // Standard (20–25°C)
        assertEquals(45, statusForTemp(26.0f)) // Warm (25–28°C)
        assertEquals(30, statusForTemp(29.0f)) // Hot (28–32°C)
        assertEquals(15, statusForTemp(36.0f)) // Critical heat (>=32°C)
    }

    @Test
    fun `test13 - Non-Breaking Regression - Demo 1 Trunk Used Home Arrival updates ViewModel Flows`() {
        viewModel.runDemo1TrunkUsedHomeArrival()

        val session = viewModel.session.value
        assertTrue("목적지 도착 상태여야 함", session.isArrivedAtDestination)
        assertEquals("기어는 PARK여야 함", GearState.PARK, session.currentGear)
        assertNotNull("하차 알림 프롬프트가 발동해야 함", viewModel.activePrompt.value)
        assertEquals(TrunkReminderScenario.ID, viewModel.activePrompt.value?.scenarioId)

        // M1 신규 StateFlow 연동 검증
        assertTrue("Demo 1 실행 후 적재 물품 목록이 갱신되어야 함", viewModel.trunkCargoItems.value.isNotEmpty())
        assertTrue("Demo 1의 신선식품 적재가 freshnessStatus에 반영되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
    }
}
