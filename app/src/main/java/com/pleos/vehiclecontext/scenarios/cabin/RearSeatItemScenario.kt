package com.pleos.vehiclecontext.scenarios.cabin

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [확장 시나리오 1] 2열 도어 개폐 + 시트 미착석 맥락 기반 뒷좌석 소지품 하차 안내
 *
 * 트렁크와 동일한 컨텍스트 엔진을 활용하여,
 * "출발 전 2열 도어를 열었다 닫았으나 주행 중 2열 탑승객이 없었던 경우"를
 * 가방/코트/세탁물 등을 뒷좌석에 실은 것으로 추론하여 하차 전 안내합니다.
 */
class RearSeatItemScenario : ScenarioPlugin {
    override val id: String = "rear_seat_item_care"
    override val name: String = "2열 뒷좌석 소지품 하차 안내 (공간 확장)"
    override val description: String = "2열 도어 개폐 이력 + 2열 미착석(짐 적재 추론) + 도착·P단 결합 시 뒷좌석 소지품 확인 안내"
    override val priority: Int = 20
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.CABIN

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        if (!session.hasUnoccupiedRearSeatLoading) return null
        if (!session.isArrivedAtDestination) return null
        if (session.currentGear != GearState.PARK) return null

        val rearRecord = session.closureHistory.lastOrNull {
            it.zone == DoorZone.REAR_LEFT || it.zone == DoorZone.REAR_RIGHT
        }

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "CABIN CONTEXT CARE",
            headline = "뒷좌석에 두고 내리는 물건이 없나요?",
            bodyMessage = "출발 전 2열 도어를 사용한 이력이 있고 동승자 착석이 감지되지 않았습니다.\n" +
                "뒷좌석에 올려둔 가방, 외투, 소지품을 잊지 말고 챙겨 내리세요.",
            contextReasonBadge = "2열 도어 개폐 + 시트 미착석 + 도착·P단",
            signalOrigin = rearRecord?.origin ?: SignalOrigin.MOCK_SIMULATOR,
            voiceTtsMessage = "출발 전 뒷좌석 문을 여셨습니다. 뒷좌석에 두고 내리는 가방이나 소지품이 없는지 확인해 주세요.",
            primaryAckLabel = "확인할게요"
        )
    }
}

