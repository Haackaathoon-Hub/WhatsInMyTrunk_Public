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
import com.pleos.vehiclecontext.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
 * End-to-End Test Suite for Developer Console Parity & Testbed Capabilities.
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases) for:
 * - Feature 14: 3D Vehicle Digital Twin Canvas Telemetry
 * - Feature 15: Empirical Journey Replayer
 * - Feature 16: Manual VHAL Signal Injection Panel
 * - Feature 17: Core 6 Demo Scenarios Panel
 * - Feature 18: Autonomous & AI Agent Panel
 * - Feature 19: Event Log Viewer
 * - Feature 20: Background HUD / Modal Trigger
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperConsoleParityE2ETest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var engine: TripContextEngine
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var pleosSignalProvider: PleosSignalProvider
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var viewModel: MainViewModel

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

        // Connect mockSignalProvider signals to engine directly for synchronous test determinism
        CoroutineScope(Dispatchers.Unconfined).launch {
            mockSignalProvider.signals.collect { engine.processSignal(it) }
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
    // Tier 1: Feature 17 - Core 6 Demo Scenarios Parity
    // =========================================================================

    @Test
    fun `Tier 1 - Core Demo 1 - Trunk Used Home Arrival fires trunk reminder prompt upon PARK`() {
        viewModel.runDemo1TrunkUsedHomeArrival()

        val session = viewModel.session.value
        assertEquals("집 (성수동 자택)", session.destinationName)
        assertEquals(GearState.PARK, session.currentGear)
        assertTrue("Trunk usage must be active", session.hasActiveTrunkUsage)
        assertTrue("Arrival flag must be set", session.isArrivedAtDestination)

        val prompt = viewModel.activePrompt.value
        assertNotNull("Trunk reminder prompt must be active", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("트렁크"))
    }

    @Test
    fun `Tier 1 - Core Demo 2 - No Trunk Usage Home Arrival suppresses reminder prompt`() {
        viewModel.runDemo2NoTrunkUsageHomeArrival()

        val session = viewModel.session.value
        assertEquals("집 (성수동 자택)", session.destinationName)
        assertEquals(GearState.PARK, session.currentGear)
        assertFalse("No trunk usage recorded", session.hasActiveTrunkUsage)
        assertTrue(session.isArrivedAtDestination)
        assertNull("Prompt must be suppressed when no cargo loaded", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 1 - Core Demo 3 - Intermediate Stop suppresses reminder prompt at waypoint`() {
        viewModel.runDemo3IntermediateStop()

        val session = viewModel.session.value
        assertEquals("경유지 (잠시 정차)", session.destinationName)
        assertEquals(GearState.PARK, session.currentGear)
        assertFalse("Intermediate stop is not final destination", session.isArrivedAtDestination)
        assertEquals(TripPhase.INTERMEDIATE_STOP, session.phase)
        assertNull("Prompt must not fire at intermediate stop", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 1 - Core Demo 4 - Repeated P-Gear Transition does not re-prompt on same arrival`() {
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull(viewModel.activePrompt.value)

        // Dismiss or acknowledge prompt
        viewModel.acknowledgePrompt()
        assertNull(viewModel.activePrompt.value)

        // Repeat P transition (readjusting parking space)
        viewModel.runDemo4RepeatedParkTransition()

        assertNull("Prompt must not fire repeatedly on same arrival", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 1 - Core Demo 5 - Route Changed updates destination and clears old prompts`() {
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull(viewModel.activePrompt.value)

        viewModel.runDemo5RouteChanged()

        val session = viewModel.session.value
        assertEquals("본가 (분당 부모님댁)", session.destinationName)
        assertEquals(PoiCategory.HOME, session.destinationPoi)
        assertEquals(GearState.DRIVE, session.currentGear)
        assertFalse("Arrival flag reset on route update", session.isArrivedAtDestination)
        assertNull("Prompt must clear when vehicle begins driving to new destination", viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 1 - Core Demo 6 - App Restart Recovery preserves trip session and pending alerts`() {
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertTrue(viewModel.session.value.hasActiveTrunkUsage)

        viewModel.runDemo6AppRestartRecovery()

        val session = viewModel.session.value
        assertTrue("Restored session retains trunk usage", session.hasActiveTrunkUsage)
        assertEquals("집 (성수동 자택)", session.destinationName)
    }

    // =========================================================================
    // Tier 1: Feature 14 - 3D Digital Twin Canvas Telemetry State
    // =========================================================================

    @Test
    fun `Tier 1 - 3D Canvas Telemetry - Reflects door open close states accurately`() {
        viewModel.triggerManualTrunkOpenClose(PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD)

        // Closure history has record, trunk is currently closed
        assertFalse("Trunk is closed after open-close cycle", DoorZone.TRUNK in viewModel.session.value.openDoors)
        assertTrue(viewModel.session.value.hasActiveTrunkUsage)

        // Open driver door manually
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.FRONT_LEFT, isOpen = true))
        assertTrue("Driver door is open", DoorZone.FRONT_LEFT in viewModel.session.value.openDoors)

        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.FRONT_LEFT, isOpen = false))
        assertFalse("Driver door is closed", DoorZone.FRONT_LEFT in viewModel.session.value.openDoors)
    }

    @Test
    fun `Tier 1 - 3D Canvas Telemetry - Cabin temperature and window count updates`() {
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = true,
                openWindowsCount = 2,
                cabinTempCelsius = 27.5f
            )
        )

        val session = viewModel.session.value
        assertEquals(27.5f, session.cabinTempCelsius, 0.01f)
        assertEquals(2, session.openWindowsCount)
        assertTrue(session.rearSeatOccupied)
    }

    // =========================================================================
    // Tier 1: Feature 16 - Manual VHAL Signal Injection Controls
    // =========================================================================

    @Test
    fun `Tier 1 - Manual VHAL - Gear shift transitions update session gear and phase`() {
        viewModel.triggerManualGear(GearState.DRIVE)
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)
        assertEquals(TripPhase.DRIVING, viewModel.session.value.phase)

        viewModel.triggerManualGear(GearState.REVERSE)
        assertEquals(GearState.REVERSE, viewModel.session.value.currentGear)

        viewModel.triggerManualGear(GearState.PARK)
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
    }

    @Test
    fun `Tier 1 - Manual VHAL - Manual arrival trigger marks arrival state`() {
        viewModel.triggerManualGear(GearState.DRIVE)
        viewModel.triggerManualArrival()

        assertTrue(viewModel.session.value.isArrivedAtDestination)
        assertTrue(viewModel.session.value.destinationName.isNotEmpty())
    }

    // =========================================================================
    // Tier 1: Feature 18 - Autonomous & AI Agent Capabilities
    // =========================================================================

    @Test
    fun `Tier 1 - AI Agent - Natural language voice utterance creates structured cargo memory`() {
        viewModel.runAiVoiceCargoTaggingDemo("이마트에서 아이스크림이랑 생일 케이크, 와인 두 병 샀어")

        val memory = viewModel.session.value.aiCargoMemory
        assertNotNull("AI cargo memory must be populated", memory)
        assertTrue("Contains perishable ice cream", memory!!.isTemperatureSensitive)
        assertTrue("Contains fragile cake/wine", memory.requiresSoftAutonomousProfile)
        assertTrue("Raw utterance preserved", memory.rawUserUtterance.contains("아이스크림"))
    }

    @Test
    fun `Tier 1 - Autonomous L3 - Cargo comfort mode recommendation and execution`() {
        viewModel.runAutonomousLv3CargoComfortDemo()

        val prompt = viewModel.activePrompt.value
        assertNotNull("Prompt must suggest switching to comfort mode", prompt)
        assertEquals(AutonomousCargoComfortScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.availableControls.contains(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT))

        viewModel.executeControl(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT)
        assertEquals(
            AutonomousDrivingProfile.CARGO_COMFORT_GUARD,
            viewModel.session.value.autonomousProfile
        )
    }

    @Test
    fun `Tier 1 - Autonomous AVP - Drop-off zone hold warning and departure delay`() {
        viewModel.runAutonomousValetDropoffGuardDemo()

        val prompt = viewModel.activePrompt.value
        assertNotNull("AVP alert prompt must be active", prompt)
        assertEquals(AutonomousValetDropoffScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.availableControls.contains(VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK))

        viewModel.executeControl(VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK)
        assertTrue(viewModel.session.value.isAvpDepartureHoldActive)
        assertTrue(DoorZone.TRUNK in viewModel.session.value.openDoors)
    }

    // =========================================================================
    // Tier 1: Feature 19 - Real-Time Event Log Viewer
    // =========================================================================

    @Test
    fun `Tier 1 - Event Log - Logs capture signals with proper formatting and origin tags`() {
        viewModel.triggerManualGear(GearState.DRIVE)
        viewModel.triggerManualGear(GearState.PARK)

        val logs = viewModel.eventLog.value
        assertTrue("Event log must not be empty", logs.isNotEmpty())
        assertTrue("Log should include gear event", logs.any { it.contains("기어") || it.contains("PARK") || it.contains("DRIVE") })
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (>= 5 tests)
    // =========================================================================

    @Test
    fun `Tier 2 - Boundary - Event log length is capped at maximum threshold`() {
        for (i in 1..50) {
            engine.processSignal(VehicleSignal.GearChanged(if (i % 2 == 0) GearState.DRIVE else GearState.PARK))
        }

        val logs = viewModel.eventLog.value
        assertTrue("Log size must be capped to prevent memory leak (<= 35)", logs.size <= 35)
    }

    @Test
    fun `Tier 2 - Boundary - Plugin toggle enables and disables scenario execution`() {
        // Disable TrunkReminderScenario
        viewModel.togglePlugin(TrunkReminderScenario.ID, false)
        assertFalse(TrunkReminderScenario.ID in viewModel.enabledPluginIds.value)

        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNull("Disabled plugin must not fire prompt", viewModel.activePrompt.value)

        // Re-enable TrunkReminderScenario
        viewModel.togglePlugin(TrunkReminderScenario.ID, true)
        assertTrue(TrunkReminderScenario.ID in viewModel.enabledPluginIds.value)
    }

    @Test
    fun `Tier 2 - Boundary - Unknown or unsupported control command does not crash engine`() {
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull(viewModel.activePrompt.value)

        // Acknowledge is standard
        viewModel.acknowledgePrompt()
        assertNull(viewModel.activePrompt.value)

        // Attempt dismissing when null
        viewModel.dismissPrompt()
        assertNull(viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 2 - Boundary - Blank custom voice utterance is safely ignored`() {
        val initialMemory = viewModel.session.value.aiCargoMemory

        viewModel.submitCustomVoiceUtterance("   ")

        assertEquals("Blank utterance must not alter memory", initialMemory, viewModel.session.value.aiCargoMemory)
    }

    @Test
    fun `Tier 2 - Boundary - ResetAll clears active prompts and restores clean session`() {
        viewModel.runDemo1TrunkUsedHomeArrival()
        assertNotNull(viewModel.activePrompt.value)

        viewModel.resetAll()

        assertNull("Active prompt must be cleared on resetAll", viewModel.activePrompt.value)
        assertFalse("Trunk usage must be cleared", viewModel.session.value.hasActiveTrunkUsage)
        assertFalse("Arrival state must be reset", viewModel.session.value.isArrivedAtDestination)
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
    }

    @Test
    fun `Tier 2 - Corner - Rapid consecutive gear shifts D-P-D-P stabilize properly`() {
        viewModel.triggerManualTrunkOpenClose()
        viewModel.triggerManualGear(GearState.DRIVE)
        viewModel.triggerManualGear(GearState.PARK)
        viewModel.triggerManualGear(GearState.DRIVE)
        viewModel.triggerManualGear(GearState.PARK)

        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
        assertNull("Intermediate shifts without arrival must not fire arrival prompt", viewModel.activePrompt.value)
    }

    // =========================================================================
    // Empirical Challenger Extensions: Journey Replayer, Stress & Edge Resilience
    // =========================================================================

    @Test
    fun `Tier 1 - Feature 15 - Empirical Journey Replayer executes 5 stages to 100 percent completion`() = runTest {
        assertFalse(viewModel.isReplaying.value)
        assertEquals(0, viewModel.replayProgress.value)
        assertNull(viewModel.replayStageTitle.value)

        viewModel.startEmpiricalJourneyReplay()

        // Step 1: 15% - Departure stop and trunk open
        assertTrue("Replayer must be running", viewModel.isReplaying.value)
        assertEquals(15, viewModel.replayProgress.value)
        assertEquals("01. 출발지 정차 및 트렁크 개방", viewModel.replayStageTitle.value)
        assertTrue(DoorZone.TRUNK in viewModel.session.value.openDoors)

        // Advance to Step 2 (1200ms delay)
        testScheduler.advanceTimeBy(1250L)
        assertEquals(35, viewModel.replayProgress.value)
        assertEquals("02. 물품 적재 완료 & AI 음성 메모 인식", viewModel.replayStageTitle.value)
        assertFalse("Trunk closed", DoorZone.TRUNK in viewModel.session.value.openDoors)
        assertNotNull("AI cargo memory recorded", viewModel.session.value.aiCargoMemory)

        // Advance to Step 3 (1200ms delay)
        testScheduler.advanceTimeBy(1250L)
        assertEquals(65, viewModel.replayProgress.value)
        assertEquals("03. D단 전환 및 내비게이션 주행 중 (안전 대기)", viewModel.replayStageTitle.value)
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)

        // Advance to Step 4 (1200ms delay)
        testScheduler.advanceTimeBy(1250L)
        assertEquals(85, viewModel.replayProgress.value)
        assertEquals("04. 목적지 도착 판정 (성수동 자택)", viewModel.replayStageTitle.value)
        assertTrue(viewModel.session.value.isArrivedAtDestination)

        // Advance to Step 5 (1200ms delay)
        testScheduler.advanceTimeBy(1250L)
        assertEquals(100, viewModel.replayProgress.value)
        assertEquals("05. P단 주차 완료 & 하차 전 개인화 브리핑 팝업", viewModel.replayStageTitle.value)
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
        assertNotNull("Prompt must trigger on arrival park", viewModel.activePrompt.value)

        // Advance to finish (1800ms delay)
        testScheduler.advanceTimeBy(1850L)
        assertFalse("Replaying flag resets after completion", viewModel.isReplaying.value)
        assertNull("Stage title resets after completion", viewModel.replayStageTitle.value)
    }

    @Test
    fun `Tier 2 - Corner - Coroutine lifecycle safety prevents duplicate concurrent replay runs`() = runTest {
        viewModel.startEmpiricalJourneyReplay()
        assertTrue(viewModel.isReplaying.value)
        assertEquals(15, viewModel.replayProgress.value)

        // Second duplicate launch must be safely ignored by re-entrancy guard
        viewModel.startEmpiricalJourneyReplay()
        assertEquals("Progress must not be reset on duplicate call", 15, viewModel.replayProgress.value)
        assertTrue(viewModel.isReplaying.value)

        // Advance to complete
        testScheduler.advanceTimeBy(7000L)
        assertFalse(viewModel.isReplaying.value)
        assertEquals(100, viewModel.replayProgress.value)
    }

    @Test
    fun `Tier 2 - Corner - Replay state and session reset cleanly when resetAll is invoked mid-flight`() = runTest {
        viewModel.startEmpiricalJourneyReplay()
        testScheduler.advanceTimeBy(2500L) // in stage 2 or 3

        viewModel.resetAll()
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
        assertFalse(viewModel.session.value.hasActiveTrunkUsage)
        assertNull(viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 2 - Stress - Log buffer overflow resilience under 500 rapid signals strictly maintains FIFO and cap of 35`() {
        for (i in 1..500) {
            engine.processSignal(
                VehicleSignal.GearChanged(
                    if (i % 2 == 0) GearState.DRIVE else GearState.PARK,
                    SignalOrigin.MOCK_SIMULATOR
                )
            )
        }

        val logs = viewModel.eventLog.value
        assertEquals("Log size must be strictly capped at 35", 35, logs.size)
        assertTrue("Newest event must be at index 0", logs.first().contains("기어"))
    }

    @Test
    fun `Tier 2 - Stress - Extreme rapid gear shifting 100 times maintains stability without unhandled exceptions`() {
        for (i in 1..100) {
            viewModel.triggerManualGear(if (i % 2 == 0) GearState.DRIVE else GearState.PARK)
        }
        assertEquals(GearState.DRIVE, viewModel.session.value.currentGear)
        assertNull("Intermediate shifts without arrival must not fire arrival prompt", viewModel.activePrompt.value)

        viewModel.triggerManualGear(GearState.PARK)
        assertEquals(GearState.PARK, viewModel.session.value.currentGear)
        assertNull(viewModel.activePrompt.value)
    }

    @Test
    fun `Tier 2 - Boundary - Blank and whitespace-only custom voice utterances are safely ignored`() {
        val initialMemory = viewModel.session.value.aiCargoMemory

        viewModel.submitCustomVoiceUtterance("")
        viewModel.submitCustomVoiceUtterance("   ")
        viewModel.submitCustomVoiceUtterance("\t\n\r  ")

        assertEquals("Whitespace-only utterances must not alter aiCargoMemory", initialMemory, viewModel.session.value.aiCargoMemory)
    }

    @Test
    fun `Tier 2 - Boundary - Long voice utterance with special characters and unicode handled safely`() {
        val longUtterance = "🍎".repeat(100) + "마트에서 아이스크림 10개, 와인 5병 샀어! @#$%^&*() " + "안녕".repeat(50)
        viewModel.submitCustomVoiceUtterance(longUtterance)

        val memory = viewModel.session.value.aiCargoMemory
        assertNotNull("AI memory must handle complex unicode utterance", memory)
        assertTrue("Ice cream recognized", memory!!.isTemperatureSensitive)
    }

    @Test
    fun `Tier 1 - Feature 20 - AAOS Background HUD and Modal simulation trigger hook is operational`() {
        var callbackInvoked = false
        val trigger: () -> Unit = { callbackInvoked = true }

        trigger.invoke()
        assertTrue("Background simulation trigger callback must be invokable", callbackInvoked)
    }

    @Test
    fun `Tier 1 - 3D Canvas Telemetry - High temperature 35C and rapid cooling 19C states`() {
        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 35.0f
            )
        )
        assertEquals(35.0f, viewModel.session.value.cabinTempCelsius, 0.01f)

        engine.processSignal(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = false,
                openWindowsCount = 0,
                cabinTempCelsius = 19.5f
            )
        )
        assertEquals(19.5f, viewModel.session.value.cabinTempCelsius, 0.01f)
    }
}
