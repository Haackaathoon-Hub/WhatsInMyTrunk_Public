package com.pleos.vehiclecontext.scenarios.autonomous

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [자율주행 × 트렁크 킬러 시나리오 1] 자율 발렛 파킹(AVP) Drop-off 하차 존 짐 미수거 방지 가드
 *
 * 문제 배경:
 * 자율주행 시대에는 목적지 입구(Drop-off Zone)에서 사람만 먼저 하차하고,
 * 차량은 스스로 지하주차장이나 충전소로 이동(Autonomous Valet Parking)합니다.
 * 이때 트렁크나 뒷좌석의 짐을 꺼내지 않으면 짐을 실은 채로 차가 혼자 멀리 가버립니다.
 *
 * 동작 조건:
 * - 자율주행 모드가 `AUTONOMOUS_VALET_DROPOFF`로 전환됨
 * - 이번 이동에서 트렁크 적재(`hasActiveTrunkUsage`) 또는 2열 소지품 적재(`hasUnoccupiedRearSeatLoading`)가 존재함
 *
 * 제공 가치:
 * - 차량이 스스로 주차하러 떠나기 전에 최우선 순위로 출발을 보류(Hold)하고 트렁크 개방을 제안합니다.
 */
class AutonomousValetDropoffScenario : ScenarioPlugin {
    override val id: String = ID
    override val name: String = "자율 발렛 파킹(AVP) 하차 존 짐 미수거 방지 가드"
    override val description: String = "자율주행 하차 존(Drop-off)에서 차량이 혼자 주차장으로 떠나기 전 트렁크/2열 짐 하차 확인 및 출발 보류 제어"
    override val priority: Int = 1 // 최우선 순위 발동
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.MOBILITY_EXIT

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        if (session.autonomousMode != AutonomousDrivingMode.AUTONOMOUS_VALET_DROPOFF) return null
        if (!session.hasActiveTrunkUsage && !session.hasUnoccupiedRearSeatLoading) return null

        val cargoDesc = session.aiCargoMemory?.inferredItems?.joinToString(", ")
            ?: if (session.hasActiveTrunkUsage) "트렁크 적재 물품" else "2열 뒷좌석 소지품"

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "AUTONOMOUS VALET (AVP) GUARD",
            headline = "자율주차로 차량이 이동하기 전, 짐을 먼저 꺼내세요!",
            bodyMessage = "하차 존(Drop-off Zone)에 정차했습니다. 잠시 후 차량이 스스로 주차장으로 이동합니다.\n" +
                "차량이 떠나기 전에 [$cargoDesc]을(를) 먼저 꺼내실 수 있도록 자율주차 출발을 보류할까요?",
            contextReasonBadge = "자율발렛 Drop-off 진입 + 적재 이력 감지 ($cargoDesc)",
            signalOrigin = SignalOrigin.AI_AGENT_INFERRED,
            voiceTtsMessage = "자율 발렛 파킹으로 차량이 이동하기 전에 트렁크에 실으신 짐을 먼저 꺼내 주세요. 자율주차 출발을 잠시 보류할까요?",
            aiReasoningNote = "🤖 자율주행(AVP) 연동 판단: 승객 하차 지점과 최종 주차 위치가 분리되어 있어, 지금 짐을 내리지 않으면 차량과 함께 지하주차장으로 이동합니다.",
            primaryAckLabel = "확인했어요 (출발 진행)",
            availableControls = listOf(
                VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK,
                VehicleControlCommand.OPEN_TRUNK
            ),
            canDisplayInAutonomousDriving = true
        )
    }

    companion object {
        const val ID = "autonomous_valet_dropoff_guard"
    }
}

