package com.pleos.vehiclecontext

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
import com.pleos.vehiclecontext.core.model.TripTimelineStep
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousCargoComfortScenario
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousValetDropoffScenario
import com.pleos.vehiclecontext.scenarios.cabin.CargoTemperatureGuardScenario
import com.pleos.vehiclecontext.scenarios.cabin.RearSeatItemScenario
import com.pleos.vehiclecontext.scenarios.driving.CargoSafeDrivingScenario
import com.pleos.vehiclecontext.scenarios.exit.ExitIntegrityScenario
import com.pleos.vehiclecontext.scenarios.trunk.DestinationReverseReminderScenario
import com.pleos.vehiclecontext.scenarios.trunk.TrunkReminderScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 리더 기획안 슬라이드 7의 6가지 첫 시연 핵심 동작 + 자율주행(AD/AVP) 및 AI 에이전트 연동을 검증하는 단위 테스트
 */
class TripContextEngineTest {

    private lateinit var persistence: InMemorySessionPersistence
    private lateinit var engine: TripContextEngine

    @Before
    fun setUp() {
        persistence = InMemorySessionPersistence()
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
            actionDispatcher = ActionDispatcher(),
            aiService = PleosAiContextAgent()
        )
    }

    @Test
    fun `Case 1 - 트렁크 사용 후 귀가 시 도착과 P단 확인 후 안내 1회 발동`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        assertNull("수동 주행 중에는 하차 안내 팝업이 뜨지 않아야 함", engine.activePrompt.value)

        engine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("목적지 도착 + P단 진입 시 트렁크 하차 전 안내가 발동해야 함", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
    }

    @Test
    fun `Case 2 - 트렁크 사용 없는 귀가 시 같은 목적지에서도 안내 생략`() {
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        assertNull("트렁크 개폐 이력이 없으면 도착+P단에서도 안내가 생략되어야 함", engine.activePrompt.value)
    }

    @Test
    fun `Case 3 - 중간 정차 시 목적지에 도착하기 전 안내 생략`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        engine.processSignal(VehicleSignal.DestinationArrived("잠시 정차", isFinalDestination = false))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        assertNull("최종 목적지 도착 전 중간 정차(P단)에서는 안내가 생략되어야 함", engine.activePrompt.value)
    }

    @Test
    fun `Case 4 - 반복 P 전환 시 같은 도착에서 재안내 방지`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        assertNotNull("첫 P단 진입 시 1회 안내 발동", engine.activePrompt.value)
        engine.acknowledgeActivePrompt()
        assertNull(engine.activePrompt.value)

        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        assertNull("같은 도착 세션에서 반복 P 전환 시 재안내가 발생하지 않아야 함", engine.activePrompt.value)
    }

    @Test
    fun `Case 5 - 경로 변경 시 현재 목적지 정보로 갱신 및 새 목적지 도착 시 안내`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        engine.processSignal(VehicleSignal.DestinationUpdated("분당 본가", PoiCategory.HOME))
        assertEquals("분당 본가", engine.session.value.destinationName)
        assertNull(engine.activePrompt.value)

        engine.processSignal(VehicleSignal.DestinationArrived("분당 본가", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull(prompt)
        assertTrue(prompt!!.bodyMessage.contains("분당 본가"))
    }

    @Test
    fun `Case 6 - 앱 재시작 시 현재 이동의 개폐 이력 및 안내 상태 복원`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        val restartedEngine = TripContextEngine(
            plugins = listOf(TrunkReminderScenario()),
            persistence = persistence
        )

        assertTrue("앱 재시작 후에도 이번 이동의 트렁크 사용 이력이 유지되어야 함", restartedEngine.session.value.hasActiveTrunkUsage)

        restartedEngine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        restartedEngine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertNotNull(restartedEngine.activePrompt.value)
    }

    @Test
    fun `Case 7 (AI x 자율주행) - 자연어 음성 적재 메모 분석 및 Lv3 자율주행 Comfort 모드 전환 제어`() {
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "이마트에서 아이스크림이랑 생일 케이크, 와인 두 병 샀어",
                currentLocationPoi = PoiCategory.MART_SHOPPING
            )
        )
        val memory = engine.session.value.aiCargoMemory
        assertNotNull(memory)
        assertTrue(memory!!.isTemperatureSensitive)
        assertTrue(memory.requiresSoftAutonomousProfile)

        // 주행 중 Lv.3 자율주행 활성화 -> 적재물 보호 Comfort 모드 전환 제안 발동
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.HIGHWAY_PILOT_L3))

        val adPrompt = engine.activePrompt.value
        assertNotNull(adPrompt)
        assertEquals(AutonomousCargoComfortScenario.ID, adPrompt?.scenarioId)

        // 제어 버튼 실행 시 자율주행 프로파일이 CARGO_COMFORT_GUARD로 변경됨
        engine.executePromptControl(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT)
        assertEquals(AutonomousDrivingProfile.CARGO_COMFORT_GUARD, engine.session.value.autonomousProfile)
    }

    @Test
    fun `Case 8 (자율 발렛 파킹 AVP) - 하차 존 Drop-off 진입 시 차량 출발 전 짐 하차 경고 및 홀드 제어`() {
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))
        engine.processSignal(VehicleSignal.AutonomousModeChanged(AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF))

        val avpPrompt = engine.activePrompt.value
        assertNotNull(avpPrompt)
        assertEquals(AutonomousValetDropoffScenario.ID, avpPrompt?.scenarioId)

        engine.executePromptControl(VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK)
        assertTrue(engine.session.value.isAvpDepartureHoldActive)
        assertTrue(DoorZone.TRUNK in engine.session.value.openDoors)
    }

    @Test
    fun `Case 9 - 도착지 기반 상시 보관 물품 역제안 플러그인(DestinationReverseReminderScenario) 검증`() {
        engine.togglePermanentTrunkNote("장바구니")
        assertTrue("장바구니가 상시 보관에 등록되어야 함", "장바구니" in engine.session.value.permanentTrunkNotes)

        // 마트로 목적지 변경 및 이동
        engine.processSignal(VehicleSignal.DestinationUpdated("이마트 역삼점", PoiCategory.MART_SHOPPING))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        assertNull(engine.activePrompt.value)

        // 마트 도착 및 P단 주차
        engine.processSignal(VehicleSignal.DestinationArrived("이마트 역삼점", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("상시 보관 품목이 있고 마트에 도착하여 P단일 때 역제안 리마인더가 발동해야 함", prompt)
        assertEquals(DestinationReverseReminderScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("장바구니"))
    }

    @Test
    fun `Case 10 - 고온 신선식품 변질 위험 경고 플러그인(CargoTemperatureGuardScenario) 검증`() {
        // 트렁크에 신선식품 적재
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // 실내외 온도가 28도(26도 이상)로 상승
        engine.processSignal(VehicleSignal.CabinStatusUpdated(rearSeatOccupied = false, openWindowsCount = 0, cabinTempCelsius = 28.0f))

        // 최종 목적지 도착 전 중간 정차(P단)
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("고온(28도)에서 신선식품 적재 상태로 경유 정차 시 변질 경고가 발동해야 함", prompt)
        assertEquals(CargoTemperatureGuardScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.bodyMessage.contains("28.0℃") || prompt.bodyMessage.contains("28"))
    }

    @Test
    fun `Case 11 - 고온 변질 위험 경고 발생 시 실내 급속 냉방 제어(ACTIVATE_CABIN_COOLING) 실행 검증`() {
        // 트렁크에 신선식품 적재 (PoiCategory.MART_SHOPPING, CargoTag.FRESH_FOOD)
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))

        // D단 주행 전환
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // 실내 온도 28.0f 상승 (CabinStatusUpdated)
        engine.processSignal(VehicleSignal.CabinStatusUpdated(rearSeatOccupied = false, openWindowsCount = 0, cabinTempCelsius = 28.0f))

        // 중간 정차 (P단 전환)
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        // prompt가 CargoTemperatureGuardScenario.ID로 발동하는지 검증
        val prompt = engine.activePrompt.value
        assertNotNull("고온(28도)에서 신선식품 적재 상태로 경유 정차 시 변질 경고가 발동해야 함", prompt)
        assertEquals(CargoTemperatureGuardScenario.ID, prompt?.scenarioId)
        assertTrue(prompt!!.availableControls.contains(VehicleControlCommand.ACTIVATE_CABIN_COOLING))

        // engine.executePromptControl(VehicleControlCommand.ACTIVATE_CABIN_COOLING) 실행
        engine.executePromptControl(VehicleControlCommand.ACTIVATE_CABIN_COOLING)

        // engine.session.value.cabinTempCelsius가 20.0f로 설정되었는지 검증
        assertEquals(20.0f, engine.session.value.cabinTempCelsius, 0.01f)

        // activePrompt가 null로 닫혔는지 검증
        assertNull("급속 냉방 제어 실행 후 활성 프롬프트가 닫혀야 함", engine.activePrompt.value)
    }

    @Test
    fun `Case 12 - 이동 중 트렁크 적재부터 도착까지 구조화된 타임라인(timelineSteps) 기록 검증`() {
        // 트렁크 적재 및 닫힘
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))

        // D단 주행
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // 목적지 도착 및 P단 주차
        engine.processSignal(VehicleSignal.DestinationArrived("집", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        // engine.session.value.timelineSteps 검증
        val steps = engine.session.value.timelineSteps
        assertTrue("트렁크 적재부터 도착까지 타임라인 스텝이 비어있지 않아야 함", steps.isNotEmpty())

        // 적재/주행/도착 스텝들이 순서대로 기록되었는지 검증
        val loadStepIndex = steps.indexOfFirst {
            it.title.contains("적재") || it.title.contains("트렁크") || it.detail.contains("트렁크") || it.phaseLabel.contains("01")
        }
        val driveStepIndex = steps.indexOfFirst {
            it.title.contains("주행") || it.title.contains("출발") || it.detail.contains("주행") || it.phaseLabel.contains("02")
        }
        val arriveStepIndex = steps.indexOfFirst {
            it.title.contains("도착") || it.title.contains("주차") || it.title.contains("안내") || it.detail.contains("도착") || it.phaseLabel.contains("03")
        }

        assertTrue("적재 관련 스텝이 기록되어야 함", loadStepIndex != -1)
        assertTrue("주행 관련 스텝이 기록되어야 함", driveStepIndex != -1)
        assertTrue("도착 관련 스텝이 기록되어야 함", arriveStepIndex != -1)
        assertTrue("적재 -> 주행 -> 도착 스텝 순서대로 타임라인에 기록되어야 함", loadStepIndex < driveStepIndex && driveStepIndex < arriveStepIndex)
    }

    @Test
    fun `Case 13 - 물건 목적지 B와 인간 목적지 C 불일치 시 중간 정차 침묵 및 최종 목적지 알림 검증`() {
        // 위치 A(마트)에서 최종 목적지 B(성수동 자택)로 가는 신선식품 적재
        engine.resetTripSession("집 (성수동 자택)")
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))

        // 인간의 목적지 C(학원/주유소)에서 잠시 정차하여 P단 체결 (최종 목적지 B에 도착하지 않은 중간 정차)
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertNull("화물의 목적지 B(성수동 자택)와 다른 중간 정차 C(학원 등)에서는 트렁크 알림이 침묵해야 함", engine.activePrompt.value)

        // 다시 주행 재개 후 최종 목적지 B(집) 도착
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("화물의 최종 목적지에 도달하여 P단 체결 시 정상적으로 하차 알림이 발동해야 함", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
    }

    @Test
    fun `Case 14 - 복수 화물 누적 인벤토리(cargoInventory) 및 트렁크 개방 하차 완료 연동 검증`() {
        engine.resetTripSession("집 (성수동 자택)")

        // 1. 센서 기반 첫 번째 화물 적재 (마트 신선식품)
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false, currentLocationPoi = PoiCategory.MART_SHOPPING, cargoTag = CargoTag.FRESH_FOOD))

        // 2. AI 음성 기반 두 번째 화물 누적 적재 (생일 케이크)
        engine.processSignal(
            VehicleSignal.VoiceCargoNoteReceived(
                utterance = "파리바게뜨에서 생일 케이크랑 와인 실었어",
                currentLocationPoi = PoiCategory.MART_SHOPPING
            )
        )

        val inventory = engine.session.value.cargoInventory
        assertTrue("인벤토리에 복수의 화물 아이템이 누적되어야 함", inventory.isNotEmpty())
        assertTrue("케이크나 와인 관련 아이템이 인벤토리에 포함되어야 함", inventory.any { it.name.contains("케이크") || it.name.contains("와인") })

        // 3. 목적지 도착 및 하차 안내 발동
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull(prompt)

        // 4. 트렁크 열기 제어 실행 -> 화물 하차 완료(isUnloaded = true) 처리
        engine.executePromptControl(VehicleControlCommand.OPEN_TRUNK)
        assertTrue("트렁크가 열려 있어야 함", DoorZone.TRUNK in engine.session.value.openDoors)
        assertTrue(
            "트렁크 개방으로 해당 목적지의 화물들이 하차 완료(isUnloaded) 상태로 전이되어야 함",
            engine.session.value.cargoInventory.all { it.isUnloaded || it.isPermanent }
        )
    }

    @Test
    fun `Case 15 - 내비 목적지 미설정 시에도 출발 POI(마트) 기반 목적지 B(자택) 자동 추론 및 하차 알림 검증`() {
        // 내비게이션 목적지 미설정 상태에서 마트에서 짐 적재
        engine.resetTripSession("목적지 미설정")
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

        // 적재 인벤토리에서 목적지 B가 HOME으로 자동 추론되었는지 검증
        val cargoItem = engine.session.value.cargoInventory.last()
        assertEquals(PoiCategory.HOME, cargoItem.inferredDestinationPoi)
        assertTrue("추론 신뢰도가 0.8 이상이어야 함", cargoItem.destinationConfidence >= 0.8f)

        // 주행 시작 후 귀가(HOME) 도착
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationUpdated("성수동 자택", PoiCategory.HOME))
        engine.processSignal(VehicleSignal.DestinationArrived("성수동 자택", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull("자동 추론된 목적지(자택) 도착 시 정상적으로 하차 알림이 발동해야 함", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
    }

    @Test
    fun `Case 16 - 로컬 개인화 패턴(장보기 루틴) 학습 및 맞춤형 브리핑 생성 검증`() {
        engine.resetTripSession("집 (성수동 자택)")

        // 1. 마트에서 짐 적재 -> 개인화 루틴('정기 마트 장보기 ➔ 귀가 루틴') 자동 매칭
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

        assertEquals("정기 마트 장보기 ➔ 귀가 루틴", engine.session.value.matchedRoutineTitle)

        // 2. 주행 후 도착
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.DestinationArrived("집 (성수동 자택)", isFinalDestination = true))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        val prompt = engine.activePrompt.value
        assertNotNull(prompt)
        assertTrue("개인화 맥락이 바디 메시지에 포함되어야 함", prompt!!.bodyMessage.contains("개인화"))
        assertTrue("헤드라인에 장보기 귀가 패턴이 반영되어야 함", prompt.headline.contains("장보기") || prompt.contextReasonBadge.contains("개인화"))

        // 3. 하차 시 루틴 학습 카운트 증가 확인
        val beforeCount = engine.session.value.personalizedProfile.routines.first { it.routineId == "routine_mart_home" }.occurrenceCount
        engine.executePromptControl(VehicleControlCommand.OPEN_TRUNK)
        val afterCount = engine.session.value.personalizedProfile.routines.first { it.routineId == "routine_mart_home" }.occurrenceCount
        assertEquals("하차 완료 시 개인화 루틴 관측 횟수가 1 증가해야 함", beforeCount + 1, afterCount)
    }

    @Test
    fun `Case 17 - 실제 차량 에뮬레이터 주행 시퀀스(적재 후 D-N-D-R-P 후진 주차) 시 도착 판정 및 하차 안내 발동`() {
        engine.resetTripSession("집 (성수동 자택)")
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))

        // D 주행 -> N 신호 대기 -> D 재주행 -> R 후진 주차
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.NEUTRAL))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.REVERSE))
        assertNull("후진 중에는 팝업이 뜨지 않아야 함", engine.activePrompt.value)

        // R -> P 전환 시 최종 주차 완료로 간주하여 하차 안내 1회 발동
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        val prompt = engine.activePrompt.value
        assertNotNull("후진 주차(R->P) 완료 시 하차 전 안내가 발동해야 함", prompt)
        assertEquals(TrunkReminderScenario.ID, prompt?.scenarioId)
    }

    @Test
    fun `Case 18 - 출발 직후 후진(P-R-D) 후 주행 중 단순 정차(D-P) 시에는 오알림이 뜨지 않아야 함`() {
        engine.resetTripSession("집 (성수동 자택)")
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = true))
        engine.processSignal(VehicleSignal.DoorStateChanged(DoorZone.TRUNK, isOpen = false))

        // 주차장에서 빠져나오기 위한 P -> R -> D 후 신호대기 N -> D -> 중간정차 P
        engine.processSignal(VehicleSignal.GearChanged(GearState.REVERSE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.NEUTRAL))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))

        assertNull("도착 신호나 최종 R->P 없이 D->P로 정차하면 중간 정차로 간주해야 함", engine.activePrompt.value)
        assertEquals(com.pleos.vehiclecontext.core.model.TripPhase.INTERMEDIATE_STOP, engine.session.value.phase)
    }

    @Test
    fun `Case 19 - CLOSE_TRUNK 명령은 트렁크 점유 상태를 즉시 해제하고 두 번째 D-N-D-R-P 시퀀스도 재안내한다`() {
        engine.resetTripSession("집 (성수동 자택)")
        engine.registerStructuredCargo(
            name = "생일 케이크",
            quantity = 1,
            targetDestinationName = "집 (성수동 자택)"
        )

        // 1차 D-N-D-R-P -> 알림 발동 -> 트렁크 열기 -> 트렁크 닫기
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.NEUTRAL))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.REVERSE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertNotNull("1차 D-N-D-R-P 시 알림 발동", engine.activePrompt.value)

        // 확인만 하고 트렁크를 열었다가 닫아도 앱이 트렁크를 계속 잡고 있지 않아야 함
        engine.executePromptControl(VehicleControlCommand.OPEN_TRUNK)
        assertTrue(DoorZone.TRUNK in engine.session.value.openDoors)
        engine.executePromptControl(VehicleControlCommand.CLOSE_TRUNK)
        assertTrue("CLOSE_TRUNK 후 openDoors에서 TRUNK가 해제되어야 함", DoorZone.TRUNK !in engine.session.value.openDoors)

        // 새 화물 등록 후 다시 D-N-D-R-P 기동 시 2차 알림이 정상 발동해야 함
        engine.registerStructuredCargo(
            name = "신선식품 묶음",
            quantity = 1,
            targetDestinationName = "집 (성수동 자택)"
        )
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.NEUTRAL))
        engine.processSignal(VehicleSignal.GearChanged(GearState.DRIVE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.REVERSE))
        engine.processSignal(VehicleSignal.GearChanged(GearState.PARK))
        assertNotNull("재출발 후 2차 D-N-D-R-P 완료 시에도 하차 알림이 발동해야 함", engine.activePrompt.value)
    }
}
