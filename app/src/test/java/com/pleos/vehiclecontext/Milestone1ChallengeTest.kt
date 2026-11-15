package com.pleos.vehiclecontext

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.engine.InMemorySessionPersistence
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.AiCargoMemory
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ClosureUsageRecord
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone1ChallengeTest {

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
    // SECTION A: Core Demo 1 ~ 6 Regression Robustness Verification
    // =========================================================================

    @Test
    fun `challenge01 - Demo 1 Regression Trunk Used Home Arrival triggers prompt and updates M1 flows`() {
        viewModel.runDemo1TrunkUsedHomeArrival()

        val session = viewModel.session.value
        assertTrue("목적지 도착 상태여야 함", session.isArrivedAtDestination)
        assertEquals("기어 상태는 PARK여야 함", GearState.PARK, session.currentGear)
        assertTrue("트렁크 사용 이력이 활성화되어 있어야 함", session.hasActiveTrunkUsage)

        val activePrompt = viewModel.activePrompt.value
        assertNotNull("하차 전 트렁크 알림 프롬프트가 발동해야 함", activePrompt)
        assertEquals("프롬프트 ID는 trunk_reminder_mvp여야 함", TrunkReminderScenario.ID, activePrompt?.scenarioId)

        // M1 Presentation Flows
        val cargoItems = viewModel.trunkCargoItems.value
        assertTrue("적재 물품 목록이 비어있지 않아야 함", cargoItems.isNotEmpty())
        assertTrue("신선식품 항목이 포함되어 있어야 함", cargoItems.any { it.isPerishable })

        val freshness = viewModel.freshnessStatus.value
        assertTrue("신선/변질 감시가 활성화되어야 함", freshness.hasPerishableCargo)
        assertEquals("초기 상온(22°C)에서는 SAFE 상태여야 함", FreshnessRiskLevel.SAFE, freshness.riskLevel)

        // Session Persistence Verification
        val saved = persistence.restore()
        assertNotNull("세션이 영속화 저장소에 기록되어 있어야 함", saved)
        assertEquals("영속화된 목적지가 일치해야 함", "집 (성수동 자택)", saved?.destinationName)
        assertTrue("영속화 세션도 트렁크 사용 이력을 포함해야 함", saved?.hasActiveTrunkUsage == true)
    }

    @Test
    fun `challenge02 - Demo 2 Regression No Trunk Usage Home Arrival suppresses prompt and keeps cargo empty`() {
        viewModel.runDemo2NoTrunkUsageHomeArrival()

        val session = viewModel.session.value
        assertTrue("목적지 도착 상태여야 함", session.isArrivedAtDestination)
        assertEquals("기어 상태는 PARK여야 함", GearState.PARK, session.currentGear)
        assertFalse("트렁크 사용 이력이 없어야 함", session.hasActiveTrunkUsage)

        assertNull("트렁크 미사용 시 하차 알림 프롬프트가 발동하지 않아야 함", viewModel.activePrompt.value)
        assertTrue("트렁크 적재 물품 목록은 비어 있어야 함", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("신선식품 감시가 비활성화되어 있어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
    }

    @Test
    fun `challenge03 - Demo 3 Regression Intermediate Stop suppresses arrival prompt while retaining cargo`() {
        viewModel.runDemo3IntermediateStop()

        val session = viewModel.session.value
        assertEquals("주행 단계는 INTERMEDIATE_STOP이어야 함", TripPhase.INTERMEDIATE_STOP, session.phase)
        assertFalse("최종 목적지 도착 상태가 아니어야 함", session.isArrivedAtDestination)
        assertEquals("기어 상태는 PARK여야 함", GearState.PARK, session.currentGear)
        assertTrue("트렁크 적재 이력이 보존되어야 함", session.hasActiveTrunkUsage)

        assertNull("중간 경유지 P단에서는 하차 알림이 발동하지 않아야 함", viewModel.activePrompt.value)

        // M1 Flows must maintain cargo during intermediate stop
        assertTrue("경유 중에도 적재 목록이 유지되어야 함", viewModel.trunkCargoItems.value.isNotEmpty())
        assertTrue("경유 중에도 신선식품 감시가 유지되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
    }

    @Test
    fun `challenge04 - Demo 4 Regression Repeated Park Transition suppresses duplicate prompt`() {
        viewModel.runDemo4RepeatedParkTransition()

        val session = viewModel.session.value
        assertTrue("현재 도착 세션에 이미 안내된 시나리오 ID로 등록되어야 함",
            TrunkReminderScenario.ID in session.promptedScenarioIdsForCurrentArrival)
        assertNull("중복 P단 진입 시 프롬프트가 재발동하지 않아야 함", viewModel.activePrompt.value)

        // M1 flows still show cargo correctly
        assertTrue("중복 P단 이후에도 적재 목록이 유지되어야 함", viewModel.trunkCargoItems.value.isNotEmpty())
        assertTrue("중복 P단 이후에도 신선 감시 상태가 유지되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
    }

    @Test
    fun `challenge05 - Demo 5 Regression Route Changed updates destination while preserving trunk inventory`() {
        viewModel.runDemo5RouteChanged()

        val session = viewModel.session.value
        assertEquals("목적지 이름이 새 목적지로 갱신되어야 함", "본가 (분당 부모님댁)", session.destinationName)
        assertEquals("목적지 POI는 HOME이어야 함", PoiCategory.HOME, session.destinationPoi)
        assertFalse("경로 변경 직후에는 미도착 상태여야 함", session.isArrivedAtDestination)
        assertTrue("트렁크 적재 화물이 보존되어야 함", session.hasActiveTrunkUsage)

        assertTrue("경로 변경 후에도 트렁크 적재 목록이 유지되어야 함", viewModel.trunkCargoItems.value.isNotEmpty())
        assertTrue("경로 변경 후에도 신선식품 감시가 유지되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
    }

    @Test
    fun `challenge06 - Demo 6 Regression App Restart Recovery restores session and derives presentation state`() {
        // Step 1: Pre-populate persistence with an active shopping trip session
        val originalSession = TripSession(
            sessionId = "TRIP-RECOVERY-TEST",
            phase = TripPhase.DRIVING,
            currentGear = GearState.DRIVE,
            destinationName = "공항 (인천공항 제2터미널)",
            destinationPoi = PoiCategory.AIRPORT_STATION,
            isArrivedAtDestination = false,
            closureHistory = listOf(
                ClosureUsageRecord(
                    zone = DoorZone.TRUNK,
                    openedAtPoi = PoiCategory.MART_SHOPPING,
                    cargoTag = CargoTag.FRESH_FOOD,
                    origin = SignalOrigin.REAL_SDK,
                    closedAtTimestampMs = System.currentTimeMillis() - 15 * 60_000L
                )
            ),
            aiCargoMemory = AiCargoMemory(
                rawUserUtterance = "마트에서 냉동식품이랑 신선육 샀어",
                inferredItems = listOf("냉동식품 (1개)", "신선육 (1개)"),
                inferredPrimaryTag = CargoTag.FRESH_FOOD,
                isTemperatureSensitive = true,
                requiresSoftAutonomousProfile = false,
                aiBriefingSummary = "신선/냉동 식재료 보관 중"
            ),
            permanentTrunkNotes = setOf("골프백")
        )
        persistence.save(originalSession)

        // Step 2: Trigger Demo 6 recovery
        viewModel.runDemo6AppRestartRecovery()

        // Step 3: Verify restored domain state
        val restored = viewModel.session.value
        assertEquals("세션 ID가 복원되어야 함", "TRIP-RECOVERY-TEST", restored.sessionId)
        assertEquals("목적지가 복원되어야 함", "공항 (인천공항 제2터미널)", restored.destinationName)
        assertTrue("트렁크 사용 이력이 복원되어야 함", restored.hasActiveTrunkUsage)
        assertNotNull("AI 적재 메모리가 복원되어야 함", restored.aiCargoMemory)
        assertTrue("상시 보관 메모가 복원되어야 함", "골프백" in restored.permanentTrunkNotes)

        // Step 4: Verify M1 derived presentation flows after restart
        val cargoItems = viewModel.trunkCargoItems.value
        assertTrue("복원된 적재 물품이 목록에 노출되어야 함", cargoItems.isNotEmpty())
        assertTrue("AI 적재 품목이 포함되어야 함", cargoItems.any { it.name.contains("냉동") || it.name.contains("신선") })
        assertTrue("상시 보관 골프백이 포함되어야 함", cargoItems.any { it.name.contains("골프백") })

        val freshness = viewModel.freshnessStatus.value
        assertTrue("신선/냉동 감시 상태가 복원되어야 함", freshness.hasPerishableCargo)
        assertTrue("경과 시간이 약 15분으로 계산되어야 함", freshness.elapsedMinutes in 14..16)
    }

    // =========================================================================
    // SECTION B: Coroutine Lifecycle, Dispatchers & Concurrency Robustness
    // =========================================================================

    @Test
    fun `challenge07 - Coroutine Collection under StandardTestDispatcher executes reliably upon advanceUntilIdle`() = runTest {
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standardDispatcher)

        val customEngine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            persistence = InMemorySessionPersistence(),
            actionDispatcher = ActionDispatcher(),
            aiService = PleosAiContextAgent()
        )

        val customVm = MainViewModel(
            pleosSignalProvider = PleosSignalProvider(),
            mockSignalProvider = MockSignalProvider(),
            engine = customEngine,
            actionDispatcher = ActionDispatcher()
        )

        // Initial check before advancing
        assertTrue("초기에는 빈 적재 목록이어야 함", customVm.trunkCargoItems.value.isEmpty())

        // Apply preset
        customVm.applyCargoPresetMartGroceries()
        // Due to dual-synchronous update, immediate value is already available!
        assertTrue("동기 업데이트로 즉시 반영되어야 함", customVm.trunkCargoItems.value.isNotEmpty())

        advanceUntilIdle()

        // Re-confirm after coroutine collection completes
        assertTrue("코루틴 수집 완료 후에도 상태가 안정적이어야 함", customVm.trunkCargoItems.value.isNotEmpty())
        assertTrue("신선식품 감시가 활성화되어야 함", customVm.freshnessStatus.value.hasPerishableCargo)
    }

    @Test
    fun `challenge08 - ViewModel Coroutine Scope Lifecycle and Reset Robustness`() {
        // Verify ViewModel operates reliably with test dispatcher
        val localVm = MainViewModel(
            pleosSignalProvider = pleosSignalProvider,
            mockSignalProvider = mockSignalProvider,
            engine = engine,
            actionDispatcher = actionDispatcher
        )

        localVm.applyCargoPresetCake()
        assertTrue("케이크 프리셋이 즉시 반영되어야 함", localVm.trunkCargoItems.value.any { it.name.contains("케이크") })

        localVm.clearTrunkCargo()
        assertTrue("비우기 동작이 즉시 반영되어야 함", localVm.trunkCargoItems.value.isEmpty())
    }

    @Test
    fun `challenge09 - MultiThreaded Parallel Signal Ingestion does not throw ConcurrentModificationException`() = kotlinx.coroutines.runBlocking {
        val pool = Executors.newFixedThreadPool(8)
        val multiDispatcher = pool.asCoroutineDispatcher()

        try {
            val jobs = (1..50).map { i ->
                CoroutineScope(multiDispatcher).launch {
                    val origin = if (i % 2 == 0) SignalOrigin.REAL_SDK else SignalOrigin.MOCK_SIMULATOR
                    engine.processSignal(
                        VehicleSignal.DoorStateChanged(
                            zone = DoorZone.TRUNK,
                            isOpen = i % 2 == 1,
                            currentLocationPoi = PoiCategory.MART_SHOPPING,
                            cargoTag = CargoTag.FRESH_FOOD,
                            origin = origin
                        )
                    )
                    engine.processSignal(
                        VehicleSignal.CabinStatusUpdated(
                            rearSeatOccupied = (i % 3 == 0),
                            openWindowsCount = i % 4,
                            cabinTempCelsius = 20.0f + (i % 15).toFloat(),
                            origin = origin
                        )
                    )
                }
            }

            jobs.joinAll()

            // Verify final state consistency
            val finalSession = viewModel.session.value
            assertNotNull("세션이 정상적으로 유지되어야 함", finalSession)
            assertNotNull("StateFlow trunkCargoItems가 접근 가능해야 함", viewModel.trunkCargoItems.value)
            assertNotNull("StateFlow freshnessStatus가 접근 가능해야 함", viewModel.freshnessStatus.value)
        } finally {
            multiDispatcher.close()
            pool.shutdown()
        }
    }

    // =========================================================================
    // SECTION C: Rapid Clear and Preset Re-population Stress Testing
    // =========================================================================

    @Test
    fun `challenge10 - Rapid Alternating Presets and Clear cycles leave zero residual state leaks`() {
        for (cycle in 1..25) {
            // 1. Mart Groceries
            viewModel.applyCargoPresetMartGroceries()
            assertTrue("Cycle $cycle: 마트 장보기 적용 후 적재 목록 존재", viewModel.trunkCargoItems.value.isNotEmpty())
            assertTrue("Cycle $cycle: 신선식품 감시 활성화", viewModel.freshnessStatus.value.hasPerishableCargo)

            // Clear
            viewModel.clearTrunkCargo()
            assertTrue("Cycle $cycle: 비우기 후 목록 비어있음", viewModel.trunkCargoItems.value.isEmpty())
            assertFalse("Cycle $cycle: 비우기 후 신선식품 비활성화", viewModel.freshnessStatus.value.hasPerishableCargo)
            assertEquals("Cycle $cycle: 비우기 후 SAFE", FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
            assertNull("Cycle $cycle: 비우기 후 AI 메모리 null", viewModel.session.value.aiCargoMemory)
            assertTrue("Cycle $cycle: 비우기 후 상시보관 empty", viewModel.session.value.permanentTrunkNotes.isEmpty())

            // 2. Cake
            viewModel.applyCargoPresetCake()
            val cakeItems = viewModel.trunkCargoItems.value
            assertTrue("Cycle $cycle: 케이크 적용 후 적재 목록 존재", cakeItems.isNotEmpty())
            assertTrue("Cycle $cycle: 케이크 품목 존재", cakeItems.any { it.name.contains("케이크") || it.icon == "🎂" })
            assertFalse("Cycle $cycle: 케이크는 신선식품 부패 감시 미대상이어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)

            // Clear
            viewModel.clearTrunkCargo()
            assertTrue("Cycle $cycle: 비우기 후 목록 비어있음", viewModel.trunkCargoItems.value.isEmpty())

            // 3. Golf Bag
            viewModel.applyCargoPresetGolfBag()
            val golfItems = viewModel.trunkCargoItems.value
            assertTrue("Cycle $cycle: 골프백 적용 후 적재 목록 존재", golfItems.isNotEmpty())
            assertTrue("Cycle $cycle: 골프백 품목 존재", golfItems.any { it.name.contains("골프백") || it.icon == "⛳" })
            assertTrue("Cycle $cycle: 상시보관에 골프백 등록", "골프백" in viewModel.session.value.permanentTrunkNotes)

            // Clear
            viewModel.clearTrunkCargo()
            assertTrue("Cycle $cycle: 비우기 후 상시보관 골프백도 제거되어야 함", viewModel.session.value.permanentTrunkNotes.isEmpty())
            assertTrue("Cycle $cycle: 비우기 후 목록 비어있음", viewModel.trunkCargoItems.value.isEmpty())

            // 4. Stroller
            viewModel.applyCargoPresetStroller()
            val strollerItems = viewModel.trunkCargoItems.value
            assertTrue("Cycle $cycle: 유모차 적용 후 적재 목록 존재", strollerItems.isNotEmpty())
            assertTrue("Cycle $cycle: 유모차 품목 존재", strollerItems.any { it.name.contains("유모차") || it.icon == "👶" })
            assertFalse("Cycle $cycle: 이전 골프백이 잔존하지 않아야 함", strollerItems.any { it.name.contains("골프백") })

            // Clear
            viewModel.clearTrunkCargo()
            assertTrue("Cycle $cycle: 비우기 후 목록 비어있음", viewModel.trunkCargoItems.value.isEmpty())
        }
    }

    @Test
    fun `challenge11 - Concurrent Preset Application and ClearTrunkCargo stress converges to clean state`() = kotlinx.coroutines.runBlocking {
        val pool = Executors.newFixedThreadPool(6)
        val dispatcher = pool.asCoroutineDispatcher()

        try {
            val jobs = (1..60).map { i ->
                CoroutineScope(dispatcher).launch {
                    when (i % 5) {
                        0 -> viewModel.applyCargoPresetMartGroceries()
                        1 -> viewModel.applyCargoPresetCake()
                        2 -> viewModel.applyCargoPresetStroller()
                        3 -> viewModel.applyCargoPresetGolfBag()
                        4 -> viewModel.clearTrunkCargo()
                    }
                }
            }

            jobs.joinAll()

            // Final deterministic clear
            viewModel.clearTrunkCargo()

            val finalSession = viewModel.session.value
            assertTrue("최종 트렁크 개폐 이력이 비워져야 함", finalSession.closureHistory.none { it.zone == DoorZone.TRUNK })
            assertNull("최종 AI 메모리가 null이어야 함", finalSession.aiCargoMemory)
            assertTrue("최종 상시 보관 메모가 비워져야 함", finalSession.permanentTrunkNotes.isEmpty())
            assertTrue("최종 트렁크 표시 아이템이 비워져야 함", viewModel.trunkCargoItems.value.isEmpty())
            assertFalse("최종 신선식품 감시가 비활성화되어야 함", viewModel.freshnessStatus.value.hasPerishableCargo)
            assertEquals("최종 위험도는 SAFE여야 함", FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
        } finally {
            dispatcher.close()
            pool.shutdown()
        }
    }

    @Test
    fun `challenge12 - ClearTrunkCargo dismisses Trunk Reminder prompt but leaves unrelated prompts intact`() {
        // Step 1: Trigger trunk reminder prompt
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull("트렁크 알림 프롬프트가 떠 있어야 함", viewModel.activePrompt.value)
        assertEquals(TrunkReminderScenario.ID, viewModel.activePrompt.value?.scenarioId)

        // Step 2: Clear trunk cargo
        viewModel.clearTrunkCargo()
        assertNull("트렁크를 비우면 트렁크 하차 알림 프롬프트는 즉시 닫혀야 함", viewModel.activePrompt.value)

        // Step 3: Trigger an unrelated prompt (Exit Integrity / Window Alert)
        // Simulate windows open and arrived parked
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 2,
                cabinTempCelsius = 22.0f
            )
        )
        // If ExitIntegrityScenario triggered
        val exitPrompt = viewModel.activePrompt.value
        if (exitPrompt != null && exitPrompt.scenarioId == "exit_window_integrity_guard") {
            // Call clearTrunkCargo again
            viewModel.clearTrunkCargo()
            // Unrelated window alert prompt must NOT be dismissed!
            assertNotNull("트렁크 비우기가 창문 닫힘 알림 프롬프트까지 오작동으로 닫아서는 안 됨", viewModel.activePrompt.value)
            assertEquals("exit_window_integrity_guard", viewModel.activePrompt.value?.scenarioId)
        }
    }

    @Test
    fun `challenge13 - Dynamic Temperature Escalation triggers live Freshness Warning and Critical transitions`() {
        // Load groceries at standard 22°C
        viewModel.applyCargoPresetMartGroceries()
        assertEquals(FreshnessRiskLevel.SAFE, viewModel.freshnessStatus.value.riskLevel)
        assertEquals(60, viewModel.freshnessStatus.value.limitMinutes)

        // Temperature rises to 26.5°C (Warm vehicle cabin)
        mockSignalProvider.emit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 26.5f
            )
        )
        val status26 = viewModel.freshnessStatus.value
        assertEquals("26.5°C에서는 즉시 WARNING 위험도로 격상되어야 함", FreshnessRiskLevel.WARNING, status26.riskLevel)
        assertEquals("26.5°C에서는 골든타임 한계가 45분으로 단축되어야 함", 45, status26.limitMinutes)

        // Temperature rises to 33.0°C (Extreme summer heat)
        mockSignalProvider.emit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 33.0f
            )
        )
        val status33 = viewModel.freshnessStatus.value
        assertEquals("33.0°C에서는 즉시 CRITICAL 위험도로 격상되어야 함", FreshnessRiskLevel.CRITICAL, status33.riskLevel)
        assertEquals("33.0°C에서는 골든타임 한계가 15분으로 단축되어야 함", 15, status33.limitMinutes)

        // Temperature drops back to 19.0°C (AC activated)
        mockSignalProvider.emit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 19.0f
            )
        )
        val status19 = viewModel.freshnessStatus.value
        assertEquals("에어컨 작동으로 19°C로 하강 시 SAFE로 복귀해야 함", FreshnessRiskLevel.SAFE, status19.riskLevel)
        assertEquals("19°C에서는 골든타임 한계가 90분으로 연장되어야 함", 90, status19.limitMinutes)
    }

    @Test
    fun `challenge14 - Arrival Alert Toggle operates independently from Cargo inventory changes`() {
        // Default is true
        assertTrue(viewModel.isArrivalAlertEnabled.value)

        // Toggle to false
        viewModel.setArrivalAlertEnabled(false)
        assertFalse(viewModel.isArrivalAlertEnabled.value)

        // Presets and clear should not alter user toggle preference
        viewModel.applyCargoPresetMartGroceries()
        assertFalse("장보기 프리셋 적용 후에도 토글 상태(false)가 유지되어야 함", viewModel.isArrivalAlertEnabled.value)

        viewModel.clearTrunkCargo()
        assertFalse("트렁크 비우기 후에도 토글 상태(false)가 유지되어야 함", viewModel.isArrivalAlertEnabled.value)

        viewModel.applyCargoPresetCake()
        assertFalse("케이크 프리셋 적용 후에도 토글 상태(false)가 유지되어야 함", viewModel.isArrivalAlertEnabled.value)

        viewModel.setArrivalAlertEnabled(true)
        assertTrue("사용자가 다시 활성화하면 true로 변경되어야 함", viewModel.isArrivalAlertEnabled.value)
    }
}
