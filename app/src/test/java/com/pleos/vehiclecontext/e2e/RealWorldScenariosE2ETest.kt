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
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousCargoComfortScenario
import com.pleos.vehiclecontext.scenarios.cabin.CargoTemperatureGuardScenario
import com.pleos.vehiclecontext.scenarios.exit.ExitIntegrityScenario
import com.pleos.vehiclecontext.scenarios.trunk.DestinationReverseReminderScenario
import com.pleos.vehiclecontext.scenarios.trunk.TrunkReminderScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Application Scenarios E2E Tests.
 * Simulates complete, multi-stage end-to-end driver journeys:
 * - Scenario 1: E-Mart Shopping to Home Arrival (Perishable Groceries)
 * - Scenario 2: Fragile Birthday Cake Delivery (Highway L3 Comfort Guard)
 * - Scenario 3: Weekend Golf Club Trip (Reverse Trunk Reminder)
 * - Scenario 4: Extreme Summer Heat Wave Alert (Rapid Cabin Cooling)
 * - Scenario 5: Family Park Outing (Stroller & Window Exit Integrity Guard)
 */
class RealWorldScenariosE2ETest {

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var engine: TripContextEngine
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var actionDispatcher: ActionDispatcher

    @Before
    fun setUp() {
        persistence = InMemorySessionPersistence()
        actionDispatcher = ActionDispatcher()
        mockSignalProvider = MockSignalProvider()

        engine = TripContextEngine(
            plugins = listOf(
                AutonomousCargoComfortScenario(),
                DestinationReverseReminderScenario(),
                CargoTemperatureGuardScenario(),
                TrunkReminderScenario(),
                ExitIntegrityScenario()
            ),
            persistence = persistence,
            actionDispatcher = actionDispatcher,
            aiService = PleosAiContextAgent()
        )

        CoroutineScope(Dispatchers.Unconfined).launch {
            mockSignalProvider.signals.collect { engine.processSignal(it) }
        }
    }

    // =========================================================================
    // Scenario 1: E-Mart Grocery Shopping to Home Arrival
    // =========================================================================

    @Test
    fun `Tier 4 - Scenario 1 - Complete E-Mart grocery run from store loading to home arrival prompt`() {
        // Stage 1: Arrive at E-Mart parking lot, park in P gear
        engine.resetTripSession("집 (성수동 자택)")
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertEquals(TripPhase.PRE_TRIP, engine.session.value.phase)

        // Stage 2: Driver opens trunk, loads groceries, speaks AI voice note, closes trunk
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD
            )
        )
        assertTrue(DoorZone.TRUNK in engine.session.value.openDoors)

        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "이마트에서 우유, 신선 채소, 아이스크림 샀어",
                currentLocationPoi = PoiCategory.MART_SHOPPING
            )
        )
        assertNotNull(engine.session.value.aiCargoMemory)
        assertTrue(engine.session.value.aiCargoMemory!!.isTemperatureSensitive)

        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD
            )
        )
        assertFalse(DoorZone.TRUNK in engine.session.value.openDoors)
        assertTrue(engine.session.value.hasActiveTrunkUsage)

        // Stage 3: Driver enters vehicle, shifts to DRIVE, begins journey
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        assertEquals(TripPhase.DRIVING, engine.session.value.phase)
        assertNull("Driver distraction guard: no prompt during drive", engine.activePrompt.value)

        // Stage 4: Arrive at Home ("집 (성수동 자택)")
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        assertTrue(engine.session.value.isArrivedAtDestination)
        assertNull("Wait for P gear before firing prompt", engine.activePrompt.value)

        // Stage 5: Shift to PARK -> Arrival Briefing popup triggers immediately
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        val prompt = engine.activePrompt.value
        assertNotNull("Arrival briefing must activate upon PARK", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
        assertTrue("Prompt body mentions trunk", prompt!!.bodyMessage.contains("트렁크"))

        // Stage 6: Driver acknowledges prompt, opens trunk to unload
        engine.acknowledgeActivePrompt()
        assertNull("Prompt dismissed after acknowledgement", engine.activePrompt.value)

        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        assertTrue("Trunk opened for unloading", DoorZone.TRUNK in engine.session.value.openDoors)
    }

    // =========================================================================
    // Scenario 2: Fragile Birthday Cake Delivery with Highway Pilot
    // =========================================================================

    @Test
    fun `Tier 4 - Scenario 2 - Fragile cake transport on highway engages Autonomous Cargo Comfort Guard`() {
        // Stage 1: Load delicate cake at bakery
        engine.resetTripSession("친구 생일 파티장")
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.UNKNOWN,
                cargoTag = CargoTag.FRAGILE
            )
        )
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "베이커리에서 3단 생일 케이크랑 샴페인 실었어",
                currentLocationPoi = PoiCategory.UNKNOWN
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.UNKNOWN,
                cargoTag = CargoTag.FRAGILE
            )
        )
        assertTrue(engine.session.value.aiCargoMemory!!.requiresSoftAutonomousProfile)

        // Stage 2: Merge onto highway and engage L3 Highway Pilot
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.HIGHWAY_PILOT_L3))

        // Stage 3: Autonomous Cargo Comfort Guard recommends soft acceleration profile
        val comfortPrompt = engine.activePrompt.value
        assertNotNull("Comfort switch recommendation must fire", comfortPrompt)
        assertEquals(AutonomousCargoComfortScenario.ID, comfortPrompt?.scenarioId)

        // Stage 4: Driver switches profile to CARGO_COMFORT_GUARD
        engine.executePromptControl(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT)
        assertEquals(AutonomousDrivingProfile.CARGO_COMFORT_GUARD, engine.session.value.autonomousProfile)
        assertNull(engine.activePrompt.value)

        // Stage 5: Arrive safely at party destination
        engine.processSignal(VehicleSignal.DestinationArrived("친구 생일 파티장", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val arrivalPrompt = engine.activePrompt.value
        assertNotNull("Arrival briefing reminds driver to carefully unload cake", arrivalPrompt)
        assertEquals(TrunkReminderScenario.ID, arrivalPrompt?.scenarioId)
    }

    // =========================================================================
    // Scenario 3: Weekend Golf Club Trip with Reverse Trunk Reminder
    // =========================================================================

    @Test
    fun `Tier 4 - Scenario 3 - Permanent golf bag note triggers reverse reminder upon arriving at Golf Club`() {
        // Stage 1: Permanent golf bag stored in trunk
        engine.togglePermanentTrunkNote("골프백")
        assertTrue("골프백" in engine.session.value.permanentTrunkNotes)

        // Stage 2: Driver sets destination to Golf Club and drives
        engine.processSignal(VehicleSignal.DestinationUpdated("남여주 컨트리클럽", PoiCategory.LEISURE_CAMPING))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        assertEquals(TripPhase.DRIVING, engine.session.value.phase)

        // Stage 3: Arrive at Golf Club and park in P gear
        engine.processSignal(VehicleSignal.DestinationArrived("남여주 컨트리클럽", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        // Stage 4: Reverse reminder fires, reminding driver to retrieve golf bag before heading to clubhouse
        val prompt = engine.activePrompt.value
        assertNotNull("Reverse reminder must trigger at golf club", prompt)
        assertEquals(DestinationReverseReminderScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("골프백"))

        // Stage 5: Driver acknowledges and unloads golf bag
        engine.acknowledgeActivePrompt()
        assertNull(engine.activePrompt.value)
    }

    // =========================================================================
    // Scenario 4: Extreme Summer Heat Wave Alert with Rapid Cabin Cooling
    // =========================================================================

    @Test
    fun `Tier 4 - Scenario 4 - Extreme heat at intermediate stop prompts emergency cabin cooling`() {
        // Stage 1: Summer heat wave (cabin at 31.0C). Load ice cream at supermarket.
        engine.resetTripSession("집 (성수동 자택)")
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 31.0f
            )
        )
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

        // Stage 2: Drive along city road
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // Stage 3: Unscheduled intermediate stop at pharmacy (not final destination)
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertEquals(TripPhase.INTERMEDIATE_STOP, engine.session.value.phase)

        // Stage 4: High temperature guard fires because cabinTemp is 31C with perishable cargo
        val heatPrompt = engine.activePrompt.value
        assertNotNull("Heat risk prompt must fire at intermediate stop", heatPrompt)
        assertEquals(CargoTemperatureGuardScenario.ID, heatPrompt?.scenarioId)
        assertTrue(heatPrompt!!.availableControls.contains(VehicleControlCommand.ACTIVATE_CABIN_COOLING))

        // Stage 5: Driver activates emergency cabin cooling -> cools down to 20.0C
        engine.executePromptControl(VehicleControlCommand.ACTIVATE_CABIN_COOLING)
        assertEquals(20.0f, engine.session.value.cabinTempCelsius, 0.01f)
        assertNull(engine.activePrompt.value)

        // Stage 6: Resume drive home and arrive safely
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val homePrompt = engine.activePrompt.value
        assertNotNull("Final arrival prompt fires", homePrompt)
        assertEquals(TrunkReminderScenario.ID, homePrompt?.scenarioId)
    }

    // =========================================================================
    // Scenario 5: Family Park Outing (Stroller & Window Exit Integrity Guard)
    // =========================================================================

    @Test
    fun `Tier 4 - Scenario 5 - Family park trip alerts driver of open windows and stroller upon parking`() {
        // Stage 1: Load stroller at home
        engine.resetTripSession("올림픽공원")
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.HOME,
                cargoTag = CargoTag.GENERAL
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.HOME,
                cargoTag = CargoTag.GENERAL
            )
        )

        // Stage 2: Drive to park, 2 windows opened for ventilation
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = true,
                openWindowsCount = 2
            )
        )

        // Stage 3: Arrive at park and shift to PARK
        engine.processSignal(VehicleSignal.DestinationArrived("올림픽공원", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        // Stage 4: Trunk reminder fires first (higher priority 10) to remind of stroller in trunk
        val prompt = engine.activePrompt.value
        assertNotNull("Trunk reminder must fire upon PARK", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("트렁크"))

        // Stage 5: Driver acknowledges stroller reminder; re-evaluation triggers ExitIntegrityScenario for open windows
        engine.acknowledgeActivePrompt()
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val exitPrompt = engine.activePrompt.value
        assertNotNull("Exit integrity prompt must alert for open windows", exitPrompt)
        assertEquals("exit_window_integrity_guard", exitPrompt?.scenarioId)
        assertTrue(exitPrompt!!.bodyMessage.contains("창문"))
    }
}
