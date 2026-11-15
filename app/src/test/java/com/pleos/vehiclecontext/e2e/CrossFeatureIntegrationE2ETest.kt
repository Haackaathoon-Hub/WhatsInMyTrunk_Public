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
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousValetDropoffScenario
import com.pleos.vehiclecontext.scenarios.cabin.CargoTemperatureGuardScenario
import com.pleos.vehiclecontext.scenarios.cabin.RearSeatItemScenario
import com.pleos.vehiclecontext.scenarios.driving.CargoSafeDrivingScenario
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
 * Tier 3: Cross-Feature Integration E2E Tests.
 * Validates pairwise and multi-module interactions across:
 * - VHAL Injection + Live Engine State
 * - AI Natural Language Processing + Autonomous Driving Profiles
 * - Ambient Temperature + Perishable Cargo + Emergency Actuators
 * - Autonomous Valet Parking + Hardware Departure Hold
 * - Permanent Inventory Notes + POI Reverse Reminders
 * - Multi-Zone Sensing (Trunk + Cabin + Rear Seats + Windows)
 * - Dynamic Rerouting Mid-Trip + Arrival Prompt Re-arming
 * - Session Persistence Recovery Across App Lifecycle
 */
class CrossFeatureIntegrationE2ETest {

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
    }

    // =========================================================================
    // Pair 1: VHAL Injection + Live Overview
    // =========================================================================

    @Test
    fun `Cross-Feature 1 - VHAL injection immediately updates engine session state`() {
        assertFalse(engine.session.value.hasActiveTrunkUsage)
        assertEquals(0, engine.session.value.closureHistory.size)

        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD,
            origin = SignalOrigin.REAL_SDK
        )

        val session = engine.session.value
        assertTrue("Trunk usage must be active after cycle", session.hasActiveTrunkUsage)
        assertEquals(1, session.closureHistory.size)
        assertEquals(DoorZone.TRUNK, session.latestTrunkUsage?.zone)
        assertEquals(CargoTag.FRESH_FOOD, session.latestTrunkUsage?.cargoTag)
        assertEquals(PoiCategory.MART_SHOPPING, session.latestTrunkUsage?.openedAtPoi)
    }

    // =========================================================================
    // Pair 2: AI Voice Utterance + Lv3 Autonomous Mode Profile
    // =========================================================================

    @Test
    fun `Cross-Feature 2 - AI voice utterance enables Autonomous Cargo Comfort switch upon L3 highway pilot`() {
        // Driver mentions delicate items via AI voice
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "베이커리에서 3단 생일 케이크랑 고급 와인 2병 실었어",
                currentLocationPoi = PoiCategory.UNKNOWN,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
        )

        val memory = engine.session.value.aiCargoMemory
        assertNotNull(memory)
        assertTrue("Fragile goods require soft profile", memory!!.requiresSoftAutonomousProfile)

        // Vehicle enters highway and engages L3 autonomous pilot
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.HIGHWAY_PILOT_L3))

        val prompt = engine.activePrompt.value
        assertNotNull("Comfort switch prompt must be presented", prompt)
        assertEquals(AutonomousCargoComfortScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.availableControls.contains(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT))

        // Execute control to switch profile
        engine.executePromptControl(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT)
        assertEquals(
            AutonomousDrivingProfile.CARGO_COMFORT_GUARD,
            engine.session.value.autonomousProfile
        )
    }

    // =========================================================================
    // Pair 3 & 4: Perishable Cargo + High Temperature + Emergency Cabin Cooling
    // =========================================================================

    @Test
    fun `Cross-Feature 3 & 4 - High temp at intermediate stop triggers hazard and cabin cooling lowers temp to 20C`() {
        // Load fresh perishable food
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.MART_SHOPPING,
            cargoTag = CargoTag.FRESH_FOOD
        )

        // Shift to DRIVE and start journey
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // Ambient temperature rises to 28.5C (exceeding 26.0C warning threshold)
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 28.5f
            )
        )

        // Intermediate stop (shift to PARK before final destination)
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("High-temp hazard prompt must be triggered", prompt)
        assertEquals(CargoTemperatureGuardScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.availableControls.contains(VehicleControlCommand.ACTIVATE_CABIN_COOLING))

        // Driver triggers rapid cabin cooling
        engine.executePromptControl(VehicleControlCommand.ACTIVATE_CABIN_COOLING)

        // Verify temperature cooled to 20.0C and prompt dismissed
        assertEquals(20.0f, engine.session.value.cabinTempCelsius, 0.01f)
        assertNull("Prompt must close after cooling command", engine.activePrompt.value)
    }

    // =========================================================================
    // Pair 5: Autonomous Valet Parking + Luggage Departure Hold
    // =========================================================================

    @Test
    fun `Cross-Feature 5 - AVP drop-off mode with loaded trunk engages departure hold and unlocks trunk`() {
        mockSignalProvider.simulateTrunkOpenAndClose(
            poi = PoiCategory.OFFICE,
            cargoTag = CargoTag.GENERAL
        )

        // Vehicle enters AVP drop-off zone
        engine.processSignal(VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF))

        val prompt = engine.activePrompt.value
        assertNotNull("AVP drop-off guard prompt must activate", prompt)
        assertEquals(AutonomousValetDropoffScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.availableControls.contains(VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK))

        // Execute hold
        engine.executePromptControl(VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK)
        assertTrue("Vehicle must hold departure for luggage retrieval", engine.session.value.isAvpDepartureHoldActive)
        assertTrue("Trunk must be forced open", DoorZone.TRUNK in engine.session.value.openDoors)
    }

    // =========================================================================
    // Pair 6: Permanent Stored Notes + POI Reverse Reminder
    // =========================================================================

    @Test
    fun `Cross-Feature 6 - Permanent notes trigger reverse reminder upon arriving at matching POI`() {
        // Register permanent shopping cart note
        engine.togglePermanentTrunkNote("장바구니")
        assertTrue("장바구니" in engine.session.value.permanentTrunkNotes)

        // Drive to E-Mart
        engine.processSignal(VehicleSignal.DestinationUpdated("이마트 역삼점", PoiCategory.MART_SHOPPING))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        assertNull("No prompt while driving", engine.activePrompt.value)

        // Arrive and park in P gear
        engine.processSignal(VehicleSignal.DestinationArrived("이마트 역삼점", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("Reverse reminder prompt must trigger", prompt)
        assertEquals(DestinationReverseReminderScenario.ID, prompt?.scenarioId)
        assertTrue("Prompt body mentions permanent item", prompt!!.bodyMessage.contains("장바구니"))
    }

    // =========================================================================
    // Pair 7: Rear Seat Item Loading + Unoccupied Cabin Alert
    // =========================================================================

    @Test
    fun `Cross-Feature 7 - Rear door loading with unoccupied rear seat alerts driver on home arrival`() {
        // Load laptop bag into rear-left seat at office
        mockSignalProvider.simulateRearDoorLoadItem(PoiCategory.OFFICE)
        assertTrue("Rear seat loading recorded", engine.session.value.hasUnoccupiedRearSeatLoading)

        // Rear seat sensor confirms unoccupied
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0
            )
        )

        // Drive home
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("Rear seat item reminder must fire", prompt)
        assertEquals("rear_seat_item_care", prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("뒷좌석"))
    }

    // =========================================================================
    // Pair 8: Cabin Window Integrity + Exit Guard
    // =========================================================================

    @Test
    fun `Cross-Feature 8 - Parking at home with open windows triggers ExitIntegrityScenario`() {
        // Open 2 windows during drive
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 2
            )
        )
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("Exit integrity prompt must warn about open windows", prompt)
        assertEquals("exit_window_integrity_guard", prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("창문"))
    }

    // =========================================================================
    // Pair 9: Dynamic Rerouting Mid-Trip + Arrival Prompt Re-arming
    // =========================================================================

    @Test
    fun `Cross-Feature 9 - Rerouting mid-drive clears stale prompts and re-arms arrival prompt for new destination`() {
        // Load groceries and depart for Home
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // Change destination mid-trip to Parents' house
        engine.processSignal(VehicleSignal.DestinationUpdated("분당 부모님댁", PoiCategory.HOME))
        assertEquals("분당 부모님댁", engine.session.value.destinationName)
        assertFalse(engine.session.value.isArrivedAtDestination)

        // Arrive at Parents' house
        engine.processSignal(VehicleSignal.DestinationArrived("분당 부모님댁", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("Arrival prompt must fire for new destination", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("분당 부모님댁"))
    }

    // =========================================================================
    // Pair 10: App Process Restart + Crash Recovery
    // =========================================================================

    @Test
    fun `Cross-Feature 10 - Restoring engine from persistence re-arms unacknowledged arrival alert`() {
        // Setup initial trip session with loaded trunk
        mockSignalProvider.simulateTrunkOpenAndClose(poi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD)
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // Simulate app crash and immediate process relaunch using same persistence
        val restartedEngine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            persistence = persistence
        )

        assertTrue("Restored engine preserves active trunk usage", restartedEngine.session.value.hasActiveTrunkUsage)

        // Complete trip on restored engine
        restartedEngine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        restartedEngine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = restartedEngine.activePrompt.value
        assertNotNull("Prompt must trigger on restored engine upon arrival", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
    }
}
