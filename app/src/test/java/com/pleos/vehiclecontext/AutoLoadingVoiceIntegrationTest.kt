package com.pleos.vehiclecontext

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.CargoItemRecord
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.settings.AlertSensitivity
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.settings.ThemeMode
import com.pleos.vehiclecontext.core.settings.isShoppingContext
import com.pleos.vehiclecontext.core.voice.CargoVoiceCoordinator
import com.pleos.vehiclecontext.core.voice.CargoVoiceQuestion
import com.pleos.vehiclecontext.core.voice.GuidedVoiceChannel
import com.pleos.vehiclecontext.core.voice.determineMissingVoiceQuestions
import com.pleos.vehiclecontext.core.voice.extractQuantityFromUtterance
import com.pleos.vehiclecontext.core.voice.parseCombinedCargoUtterance
import com.pleos.vehiclecontext.core.voice.samePlaceName
import com.pleos.vehiclecontext.core.voice.spokenCargoName
import com.pleos.vehiclecontext.core.voice.spokenPlace
import com.pleos.vehiclecontext.core.voice.spokenPurpose
import com.pleos.vehiclecontext.core.voice.spokenQuantity
import com.pleos.vehiclecontext.gleo.GleoCargoActions
import com.pleos.vehiclecontext.scenarios.trunk.TrunkReminderScenario
import com.pleos.vehiclecontext.ui.model.UserScreenDataMapper
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLoadingVoiceIntegrationTest {

    @Test
    fun `trunk open then close triggers loading prompt and auto voice when enabled`() {
        var currentSettings = AppSettings(autoVoiceOnTrunkClose = true)
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher(),
            settingsProvider = { currentSettings }
        )

        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                origin = SignalOrigin.REAL_SDK
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                origin = SignalOrigin.REAL_SDK
            )
        )

        val session = engine.session.value
        assertNotNull(session.pendingLoadingPromptRequestId)
        assertEquals(session.pendingLoadingPromptRequestId, session.autoVoiceRegistrationId)
        assertTrue(engine.claimAutoVoiceRegistration(session.autoVoiceRegistrationId!!))
        assertFalse(engine.claimAutoVoiceRegistration(session.autoVoiceRegistrationId!!))

        // Starting to drive clears pending loading/auto-voice prompt
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.REAL_SDK))
        assertNull(engine.session.value.pendingLoadingPromptRequestId)
        assertNull(engine.session.value.autoVoiceRegistrationId)

        // When setting is disabled, trunk close only opens manual request without autoVoiceId
        currentSettings = currentSettings.copy(autoVoiceOnTrunkClose = false)
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.REAL_SDK))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, true, PoiCategory.HOME))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, false, PoiCategory.HOME))

        assertNotNull(engine.session.value.pendingLoadingPromptRequestId)
        assertNull(engine.session.value.autoVoiceRegistrationId)
    }

    @Test
    fun `structured cargo registration upgrades recent sensor placeholder and supports partial unload`() {
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher(),
            settingsProvider = { AppSettings(autoShoppingBundle = true) }
        )
        engine.processSignal(
            VehicleSignal.DestinationUpdated("성수동 자택", PoiCategory.HOME, SignalOrigin.REAL_SDK)
        )

        // Sensor open/close creates placeholder first
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, true, PoiCategory.MART_SHOPPING))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, false, PoiCategory.MART_SHOPPING))
        assertTrue(engine.session.value.cargoInventory.any { it.id.startsWith("trunk-sensor-") })

        // Structured voice registration replaces placeholder seamlessly
        val regResult = engine.registerStructuredCargo(
            name = "생수",
            quantity = 3,
            purpose = "가족 행사",
            targetDestinationName = "성수동 자택",
            countAsBundle = false
        )
        assertTrue(regResult.success)
        val inventory = engine.session.value.cargoInventory
        assertEquals(1, inventory.size)
        val item = inventory.first()
        assertEquals("생수", item.name)
        assertEquals(3, item.quantity)
        assertEquals("가족 행사", item.purpose)
        assertEquals("성수동 자택", item.targetDestinationName)
        assertNull(engine.session.value.pendingLoadingPromptRequestId)

        // Check UserScreenDataMapper displays structured metadata
        val uiList = UserScreenDataMapper.toCargoItemList(engine.session.value)
        assertEquals(1, uiList.size)
        assertTrue(uiList.first().name.contains("생수 (3개)"))
        assertTrue(uiList.first().locationNote.contains("성수동 자택"))
        assertTrue(uiList.first().locationNote.contains("가족 행사"))

        // Arriving and closing trunk triggers unload confirmation instead of adding dummy cargo
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("성수동 자택", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, true, PoiCategory.HOME))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, false, PoiCategory.HOME))

        assertTrue(engine.session.value.unloadConfirmationNeeded)
        assertEquals(1, engine.session.value.cargoInventory.size)

        // Partial unload 1 of 3
        val partial = engine.confirmUnloadCargo(targetName = "성수동 자택", quantity = 1)
        assertTrue(partial.success)
        assertEquals(2, engine.session.value.cargoInventory.first().remainingQuantity)
        assertFalse(engine.session.value.cargoInventory.first().isUnloaded)

        // Final unload 2 of 2
        val done = engine.confirmUnloadCargo(targetName = "성수동 자택", quantity = null)
        assertTrue(done.success)
        assertTrue(engine.session.value.cargoInventory.first().isUnloaded)
        assertFalse(engine.session.value.unloadConfirmationNeeded)
    }

    @Test
    fun `voice answer parsers extract korean cargo names quantities purposes and destinations`() {
        assertEquals("생일 케이크", spokenCargoName("생일 케이크를 실었어요."))
        assertEquals("캠핑", spokenPurpose("캠핑에 쓰려고 가져왔어요."))
        assertEquals("우리 집", spokenPlace("우리 집에서 내릴 거예요.", "강남 오피스"))
        assertEquals(3, spokenQuantity("세계"))
        assertEquals(12, spokenQuantity("열 두 개"))
        assertEquals(2, extractQuantityFromUtterance("와인 두 병 실었어"))
        assertEquals(5, extractQuantityFromUtterance("사과 5박스"))
        assertTrue(samePlaceName("성수동 자택", "성수동자택"))
        assertTrue(isShoppingContext("마트 장보기", "집"))
    }

    @Test
    fun `theme mode resolves day and night hours and settings defaults match expectations`() {
        assertFalse(ThemeMode.AUTO.isDark(10))
        assertTrue(ThemeMode.AUTO.isDark(21))
        assertTrue(ThemeMode.AUTO.isDark(5))
        assertFalse(ThemeMode.LIGHT.isDark(23))
        assertTrue(ThemeMode.DARK.isDark(12))

        val defaults = AppSettings()
        assertEquals(AlertSensitivity.PARK_STANDARD, defaults.alertSensitivity)
        assertTrue(defaults.autoVoiceOnTrunkClose)
        assertTrue(defaults.askPurposeOnVoice)
    }

    @Test
    fun `gleo actions stage and confirm registration move keep and unload on TripContextEngine`() {
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher()
        )
        var nowMs = 1_000L
        val gleo = GleoCargoActions(
            engine = engine,
            clockMs = { nowMs },
            activeNavigationDestination = { "강남 오피스" }
        )

        // Stage REGISTER
        val stageReg = gleo.handle(
            GleoCargoActions.REGISTER,
            JSONObject()
                .put("cargo_name", "음료 박스")
                .put("quantity", 2)
                .put("purpose", "회의 다과")
        )
        assertEquals("asking", stageReg.optString("status"))
        val regDraft = gleo.pending.value
        assertNotNull(regDraft)
        assertTrue(gleo.confirm(regDraft!!.id).success)
        assertNull(gleo.pending.value)

        val item = engine.session.value.cargoInventory.single()
        assertEquals("음료 박스", item.name)
        assertEquals(2, item.quantity)
        assertEquals("강남 오피스", item.targetDestinationName)

        // LIST matching query
        val listResp = gleo.handle(
            GleoCargoActions.LIST,
            JSONObject().put("query", "음료")
        )
        assertEquals("success", listResp.optString("status"))
        assertTrue(listResp.optString("data").contains("음료 박스"))

        // Stage UPDATE -> change_destination
        val stageMove = gleo.handle(
            GleoCargoActions.UPDATE,
            JSONObject()
                .put("operation", "change_destination")
                .put("cargo_id", item.id)
                .put("target_place", "판교 연구소")
        )
        assertEquals("asking", stageMove.optString("status"))
        assertTrue(gleo.confirm(gleo.pending.value!!.id).success)
        assertEquals("판교 연구소", engine.session.value.cargoInventory.single().targetDestinationName)

        // Expired draft cannot confirm
        gleo.handle(
            GleoCargoActions.UPDATE,
            JSONObject()
                .put("operation", "unload")
                .put("cargo_id", item.id)
                .put("quantity", 1)
        )
        nowMs += 130_000L
        assertFalse(gleo.confirm(gleo.pending.value!!.id).success)

        // Fresh unload confirm
        gleo.handle(
            GleoCargoActions.UPDATE,
            JSONObject()
                .put("operation", "unload")
                .put("cargo_id", item.id)
                .put("quantity", 2)
        )
        assertTrue(gleo.confirm(gleo.pending.value!!.id).success)
        assertTrue(engine.session.value.cargoInventory.single().isUnloaded)
    }

    @Test
    fun `voice cargo note extracts quantity into cargoInventory`() {
        val engine = TripContextEngine(plugins = listOf(TrunkReminderScenario()))
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "생일 케이크 2개 트렁크에 실었어",
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                origin = SignalOrigin.AI_AGENT_INFERRED
            )
        )
        val items = engine.session.value.cargoInventory
        assertTrue(items.any { it.name.contains("케이크") && it.quantity == 2 })
    }

    @Test
    fun `combined voice parser extracts item quantity and destination in one shot and isolates missing slots`() {
        val full = parseCombinedCargoUtterance("생일 케이크 2개 우리 집에서 내릴 거야", "성수동 자택")
        assertEquals("생일 케이크", full.itemName)
        assertEquals(2, full.quantity)
        assertEquals("우리 집", full.targetDestination)
        assertTrue(determineMissingVoiceQuestions(full, isAutoBundleItem = false).isEmpty())

        val withOrigin = parseCombinedCargoUtterance("생수 2묶음, 이마트에서 집으로", "성수동 자택")
        assertEquals("생수", withOrigin.itemName)
        assertEquals(2, withOrigin.quantity)
        assertTrue(withOrigin.isBundle)
        assertEquals("집", withOrigin.targetDestination)
        assertTrue(determineMissingVoiceQuestions(withOrigin, isAutoBundleItem = false).isEmpty())

        val missingTarget = parseCombinedCargoUtterance("와인 두 병", "성수동 자택")
        assertEquals("와인", missingTarget.itemName)
        assertEquals(2, missingTarget.quantity)
        assertNull(missingTarget.targetDestination)
        assertEquals(
            listOf(CargoVoiceQuestion.TARGET),
            determineMissingVoiceQuestions(missingTarget, isAutoBundleItem = false)
        )

        val missingQty = parseCombinedCargoUtterance("노트북 강남 오피스에서 내릴 거예요", "성수동 자택")
        assertEquals("노트북", missingQty.itemName)
        assertNull(missingQty.quantity)
        assertEquals("강남 오피스", missingQty.targetDestination)
        assertEquals(
            listOf(CargoVoiceQuestion.QUANTITY),
            determineMissingVoiceQuestions(missingQty, isAutoBundleItem = false)
        )

        val missingItem = parseCombinedCargoUtterance("세 개 우리 집으로", "성수동 자택")
        assertNull(missingItem.itemName)
        assertEquals(3, missingItem.quantity)
        assertEquals("우리 집", missingItem.targetDestination)
        assertEquals(
            listOf(CargoVoiceQuestion.NAME),
            determineMissingVoiceQuestions(missingItem, isAutoBundleItem = false)
        )
    }

    @Test
    fun `guided voice coordinator registers in one shot or re-asks only missing fields`() = runTest {
        class ScriptedChannel(
            private val replies: Map<CargoVoiceQuestion, String>,
            private val qtyTapReply: Int = 1
        ) : GuidedVoiceChannel {
            val askedQuestions = mutableListOf<CargoVoiceQuestion>()
            override suspend fun ask(
                question: CargoVoiceQuestion,
                isQuantity: Boolean,
                customPromptStatus: String?
            ): String {
                askedQuestions += question
                return replies[question].orEmpty()
            }

            override suspend fun askQuantityWithTapFallback(customPromptStatus: String?): Int {
                askedQuestions += CargoVoiceQuestion.QUANTITY
                return qtyTapReply
            }
        }

        // Case 1: All-in-one complete -> 0 follow-up questions
        val engine1 = TripContextEngine(plugins = listOf(TrunkReminderScenario()))
        val ch1 = ScriptedChannel(
            mapOf(CargoVoiceQuestion.ALL_IN_ONE to "생일 케이크 2개 우리 집에서 내릴 거야")
        )
        assertTrue(
            CargoVoiceCoordinator.executeGuidedVoiceRegistration(
                engine = engine1,
                settings = AppSettings(autoShoppingBundle = false),
                channel = ch1
            )
        )
        assertEquals(listOf(CargoVoiceQuestion.ALL_IN_ONE), ch1.askedQuestions)
        val item1 = engine1.session.value.cargoInventory.single()
        assertEquals("생일 케이크", item1.name)
        assertEquals(2, item1.quantity)
        assertEquals("우리 집", item1.targetDestinationName)

        // Case 2: Only item + quantity recognized -> re-asks ONLY TARGET
        val engine2 = TripContextEngine(plugins = listOf(TrunkReminderScenario()))
        val ch2 = ScriptedChannel(
            mapOf(
                CargoVoiceQuestion.ALL_IN_ONE to "와인 두 병 실었어요",
                CargoVoiceQuestion.TARGET to "강남 오피스"
            )
        )
        assertTrue(
            CargoVoiceCoordinator.executeGuidedVoiceRegistration(
                engine = engine2,
                settings = AppSettings(autoShoppingBundle = false),
                channel = ch2
            )
        )
        assertEquals(listOf(CargoVoiceQuestion.ALL_IN_ONE, CargoVoiceQuestion.TARGET), ch2.askedQuestions)
        val item2 = engine2.session.value.cargoInventory.single()
        assertEquals("와인", item2.name)
        assertEquals(2, item2.quantity)
        assertEquals("강남 오피스", item2.targetDestinationName)

        // Case 3: Item + destination recognized -> re-asks ONLY QUANTITY
        val engine3 = TripContextEngine(plugins = listOf(TrunkReminderScenario()))
        val ch3 = ScriptedChannel(
            mapOf(CargoVoiceQuestion.ALL_IN_ONE to "노트북 강남 오피스에서 내릴 거예요"),
            qtyTapReply = 3
        )
        assertTrue(
            CargoVoiceCoordinator.executeGuidedVoiceRegistration(
                engine = engine3,
                settings = AppSettings(autoShoppingBundle = false),
                channel = ch3
            )
        )
        assertEquals(listOf(CargoVoiceQuestion.ALL_IN_ONE, CargoVoiceQuestion.QUANTITY), ch3.askedQuestions)
        val item3 = engine3.session.value.cargoInventory.single()
        assertEquals("노트북", item3.name)
        assertEquals(3, item3.quantity)
        assertEquals("강남 오피스", item3.targetDestinationName)

        // Case 4: Quantity + destination recognized, item missed -> re-asks ONLY NAME
        val engine4 = TripContextEngine(plugins = listOf(TrunkReminderScenario()))
        val ch4 = ScriptedChannel(
            mapOf(
                CargoVoiceQuestion.ALL_IN_ONE to "세 개 우리 집으로",
                CargoVoiceQuestion.NAME to "딸기 케이크"
            )
        )
        assertTrue(
            CargoVoiceCoordinator.executeGuidedVoiceRegistration(
                engine = engine4,
                settings = AppSettings(autoShoppingBundle = false),
                channel = ch4
            )
        )
        assertEquals(listOf(CargoVoiceQuestion.ALL_IN_ONE, CargoVoiceQuestion.NAME), ch4.askedQuestions)
        val item4 = engine4.session.value.cargoInventory.single()
        assertEquals("딸기 케이크", item4.name)
        assertEquals(3, item4.quantity)
        assertEquals("우리 집", item4.targetDestinationName)
    }
}
