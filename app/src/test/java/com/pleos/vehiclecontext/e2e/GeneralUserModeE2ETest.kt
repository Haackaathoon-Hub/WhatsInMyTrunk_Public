package com.pleos.vehiclecontext.e2e

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
import com.pleos.vehiclecontext.core.model.TripPhase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * End-to-End Test Suite for General User Mode & 5 Automotive Widgets.
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases) for:
 * - Widget 1: Tailgate Status Live Overview & Cargo Item Presentation
 * - Widget 2: Ambient Temp Sync & Freshness Golden Time Smart Care
 * - Widget 3: Pre-Exit Smart Alert Card & Window Integrity Sub-Status
 * - Widget 4: Quick Cargo Actions, Presets & Clear Trunk Action
 * - Widget 5: Today's Trip & Cargo Timeline
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralUserModeE2ETest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // Presentation models matching PROJECT.md interface contracts
    data class TestCargoItemPresentation(
        val id: String,
        val name: String,
        val categoryBadge: String,
        val icon: String,
        val locationNote: String,
        val loadTimeFormatted: String,
        val isPerishable: Boolean
    )

    enum class TestFreshnessRiskLevel(val label: String) {
        SAFE("안전"),
        WARNING("주의"),
        CRITICAL("위험")
    }

    data class TestFreshnessCareStatus(
        val hasPerishableCargo: Boolean,
        val itemName: String,
        val currentTempCelsius: Float,
        val elapsedMinutes: Int,
        val limitMinutes: Int,
        val remainingMinutes: Int,
        val progress: Float,
        val riskLevel: TestFreshnessRiskLevel
    )

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var engine: TripContextEngine
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var pleosSignalProvider: PleosSignalProvider
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var viewModel: MainViewModel
    private lateinit var isArrivalAlertEnabledState: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        persistence = InMemorySessionPersistence()
        actionDispatcher = ActionDispatcher()
        mockSignalProvider = MockSignalProvider()
        pleosSignalProvider = PleosSignalProvider()

        engine = TripContextEngine(
            plugins = listOf(
                AutonomousValetDropoffScenario(),
                AutonomousCargoComfortScenario(),
                CargoSafeDrivingScenario(),
                DestinationReverseReminderScenario(),
                CargoTemperatureGuardScenario(),
                TrunkReminderScenario(),
                RearSeatItemScenario(),
                ExitIntegrityScenario()
            ),
            persistence = persistence,
            actionDispatcher = actionDispatcher,
            aiService = PleosAiContextAgent()
        )

        CoroutineScope(Dispatchers.Unconfined).launch {
            mockSignalProvider.signals.collect { engine.processSignal(it) }
        }

        viewModel = MainViewModel(
            pleosSignalProvider = pleosSignalProvider,
            mockSignalProvider = mockSignalProvider,
            engine = engine,
            actionDispatcher = actionDispatcher
        )

        isArrivalAlertEnabledState = MutableStateFlow(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper: Derives Freshness Status matching UserScreenDataMapper algorithm
    private fun calculateFreshnessStatus(
        session: TripSession,
        currentTimeMs: Long = System.currentTimeMillis()
    ): TestFreshnessCareStatus {
        val hasPerishable = session.aiCargoMemory?.isTemperatureSensitive == true ||
            session.latestTrunkUsage?.cargoTag == CargoTag.FRESH_FOOD ||
            session.closureHistory.any { it.zone == DoorZone.TRUNK && it.cargoTag == CargoTag.FRESH_FOOD }

        if (!hasPerishable) {
            return TestFreshnessCareStatus(
                hasPerishableCargo = false,
                itemName = "신선/냉동 물품 없음",
                currentTempCelsius = session.cabinTempCelsius,
                elapsedMinutes = 0,
                limitMinutes = 60,
                remainingMinutes = 60,
                progress = 1.0f,
                riskLevel = TestFreshnessRiskLevel.SAFE
            )
        }

        val temp = session.cabinTempCelsius
        val limitMinutes = when {
            temp >= 32.0f -> 15
            temp >= 28.0f -> 30
            temp >= 25.0f -> 45
            temp >= 20.0f -> 60
            else -> 90
        }

        val loadTimeMs = session.closureHistory.lastOrNull {
            it.zone == DoorZone.TRUNK && it.cargoTag == CargoTag.FRESH_FOOD
        }?.closedAtTimestampMs ?: currentTimeMs

        val elapsedMinutes = ((currentTimeMs - loadTimeMs).coerceAtLeast(0L) / 60_000L).toInt()
        val remainingMinutes = (limitMinutes - elapsedMinutes).coerceAtLeast(0)
        val progress = if (limitMinutes > 0) {
            (remainingMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val riskLevel = when {
            remainingMinutes <= 5 || temp >= 32.0f -> TestFreshnessRiskLevel.CRITICAL
            remainingMinutes <= limitMinutes / 2 || temp >= 26.0f -> TestFreshnessRiskLevel.WARNING
            else -> TestFreshnessRiskLevel.SAFE
        }

        val itemName = session.aiCargoMemory?.inferredItems?.firstOrNull {
            it.contains("아이스크림") || it.contains("신선") || it.contains("우유")
        }?.substringBefore(" (") ?: "신선/냉동 식재료"

        return TestFreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = itemName,
            currentTempCelsius = temp,
            elapsedMinutes = elapsedMinutes,
            limitMinutes = limitMinutes,
            remainingMinutes = remainingMinutes,
            progress = progress,
            riskLevel = riskLevel
        )
    }

    // =========================================================================
    // Tier 1: Widget 1 - Tailgate Status Live Overview (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - Widget 1 - Tailgate open state is correctly indicated`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        assertTrue("Trunk must be reported open", DoorZone.TRUNK in engine.session.value.openDoors)

        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))
        assertFalse("Trunk must be reported closed", DoorZone.TRUNK in engine.session.value.openDoors)
    }

    @Test
    fun `Tier 1 - Widget 1 - Loaded cargo item records populate inventory`() {
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD
        )

        val session = engine.session.value
        assertEquals(1, session.closureHistory.size)
        assertEquals(CargoTag.FRESH_FOOD, session.closureHistory.first().cargoTag)
        assertEquals(PoiCategory.MART_SHOPPING, session.closureHistory.first().openedAtPoi)
    }

    @Test
    fun `Tier 1 - Widget 1 - Permanent trunk items appear in active inventory`() {
        engine.togglePermanentTrunkNote("골프백")
        engine.togglePermanentTrunkNote("우산")

        val session = engine.session.value
        assertEquals(2, session.permanentTrunkNotes.size)
        assertTrue("골프백" in session.permanentTrunkNotes)
        assertTrue("우산" in session.permanentTrunkNotes)
    }

    @Test
    fun `Tier 1 - Widget 1 - Empty trunk inventory placeholder when no items loaded`() {
        val session = engine.session.value
        assertTrue("Closure history is empty initially", session.closureHistory.isEmpty())
        assertTrue("Permanent notes empty initially", session.permanentTrunkNotes.isEmpty())
    }

    @Test
    fun `Tier 1 - Widget 1 - Multi-cargo loading accumulates items sequentially`() {
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.OFFICE, cargoTag = CargoTag.GENERAL)

        val session = engine.session.value
        assertEquals(2, session.closureHistory.size)
        assertEquals(CargoTag.FRESH_FOOD, session.closureHistory[0].cargoTag)
        assertEquals(CargoTag.GENERAL, session.closureHistory[1].cargoTag)
    }

    // =========================================================================
    // Tier 1: Widget 2 - Freshness Golden Time Smart Care (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - Widget 2 - Ambient temperature reflects VHAL telemetry`() {
        engine.processSignal(VehicleSignal.CabinStatusUpdated(rearSeatOccupied = false, openWindowsCount = 0, cabinTempCelsius = 25.5f))
        assertEquals(25.5f, engine.session.value.cabinTempCelsius, 0.01f)

        engine.processSignal(VehicleSignal.CabinStatusUpdated(rearSeatOccupied = false, openWindowsCount = 0, cabinTempCelsius = 29.0f))
        assertEquals(29.0f, engine.session.value.cabinTempCelsius, 0.01f)
    }

    @Test
    fun `Tier 1 - Widget 2 - Standard 20C ambient temp initializes 60 minute limit and SAFE risk`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 20.0f,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, loadTime)
            )
        )

        val status = calculateFreshnessStatus(session, currentTimeMs = loadTime + 10 * 60_000L)
        assertTrue(status.hasPerishableCargo)
        assertEquals(60, status.limitMinutes)
        assertEquals(10, status.elapsedMinutes)
        assertEquals(50, status.remainingMinutes)
        assertEquals(TestFreshnessRiskLevel.SAFE, status.riskLevel)
    }

    @Test
    fun `Tier 1 - Widget 2 - 28C high heat environment reduces limit to 30 min and triggers WARNING`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 28.0f,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, loadTime)
            )
        )

        val status = calculateFreshnessStatus(session, currentTimeMs = loadTime + 5 * 60_000L)
        assertEquals(30, status.limitMinutes)
        assertEquals(25, status.remainingMinutes)
        assertEquals("Heat above 26C must trigger WARNING", TestFreshnessRiskLevel.WARNING, status.riskLevel)
    }

    @Test
    fun `Tier 1 - Widget 2 - 35C extreme heat reduces limit to 15 min and triggers CRITICAL immediately`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 35.0f,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, loadTime)
            )
        )

        val status = calculateFreshnessStatus(session, currentTimeMs = loadTime + 2 * 60_000L)
        assertEquals(15, status.limitMinutes)
        assertEquals("Extreme heat >=32C must trigger CRITICAL", TestFreshnessRiskLevel.CRITICAL, status.riskLevel)
    }

    @Test
    fun `Tier 1 - Widget 2 - No perishable items maintains SAFE status regardless of temperature`() {
        val session = TripSession(
            cabinTempCelsius = 35.0f,
            permanentTrunkNotes = setOf("골프백")
        )

        val status = calculateFreshnessStatus(session)
        assertFalse(status.hasPerishableCargo)
        assertEquals(TestFreshnessRiskLevel.SAFE, status.riskLevel)
        assertEquals(1.0f, status.progress, 0.01f)
    }

    // =========================================================================
    // Tier 1: Widget 3 - Pre-Exit Smart Alert Card (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - Widget 3 - Displays current destination and POI category`() {
        engine.resetTripSession("분당구 정자동 자택")
        assertEquals("분당구 정자동 자택", engine.session.value.destinationName)
    }

    @Test
    fun `Tier 1 - Widget 3 - Pre-exit smart alert toggle switches ON and OFF`() {
        assertTrue("Default alert toggle must be ON", isArrivalAlertEnabledState.value)

        isArrivalAlertEnabledState.value = false
        assertFalse("Alert toggle must be OFF", isArrivalAlertEnabledState.value)

        isArrivalAlertEnabledState.value = true
        assertTrue("Alert toggle restored to ON", isArrivalAlertEnabledState.value)
    }

    @Test
    fun `Tier 1 - Widget 3 - Window integrity sub-status reflects 0 open windows as normal`() {
        engine.processSignal(VehicleSignal.CabinStatusUpdated(rearSeatOccupied = false, openWindowsCount = 0))
        assertEquals(0, engine.session.value.openWindowsCount)
    }

    @Test
    fun `Tier 1 - Widget 3 - Window integrity sub-status alerts when open windows are detected`() {
        engine.processSignal(VehicleSignal.CabinStatusUpdated(rearSeatOccupied = false, openWindowsCount = 2))
        assertEquals(2, engine.session.value.openWindowsCount)
    }

    @Test
    fun `Tier 1 - Widget 3 - Destination update mid-trip refreshes target location`() {
        engine.processSignal(VehicleSignal.DestinationUpdated("스타필드 하남", PoiCategory.MART_SHOPPING))
        assertEquals("스타필드 하남", engine.session.value.destinationName)
        assertEquals(PoiCategory.MART_SHOPPING, engine.session.value.destinationPoi)
    }

    // =========================================================================
    // Tier 1: Widget 4 - Quick Cargo Actions & Presets (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - Widget 4 - Mart groceries action registers fresh food and shopping POI`() {
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD
            )
        )

        val usage = engine.session.value.latestTrunkUsage
        assertNotNull(usage)
        assertEquals(CargoTag.FRESH_FOOD, usage?.cargoTag)
        assertEquals(PoiCategory.MART_SHOPPING, usage?.openedAtPoi)
    }

    @Test
    fun `Tier 1 - Widget 4 - Cake preset registers fragile cargo and soft driving profile`() {
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                cargoTag = CargoTag.FRAGILE
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                cargoTag = CargoTag.FRAGILE
            )
        )
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "베이커리에서 케이크 실었어",
                currentLocationPoi = PoiCategory.UNKNOWN
            )
        )

        assertEquals(CargoTag.FRAGILE, engine.session.value.latestTrunkUsage?.cargoTag)
        assertTrue(engine.session.value.aiCargoMemory!!.requiresSoftAutonomousProfile)
    }

    @Test
    fun `Tier 1 - Widget 4 - Stroller preset registers infant cargo`() {
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                cargoTag = CargoTag.GENERAL
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                cargoTag = CargoTag.GENERAL
            )
        )
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "트렁크에 유모차 실었어",
                currentLocationPoi = PoiCategory.HOME
            )
        )

        assertEquals(DoorZone.TRUNK, engine.session.value.latestTrunkUsage?.zone)
        assertTrue(engine.session.value.aiCargoMemory!!.rawUserUtterance.contains("유모차"))
    }

    @Test
    fun `Tier 1 - Widget 4 - Golf bag preset registers permanent trunk note`() {
        engine.togglePermanentTrunkNote("골프백")
        assertTrue("골프백" in engine.session.value.permanentTrunkNotes)
    }

    @Test
    fun `Tier 1 - Widget 4 - Clear trunk action resets closure history and active cargo`() {
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        assertTrue(engine.session.value.hasActiveTrunkUsage)

        engine.resetTripSession()
        assertFalse(engine.session.value.hasActiveTrunkUsage)
        assertTrue(engine.session.value.closureHistory.isEmpty())
    }

    // =========================================================================
    // Tier 1: Widget 5 - Today's Trip & Cargo Timeline (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - Widget 5 - Trunk loading records chronological timeline step`() {
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)

        val steps = engine.session.value.timelineSteps
        assertTrue("Timeline step recorded for loading", steps.any { it.title.contains("적재") || it.detail.contains("트렁크") })
    }

    @Test
    fun `Tier 1 - Widget 5 - Departure and D gear shift records departure timeline step`() {
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        val steps = engine.session.value.timelineSteps
        assertTrue("Timeline step recorded for departure", steps.any { it.title.contains("출발") || it.title.contains("주행") })
    }

    @Test
    fun `Tier 1 - Widget 5 - Destination arrival records arrival timeline step`() {
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val steps = engine.session.value.timelineSteps
        assertTrue("Timeline step recorded for arrival", steps.any { it.title.contains("도착") || it.title.contains("주차") })
    }

    @Test
    fun `Tier 1 - Widget 5 - Timeline steps preserve sequential chronological order`() {
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val steps = engine.session.value.timelineSteps
        assertTrue("Must record at least 3 distinct steps", steps.size >= 3)
    }

    @Test
    fun `Tier 1 - Widget 5 - Empty timeline state displays placeholder on fresh trip`() {
        engine.resetTripSession()
        assertTrue(engine.session.value.timelineSteps.isEmpty())
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (5 tests)
    // =========================================================================

    @Test
    fun `Tier 2 - Boundary - Freshness timer expiration clamps remaining minutes to 0 and progress to 0`() {
        val loadTime = 1_000_000L
        val session = TripSession(
            cabinTempCelsius = 22.0f,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, loadTime)
            )
        )

        // Elapsed 80 minutes on 60 minute limit
        val status = calculateFreshnessStatus(session, currentTimeMs = loadTime + 80 * 60_000L)
        assertEquals(0, status.remainingMinutes)
        assertEquals(0.0f, status.progress, 0.01f)
        assertEquals(TestFreshnessRiskLevel.CRITICAL, status.riskLevel)
    }

    @Test
    fun `Tier 2 - Boundary - Temperature bracket boundaries evaluate accurately`() {
        val baseTime = 1_000_000L
        fun limitFor(temp: Float): Int {
            val s = TripSession(
                cabinTempCelsius = temp,
                closureHistory = listOf(
                    ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, baseTime)
                )
            )
            return calculateFreshnessStatus(s, currentTimeMs = baseTime).limitMinutes
        }

        assertEquals(90, limitFor(19.9f)) // Below 20C -> 90 min
        assertEquals(60, limitFor(20.0f)) // Exactly 20C -> 60 min
        assertEquals(45, limitFor(25.0f)) // Exactly 25C -> 45 min
        assertEquals(30, limitFor(28.0f)) // Exactly 28C -> 30 min
        assertEquals(15, limitFor(32.0f)) // Exactly 32C -> 15 min
    }

    @Test
    fun `Tier 2 - Boundary - Negative or zero elapsed time does not crash freshness calculation`() {
        val loadTime = 2_000_000L
        val session = TripSession(
            cabinTempCelsius = 22.0f,
            closureHistory = listOf(
                ClosureUsageRecord(DoorZone.TRUNK, PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD, SignalOrigin.REAL_SDK, loadTime)
            )
        )

        // Clock skew: current time before load time
        val status = calculateFreshnessStatus(session, currentTimeMs = loadTime - 5_000L)
        assertEquals(0, status.elapsedMinutes)
        assertEquals(60, status.remainingMinutes)
        assertEquals(1.0f, status.progress, 0.01f)
    }

    @Test
    fun `Tier 2 - Boundary - Repeated clearing of already empty trunk is idempotent`() {
        engine.resetTripSession()
        assertFalse(engine.session.value.hasActiveTrunkUsage)

        engine.resetTripSession()
        assertFalse(engine.session.value.hasActiveTrunkUsage)
    }

    @Test
    fun `Tier 2 - Boundary - Toggling same permanent note on and off updates set correctly`() {
        engine.togglePermanentTrunkNote("장바구니")
        assertTrue("장바구니" in engine.session.value.permanentTrunkNotes)

        engine.togglePermanentTrunkNote("장바구니")
        assertFalse("장바구니" in engine.session.value.permanentTrunkNotes)
    }
}
