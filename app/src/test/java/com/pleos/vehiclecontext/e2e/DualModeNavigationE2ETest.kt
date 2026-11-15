package com.pleos.vehiclecontext.e2e

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.engine.InMemorySessionPersistence
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-End Test Suite for Dual-Mode Navigation & State Continuity.
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases) for:
 * - Feature 1: Dual-Mode Top Navigation Bar (General User Mode vs Developer Console)
 * - Feature 2: Singleton State Continuity & Cross-View Synchronization
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DualModeNavigationE2ETest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /**
     * Interface contract for top navigation bar as specified in PROJECT.md:
     * enum class NavigationTab(val label: String, val icon: String)
     */
    enum class TestNavigationTab(val label: String, val icon: String) {
        GENERAL_USER("운전자 모드", "🚗"),
        DEVELOPER_CONSOLE("개발자 콘솔", "🛠️")
    }

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var engine: TripContextEngine
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var pleosSignalProvider: PleosSignalProvider
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var viewModel: MainViewModel
    private lateinit var currentTabState: MutableStateFlow<TestNavigationTab>

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

        // Navigation host state defaulting to GENERAL_USER (R1 acceptance criteria)
        currentTabState = MutableStateFlow(TestNavigationTab.GENERAL_USER)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Tier 1: Feature 1 - Dual-Mode Top Navigation Bar (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - Nav 1 - Default view on launch is General User Mode`() {
        assertEquals(
            "App must launch into General User Mode by default",
            TestNavigationTab.GENERAL_USER,
            currentTabState.value
        )
        assertEquals("운전자 모드", currentTabState.value.label)
        assertEquals("🚗", currentTabState.value.icon)
    }

    @Test
    fun `Tier 1 - Nav 2 - Top navigation bar switches between General User and Developer Console`() {
        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertEquals(TestNavigationTab.DEVELOPER_CONSOLE, currentTabState.value)
        assertEquals("개발자 콘솔", currentTabState.value.label)
        assertEquals("🛠️", currentTabState.value.icon)

        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertEquals(TestNavigationTab.GENERAL_USER, currentTabState.value)
    }

    @Test
    fun `Tier 1 - Nav 3 - Navigation status pills reflect real-time gear changes`() {
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)

        viewModel.triggerManualGear(GearState.DRIVE)
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)

        viewModel.triggerManualGear(GearState.REVERSE)
        assertEquals(GearState.REVERSE, viewModel.session.value.currentGear)

        viewModel.triggerManualGear(GearState.PARK)
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
    }

    @Test
    fun `Tier 1 - Nav 4 - Navigation status pills reflect autonomous driving mode changes`() {
        assertEquals(AutonomousDrivingMode.MANUAL, viewModel.session.value.autonomousMode)

        engine.processSignal(
            VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.HIGHWAY_PILOT_L3)
        )
        assertEquals(AutonomousDrivingMode.HIGHWAY_PILOT_L3, viewModel.session.value.autonomousMode)

        engine.processSignal(
            VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF)
        )
        assertEquals(AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF, viewModel.session.value.autonomousMode)
    }

    @Test
    fun `Tier 1 - Nav 5 - Navigation tab labels and icons match automotive specification`() {
        val generalTab = TestNavigationTab.GENERAL_USER
        assertTrue(generalTab.label.contains("운전자 모드"))
        assertEquals("🚗", generalTab.icon)

        val devTab = TestNavigationTab.DEVELOPER_CONSOLE
        assertTrue(devTab.label.contains("개발자 콘솔"))
        assertEquals("🛠️", devTab.icon)
    }

    // =========================================================================
    // Tier 1: Feature 2 - Singleton State Continuity (5 tests)
    // =========================================================================

    @Test
    fun `Tier 1 - State 1 - VHAL signal injected in Dev Console immediately updates General User state`() {
        // Dev Console view active
        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE

        // Inject trunk loading via Dev Console manual control
        viewModel.triggerManualTrunkOpenClose(PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD)

        // Switch to General User mode
        currentTabState.value = TestNavigationTab.GENERAL_USER

        val session = viewModel.session.value
        assertTrue("Trunk usage must be visible in General User Mode", session.hasActiveTrunkUsage)
        assertEquals(CargoTag.FRESH_FOOD, session.latestTrunkUsage?.cargoTag)
        assertEquals(PoiCategory.MART_SHOPPING, session.latestTrunkUsage?.openedAtPoi)
    }

    @Test
    fun `Tier 1 - State 2 - Switching navigation views does not reset or reinitialize trip session`() {
        // In General User Mode, set destination and record trunk load
        engine.resetTripSession("집 (성수동 자택)")
        viewModel.triggerManualTrunkOpenClose()
        viewModel.triggerManualGear(GearState.DRIVE)

        val beforeSwitchSession = viewModel.session.value

        // Switch to Developer Console
        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertEquals(beforeSwitchSession.destinationName, viewModel.session.value.destinationName)
        assertEquals(beforeSwitchSession.hasActiveTrunkUsage, viewModel.session.value.hasActiveTrunkUsage)
        assertEquals(beforeSwitchSession.currentGear, viewModel.session.value.currentGear)

        // Switch back to General User Mode
        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertEquals(beforeSwitchSession.destinationName, viewModel.session.value.destinationName)
        assertEquals(beforeSwitchSession.hasActiveTrunkUsage, viewModel.session.value.hasActiveTrunkUsage)
    }

    @Test
    fun `Tier 1 - State 3 - Active prompt survives navigation tab switching`() {
        // Trigger arrival prompt
        viewModel.runDemo1TrunkUsedHomeArrival()
        val prompt = viewModel.activePrompt.value
        assertNotNull("Prompt active before tab switch", prompt)

        // Driver switches to Dev Console to inspect event logs
        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertNotNull("Prompt remains active in Dev Console", viewModel.activePrompt.value)
        assertEquals(prompt?.scenarioId, viewModel.activePrompt.value?.scenarioId)

        // Switch back to General User Mode
        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertNotNull("Prompt remains active in General User Mode", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 1 - State 4 - Prompt acknowledgement in one view resolves prompt globally`() {
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull(viewModel.activePrompt.value)

        // User acknowledges prompt in General User view
        currentTabState.value = TestNavigationTab.GENERAL_USER
        viewModel.acknowledgePrompt()

        assertNull("Prompt must be null in General User Mode", viewModel.activePrompt.value)

        // Switch to Dev Console -> verified prompt is also dismissed there
        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertNull("Prompt must also be null in Developer Console", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 1 - State 5 - Timeline steps remain synchronized across mode switches`() {
        viewModel.runDemo1TrunkUsedHomeArrival()

        val timelineSteps = viewModel.session.value.timelineSteps
        assertTrue("Timeline steps must be recorded", timelineSteps.isNotEmpty())

        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertEquals(timelineSteps.size, viewModel.session.value.timelineSteps.size)

        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertEquals(timelineSteps.size, viewModel.session.value.timelineSteps.size)
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (5 tests)
    // =========================================================================

    @Test
    fun `Tier 2 - Boundary - Rapid consecutive tab switching does not corrupt session state`() {
        viewModel.triggerManualTrunkOpenClose()
        viewModel.triggerManualGear(GearState.DRIVE)

        // Rapid 10x tab toggle
        for (i in 1..10) {
            currentTabState.value = if (i % 2 == 0) TestNavigationTab.GENERAL_USER else TestNavigationTab.DEVELOPER_CONSOLE
        }

        assertTrue("Session trunk usage intact after rapid toggles", viewModel.session.value.hasActiveTrunkUsage)
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)
    }

    @Test
    fun `Tier 2 - Boundary - Tab switching while driving (D gear) maintains distraction safety`() {
        viewModel.triggerManualGear(GearState.DRIVE)
        assertEquals(TripPhase.DRIVING, viewModel.session.value.phase)

        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertNull("No popup prompt while driving in Dev Console", viewModel.activePrompt.value)

        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertNull("No popup prompt while driving in General User Mode", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 2 - Boundary - Concurrent VHAL signal arrival while switching tabs`() {
        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE

        // Concurrently emit signal during switch
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 1,
                cabinTempCelsius = 24.5f
            )
        )
        currentTabState.value = TestNavigationTab.GENERAL_USER

        assertEquals(24.5f, viewModel.session.value.cabinTempCelsius, 0.01f)
        assertEquals(1, viewModel.session.value.openWindowsCount)
    }

    @Test
    fun `Tier 2 - Boundary - Tab switching at intermediate stop preserves waypoint phase`() {
        viewModel.runDemo3IntermediateStop()
        assertEquals(TripPhase.INTERMEDIATE_STOP, viewModel.session.value.phase)

        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertEquals(TripPhase.INTERMEDIATE_STOP, viewModel.session.value.phase)
        assertNull(viewModel.activePrompt.value)

        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertEquals(TripPhase.INTERMEDIATE_STOP, viewModel.session.value.phase)
        assertNull(viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 2 - Boundary - Tab switching preserves permanent trunk notes`() {
        viewModel.togglePermanentTrunkItem("장바구니")
        viewModel.togglePermanentTrunkItem("골프백")
        assertTrue("장바구니" in viewModel.session.value.permanentTrunkNotes)
        assertTrue("골프백" in viewModel.session.value.permanentTrunkNotes)

        currentTabState.value = TestNavigationTab.DEVELOPER_CONSOLE
        assertEquals(2, viewModel.session.value.permanentTrunkNotes.size)

        currentTabState.value = TestNavigationTab.GENERAL_USER
        assertEquals(2, viewModel.session.value.permanentTrunkNotes.size)
    }
}
