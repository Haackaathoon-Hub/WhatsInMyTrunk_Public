package com.pleos.vehiclecontext.scenarios.cabin

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [확장 시나리오 / Task 2-2] 고온 적재물 변질 위험 경고 플러그인
 *
 * 트렁크에 온도 민감 물품(신선/냉동식품 등)이 적재된 상태에서
 * 차량 실내외 온도가 높을 때(26℃ 이상) 경유지 정차 또는 목적지 도착 전 P단 정차 시
 * 신선식품 변질 방지를 위한 주의 알림을 제공합니다.
 */
class CargoTemperatureGuardScenario : ScenarioPlugin {
    override val id: String = ID
    override val name: String = "적재물 고온 변질 위험 경고"
    override val description: String = "고온 환경에서 신선식품 적재 상태로 경유지 또는 정차 시 변질 주의 안내"
    override val priority: Int = 20
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.CABIN

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        if (session.cabinTempCelsius < 26.0f) return null

        val isTemperatureSensitiveCargo = session.aiCargoMemory?.isTemperatureSensitive == true ||
            session.latestTrunkUsage?.cargoTag == CargoTag.FRESH_FOOD ||
            session.cargoInventory.any { it.isPerishable && !it.isUnloaded }
        if (!isTemperatureSensitiveCargo) return null

        val isIntermediateOrParkBeforeArrival = session.phase == TripPhase.INTERMEDIATE_STOP ||
            (session.currentGear == GearState.PARK && !session.isArrivedAtDestination)
        if (!isIntermediateOrParkBeforeArrival) return null

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "CARGO TEMPERATURE GUARD",
            headline = "적재물 고온 변질 위험 경고",
            bodyMessage = "현재 실내 온도가 ${session.cabinTempCelsius}℃로 높습니다. 트렁크에 보관 중인 신선/냉동 식품이 변질되지 않도록 주의하세요.",
            contextReasonBadge = "고온 주의(${session.cabinTempCelsius}℃) + 신선식품 적재",
            signalOrigin = SignalOrigin.REAL_SDK,
            voiceTtsMessage = "차량 실내 온도가 높습니다. 트렁크의 신선식품 변질에 유의해 주세요.",
            primaryAckLabel = "확인했습니다",
            availableControls = listOf(VehicleControlCommand.ACTIVATE_CABIN_COOLING, VehicleControlCommand.OPEN_TRUNK),
            isDrivingSafeVoiceOnly = true
        )
    }

    companion object {
        const val ID = "cargo_temperature_guard"
    }
}
