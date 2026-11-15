package com.pleos.vehiclecontext

import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.location.PlaceSnapshot
import com.pleos.vehiclecontext.core.location.VehicleLocationResolver
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.scenarios.trunk.TrunkReminderScenario
import com.pleos.vehiclecontext.ui.model.UserScreenDataMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CargoRegistrationAndLocationTest {

    @Test
    fun `resolveRouteContext prioritizes active navi destination and navi origin over current gps`() {
        val naviOrigin = PlaceSnapshot(
            name = "강남역 사거리",
            address = "서울 강남구 강남대로 396",
            latitude = 37.4979,
            longitude = 127.0276,
            sourceLabel = "내비 출발지"
        )
        val currentGps = PlaceSnapshot(
            name = "서초동 1318",
            address = "서울 서초구 서초동 1318",
            latitude = 37.4950,
            longitude = 127.0260,
            sourceLabel = "GPS(gps)"
        )
        val naviDest = PlaceSnapshot(
            name = "성수동 자택",
            address = "서울 성동구 뚝섬로 379",
            latitude = 37.5398,
            longitude = 127.0532,
            poiCategory = PoiCategory.HOME,
            sourceLabel = "내비 목적지"
        )

        val resolved = VehicleLocationResolver.resolveRouteContext(
            naviOrigin = naviOrigin,
            currentGpsPlace = currentGps,
            activeDestination = naviDest
        )

        assertEquals("강남역 사거리", resolved.originName)
        assertEquals("내비 출발지", resolved.originSourceLabel)
        assertEquals("성수동 자택", resolved.destinationName)
        assertTrue(resolved.destinationCandidates.any { it.name == "성수동 자택" })
    }

    @Test
    fun `resolveRouteContext uses current gps enriched by nearby bookmark and recommends mart preset`() {
        val bookmarkMart = PlaceSnapshot(
            name = "이마트 성수점",
            address = "서울 성동구 뚝섬로 379",
            latitude = 37.53985,
            longitude = 127.05325,
            poiCategory = PoiCategory.MART_SHOPPING,
            sourceLabel = "지도 즐겨찾기"
        )
        val currentGps = PlaceSnapshot(
            name = "성동구 성수동2가",
            address = "서울 성동구 성수동2가 333-16",
            latitude = 37.53990,
            longitude = 127.05330,
            sourceLabel = "GPS(gps)"
        )

        val resolved = VehicleLocationResolver.resolveRouteContext(
            naviOrigin = null,
            currentGpsPlace = currentGps,
            activeDestination = null,
            bookmarks = listOf(bookmarkMart),
            fallbackDestinationName = "집 (성수동 자택)"
        )

        assertEquals("이마트 성수점", resolved.originName)
        assertEquals(PoiCategory.MART_SHOPPING, resolved.originPoi)
        assertTrue(resolved.originSourceLabel.contains("지도 장소 매칭"))
        assertEquals("집 (성수동 자택)", resolved.destinationName)
        assertEquals(VehicleLocationResolver.PRESET_MART, resolved.suggestedPresetKey)
    }

    @Test
    fun `trunk close sets auto prompt keeps gps and navi context and registers structured cargo`() {
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher(),
            settingsProvider = { AppSettings(autoVoiceOnTrunkClose = false) }
        )

        // 1. GPS + 내비 컨텍스트 수신
        engine.processSignal(
            VehicleSignal.LocationContextUpdated(
                originName = "이마트 성수점",
                originAddress = "서울 성동구 뚝섬로 379",
                originPoi = PoiCategory.MART_SHOPPING,
                originSourceLabel = "GPS · 지도 장소 매칭",
                destinationName = "분당 부모님댁",
                originSuggestions = listOf("이마트 성수점", "회사"),
                destinationSuggestions = listOf("분당 부모님댁", "집 (성수동 자택)"),
                suggestedPresetKey = VehicleLocationResolver.PRESET_MART
            )
        )

        // 2. 주차 중 트렁크 열림 -> 닫힘 감지 시 팝업 자동 트리거 식별자 활성화
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD,
                origin = SignalOrigin.REAL_SDK,
                timestampMs = 10_000L
            )
        )
        engine.processSignal(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = PoiCategory.MART_SHOPPING,
                cargoTag = CargoTag.FRESH_FOOD,
                origin = SignalOrigin.REAL_SDK,
                timestampMs = 11_000L
            )
        )

        val sessionAfterClose = engine.session.value
        assertNotNull(sessionAfterClose.pendingLoadingPromptRequestId)
        assertTrue(sessionAfterClose.pendingLoadingPromptRequestId!!.startsWith("load-"))
        assertEquals("이마트 성수점", sessionAfterClose.originName)
        assertEquals("분당 부모님댁", sessionAfterClose.destinationName)

        // 3. 주차 PRE_TRIP 중 내비 목적지 갱신 신호가 와도 열려있는 등록 팝업은 유지
        engine.processSignal(
            VehicleSignal.DestinationUpdated(
                destinationName = "집 (서초 푸르지오)",
                destinationPoi = PoiCategory.HOME,
                timestampMs = 12_000L
            )
        )
        assertNotNull(engine.session.value.pendingLoadingPromptRequestId)
        assertEquals("집 (서초 푸르지오)", engine.session.value.destinationName)

        // 4. 팝업에서 출발지/도착지 및 품목 확정 등록
        val result = engine.registerStructuredCargo(
            name = "생수 & 신선식품",
            quantity = 2,
            purpose = "저녁 장보기",
            originName = "이마트 성수점",
            targetDestinationName = "집 (서초 푸르지오)",
            countAsBundle = true,
            isPerishableOverride = true
        )
        assertTrue(result.success)
        assertNull(engine.session.value.pendingLoadingPromptRequestId)

        val items = UserScreenDataMapper.toCargoItemList(engine.session.value)
        assertEquals(1, items.size)
        assertTrue(items.first().locationNote.contains("이마트 성수점 → 집 (서초 푸르지오)"))
    }

    @Test
    fun `manual registration request opens popup and skip clears it`() {
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher(),
            settingsProvider = { AppSettings(autoVoiceOnTrunkClose = true) }
        )

        assertNull(engine.session.value.pendingLoadingPromptRequestId)
        engine.requestManualCargoRegistration()
        val reqId = engine.session.value.pendingLoadingPromptRequestId
        assertNotNull(reqId)
        assertTrue(reqId!!.startsWith("manual-"))
        // 직접 추가 버튼은 음성을 강제 자동 시작하지 않음
        assertNull(engine.session.value.autoVoiceRegistrationId)

        engine.skipLoadingRegistration()
        assertNull(engine.session.value.pendingLoadingPromptRequestId)
        assertFalse(engine.session.value.unloadConfirmationNeeded)
    }

    @Test
    fun `editCargoItem and removeCargoItem update structured UI items cleanly without phantom fallbacks`() {
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher(),
            settingsProvider = { AppSettings(autoVoiceOnTrunkClose = false) }
        )

        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, true, PoiCategory.MART_SHOPPING))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, false, PoiCategory.MART_SHOPPING))
        val regResult = engine.registerStructuredCargo(
            name = "생수",
            quantity = 2,
            purpose = "가족 행사",
            originName = "이마트 성수점",
            targetDestinationName = "집 (서초 푸르지오)",
            countAsBundle = false,
            isPerishableOverride = false
        )
        assertTrue(regResult.success)
        val itemId = regResult.itemId!!

        // Edit existing item in-place
        val editResult = engine.editCargoItem(
            itemId = itemId,
            name = "딸기 케이크",
            quantity = 1,
            purpose = "파손주의",
            originName = "성수동 베이커리",
            targetDestinationName = "부모님댁",
            countAsBundle = false,
            isPerishableOverride = true,
            isPermanent = false
        )
        assertTrue(editResult.success)
        assertEquals(1, engine.session.value.cargoInventory.size)

        val editedPresentation = UserScreenDataMapper.toCargoItemList(engine.session.value)
        assertEquals(1, editedPresentation.size)
        assertEquals("딸기 케이크", editedPresentation.first().itemTitle)
        assertEquals("1개", editedPresentation.first().quantityLabel)
        assertEquals("파손주의", editedPresentation.first().categoryBadge)
        assertEquals("부모님댁", editedPresentation.first().destinationLabel)
        assertEquals("파손주의", editedPresentation.first().purposeLabel)
        assertEquals("성수동 베이커리", editedPresentation.first().originLabel)

        // Delete item and verify phantom closure fallback does NOT resurrect an item
        val removeResult = engine.removeCargoItem(itemId)
        assertTrue(removeResult.success)
        assertTrue(engine.session.value.cargoInventory.isEmpty())
        assertTrue(UserScreenDataMapper.toCargoItemList(engine.session.value).isEmpty())
    }

    @Test
    fun `confirming or dismissing prompt or promoting loading alert prevents duplicate popups`() {
        val engine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            actionDispatcher = ActionDispatcher(),
            settingsProvider = { AppSettings(autoVoiceOnTrunkClose = false) }
        )

        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, true, PoiCategory.MART_SHOPPING))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, false, PoiCategory.MART_SHOPPING))
        assertTrue(engine.session.value.pendingLoadingPromptRequestId!!.startsWith("load-"))

        // Overlay '품목 직접 추가' click promotes load-* to manual-* so GeneralUserScreen skips re-showing alert
        engine.promoteLoadingAlertToManualForm()
        assertTrue(engine.session.value.pendingLoadingPromptRequestId!!.startsWith("manual-"))

        val regResult = engine.registerStructuredCargo(
            name = "장바구니",
            quantity = 1,
            purpose = "장보기",
            targetDestinationName = "집 (성수동 자택)"
        )
        assertTrue(regResult.success)
        assertNull(engine.session.value.pendingLoadingPromptRequestId)

        // Arrive & Park -> PreExit prompt triggers once
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertNotNull(engine.activePrompt.value)

        // Dismiss once -> recorded in acknowledgedScenarioIds and stays null
        engine.dismissActivePrompt()
        assertNull(engine.activePrompt.value)
        assertTrue("trunk_reminder_mvp" in engine.session.value.acknowledgedScenarioIds)

        // Re-emitting signal during same arrival does NOT re-open popup
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        assertNull(engine.activePrompt.value)
    }
}
