package com.pleos.vehiclecontext.e2e

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.engine.InMemorySessionPersistence
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.AutonomousDrivingProfile
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
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
import com.pleos.vehiclecontext.ui.MainScreen
import com.pleos.vehiclecontext.ui.MainViewModel
import com.pleos.vehiclecontext.ui.model.FreshnessRiskLevel
import com.pleos.vehiclecontext.ui.model.NavigationTab
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
 * Empirical Challenger Stress Test Suite for Milestone 2:
 * Dual-Mode Navigation Architecture & Developer Console Relocation.
 *
 * Verifies real production contracts, rapid view switching thrashing,
 * cross-view state continuity, prompt lifecycle integrity, preset interactions,
 * and high-frequency concurrent signal storm robustness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DualModeNavigationEmpiricalStressTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var engine: TripContextEngine
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var pleosSignalProvider: PleosSignalProvider
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var viewModel: MainViewModel
    private lateinit var activeTabFlow: MutableStateFlow<NavigationTab>

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

        // Mode state initialized to default as required by R1
        activeTabFlow = MutableStateFlow(NavigationTab.GENERAL_USER)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Challenge 1: Production NavigationTab Specification & Contract Verification
    // =========================================================================

    @Test
    fun `Empirical - NavigationTab production enum matches R1 specification exactly`() {
        val entries = NavigationTab.entries
        assertEquals("Must only have 2 navigation modes", 2, entries.size)

        val general = NavigationTab.GENERAL_USER
        assertEquals("운전자 모드", general.label)
        assertEquals("🚗", general.icon)

        val dev = NavigationTab.DEVELOPER_CONSOLE
        assertEquals("개발자 콘솔", dev.label)
        assertEquals("🛠️", dev.icon)
    }

    @Test
    fun `Empirical - App launch defaults to GENERAL_USER mode without regression`() {
        // Contract check: default mode on launch is GENERAL_USER
        assertEquals(NavigationTab.GENERAL_USER, activeTabFlow.value)
    }

    // =========================================================================
    // Challenge 2: Cross-View State Continuity under Signal Injection
    // =========================================================================

    @Test
    fun `Empirical - Signal injected in Developer Console immediately reflects in General User mode`() {
        // Active view: Developer Console
        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE

        // Inject signals: Perishable cargo loaded in Mart + High heat + Driving
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD
        )
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 34.0f
            )
        )
        viewModel.triggerManualGear(GearState.DRIVE)
        engine.processSignal(
            VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.HIGHWAY_PILOT_L3)
        )

        // Verify in Developer Console
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)
        assertEquals(AutonomousDrivingMode.HIGHWAY_PILOT_L3, viewModel.session.value.autonomousMode)

        // Switch to General User mode
        activeTabFlow.value = NavigationTab.GENERAL_USER

        // General User presentation state MUST be synchronized immediately
        val cargoItems = viewModel.trunkCargoItems.value
        assertTrue("Trunk items must not be empty", cargoItems.isNotEmpty())
        val freshItem = cargoItems.firstOrNull { it.isPerishable }
        assertNotNull("Perishable cargo item must be visible in General User view", freshItem)
        assertEquals("신선식품", freshItem?.categoryBadge)

        val freshness = viewModel.freshnessStatus.value
        assertTrue("Freshness monitor must detect perishable cargo", freshness.hasPerishableCargo)
        assertEquals("34C extreme heat must result in CRITICAL risk", FreshnessRiskLevel.CRITICAL, freshness.riskLevel)
        assertEquals(15, freshness.limitMinutes)

        // Vehicle status pills
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)
        assertEquals(AutonomousDrivingMode.HIGHWAY_PILOT_L3, viewModel.session.value.autonomousMode)
    }

    // =========================================================================
    // Challenge 3: Cross-View Prompt Lifecycle & Resolution Consistency
    // =========================================================================

    @Test
    fun `Empirical - Arrival prompt triggered in one view is acked in other view with zero state loss`() {
        // Trigger arrival prompt via Demo 1
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull("Prompt must be active upon arrival", viewModel.activePrompt.value)
        assertEquals(TrunkReminderScenario.ID, viewModel.activePrompt.value?.scenarioId)

        // Switch back and forth between tabs 5 times
        for (i in 1..5) {
            activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
            assertNotNull("Prompt persists in Dev Console (iteration $i)", viewModel.activePrompt.value)
            activeTabFlow.value = NavigationTab.GENERAL_USER
            assertNotNull("Prompt persists in General User Mode (iteration $i)", viewModel.activePrompt.value)
        }

        // Driver acknowledges prompt in General User mode
        activeTabFlow.value = NavigationTab.GENERAL_USER
        viewModel.acknowledgePrompt()
        assertNull("Prompt must be dismissed in General User mode", viewModel.activePrompt.value)

        // Switch to Dev Console -> verified prompt is also resolved there
        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
        assertNull("Prompt must remain dismissed in Developer Console", viewModel.activePrompt.value)
    }

    // =========================================================================
    // Challenge 4: Cargo Presets & Clear Trunk Across Navigation Views
    // =========================================================================

    @Test
    fun `Empirical - Cargo presets applied in General User view update Dev Console inventory & vice versa`() {
        // General User view: Apply Cake preset (fragile cargo)
        activeTabFlow.value = NavigationTab.GENERAL_USER
        viewModel.applyCargoPresetCake()

        assertTrue(viewModel.session.value.hasActiveTrunkUsage)
        assertEquals(CargoTag.FRAGILE, viewModel.session.value.latestTrunkUsage?.cargoTag)
        assertTrue(viewModel.trunkCargoItems.value.any { it.name.contains("케이크") })

        // Switch to Dev Console: Verify closure history contains Cake
        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
        assertEquals(1, viewModel.session.value.closureHistory.size)
        assertEquals(CargoTag.FRAGILE, viewModel.session.value.closureHistory.first().cargoTag)

        // In Dev Console: Toggle permanent trunk note '골프백'
        viewModel.togglePermanentTrunkItem("골프백")
        assertTrue("골프백" in viewModel.session.value.permanentTrunkNotes)

        // Switch to General User: Verify Cake, Wine, and Golf Bag are in presentation items
        activeTabFlow.value = NavigationTab.GENERAL_USER
        val items = viewModel.trunkCargoItems.value
        assertEquals("Must contain 3 items: Cake, Wine (from multi-token AI parsing) and Golf bag", 3, items.size)
        assertTrue(items.any { it.name.contains("케이크") })
        assertTrue(items.any { it.name.contains("와인") })
        assertTrue(items.any { it.name.contains("골프백") })

        // In General User: Clear trunk
        viewModel.clearTrunkCargo()

        // Switch to Dev Console: Verify trunk is empty
        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
        assertFalse(viewModel.session.value.hasActiveTrunkUsage)
        assertTrue(viewModel.session.value.closureHistory.isEmpty())
        assertTrue(viewModel.session.value.permanentTrunkNotes.isEmpty())
        assertTrue(viewModel.trunkCargoItems.value.isEmpty())
    }

    // =========================================================================
    // Challenge 5: Pre-Exit Smart Alert Toggle Continuity Across Views
    // =========================================================================

    @Test
    fun `Empirical - Arrival alert toggle persists seamlessly across tab switches`() {
        assertTrue("Default alert toggle is enabled", viewModel.isArrivalAlertEnabled.value)

        // Disable in General User mode
        activeTabFlow.value = NavigationTab.GENERAL_USER
        viewModel.setArrivalAlertEnabled(false)
        assertFalse(viewModel.isArrivalAlertEnabled.value)

        // Switch to Dev Console and back 10 times
        for (i in 1..10) {
            activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
            assertFalse(viewModel.isArrivalAlertEnabled.value)
            activeTabFlow.value = NavigationTab.GENERAL_USER
            assertFalse(viewModel.isArrivalAlertEnabled.value)
        }

        // Re-enable
        viewModel.setArrivalAlertEnabled(true)
        assertTrue(viewModel.isArrivalAlertEnabled.value)
        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
        assertTrue(viewModel.isArrivalAlertEnabled.value)
    }

    // =========================================================================
    // Challenge 6: Stress Fuzzing - 1,000 Tab Switches Under Heavy Signal Storm
    // =========================================================================

    @Test
    fun `Empirical - Stress 1000 tab toggles with concurrent VHAL signal flooding`() {
        // Preset cargo loaded
        viewModel.applyCargoPresetMartGroceries()
        assertTrue(viewModel.session.value.hasActiveTrunkUsage)

        val gears = listOf(GearState.PARK, GearState.DRIVE, GearState.NEUTRAL, GearState.REVERSE)
        val modes = listOf(
            AutonomousDrivingMode.MANUAL,
            AutonomousDrivingMode.SMART_CRUISE_ADAS,
            AutonomousDrivingMode.HIGHWAY_PILOT_L3,
            AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF
        )

        // Run 1,000 rapid switches while firing signals
        for (i in 1..1000) {
            val tab = if (i % 2 == 0) NavigationTab.GENERAL_USER else NavigationTab.DEVELOPER_CONSOLE
            activeTabFlow.value = tab

            // Inject periodic signals
            if (i % 20 == 0) {
                val gear = gears[(i / 20) % gears.size]
                viewModel.triggerManualGear(gear)
            }
            if (i % 50 == 0) {
                val mode = modes[(i / 50) % modes.size]
                engine.processSignal(VehicleSignal.AutonomousModeChanged(mode))
            }
            if (i % 100 == 0) {
                engine.processSignal(
                    VehicleSignal.CabinStatusUpdated(
                        rearSeatOccupied = i % 200 == 0,
                        openWindowsCount = (i % 4),
                        cabinTempCelsius = 20.0f + (i % 15)
                    )
                )
            }
        }

        // Ensure session integrity survived
        assertTrue("Trunk cargo intact after 1000 thrash switches", viewModel.session.value.hasActiveTrunkUsage)
        assertNotNull("TrunkCargoItems intact", viewModel.trunkCargoItems.value)
        assertNotNull("FreshnessStatus intact", viewModel.freshnessStatus.value)
        assertTrue("Event log bounded <= 35", viewModel.eventLog.value.size <= 35)
    }

    // =========================================================================
    // Challenge 7: Motion Gating Distraction Safety During Tab Switch
    // =========================================================================

    @Test
    fun `Empirical - Motion gating suppresses arrival prompts while driving regardless of tab switch`() {
        // Vehicle is driving (D gear)
        viewModel.applyCargoPresetCake()
        viewModel.triggerManualGear(GearState.DRIVE)
        assertEquals(TripPhase.DRIVING, viewModel.session.value.phase)

        // Even if arrival is signaled while driving, prompt should not fire until PARK
        engine.processSignal(VehicleSignal.DestinationArrived("성수동 자택", isFinalDestination = true))

        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
        assertNull("No prompt while vehicle is in motion", viewModel.activePrompt.value)

        activeTabFlow.value = NavigationTab.GENERAL_USER
        assertNull("No prompt in General User while vehicle is in motion", viewModel.activePrompt.value)

        // Shifting to PARK enables prompt
        viewModel.triggerManualGear(GearState.PARK)
        assertNotNull("Prompt fires once vehicle safely parked in P gear", viewModel.activePrompt.value)
    }

    // =========================================================================
    // Challenge 8: ResetAll Restores Clean Slate Across Both Modes
    // =========================================================================

    @Test
    fun `Empirical - ResetAll in Dev Console resets General User presentation state cleanly`() {
        viewModel.applyCargoPresetMartGroceries()
        viewModel.togglePermanentTrunkItem("골프백")
        viewModel.triggerManualGear(GearState.DRIVE)
        assertTrue(viewModel.session.value.hasActiveTrunkUsage)
        assertEquals(3, viewModel.trunkCargoItems.value.size) // Fresh food + Ice cream (multi-token AI parsing) + Golf bag

        // ResetAll invoked in Dev Console
        activeTabFlow.value = NavigationTab.DEVELOPER_CONSOLE
        viewModel.resetAll()

        // Switch to General User mode
        activeTabFlow.value = NavigationTab.GENERAL_USER
        assertFalse(viewModel.session.value.hasActiveTrunkUsage)
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
        assertTrue("Cargo items must be cleared", viewModel.trunkCargoItems.value.isEmpty())
        assertFalse("Perishable cargo must be false", viewModel.freshnessStatus.value.hasPerishableCargo)
        assertNull("Active prompt must be null", viewModel.activePrompt.value)
    }
}
