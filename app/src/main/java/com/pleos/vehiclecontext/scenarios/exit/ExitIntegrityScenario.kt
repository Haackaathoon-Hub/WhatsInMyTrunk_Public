package com.pleos.vehiclecontext.scenarios.exit

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [확장 시나리오 3] 하차 전 창문/선루프 열림 통합 점검 및 원터치 제어
 *
 * 목적지 도착 및 P단 주차 시 열린 창문이 감지되면 하차 전 원터치 닫기 제어를 제안합니다.
 */
class ExitIntegrityScenario : ScenarioPlugin {
    override val id: String = "exit_window_integrity_guard"
    override val name: String = "하차 전 창문 열림 점검 & 제어 (차체 제어 확장)"
    override val description: String = "목적지 도착 + P단 진입 시 열려 있는 창문을 감지하고 원터치 닫기 제어 버튼 제공"
    override val priority: Int = 30
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.MOBILITY_EXIT

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        if (!session.isArrivedAtDestination) return null
        if (session.currentGear != GearState.PARK) return null
        if (session.openWindowsCount <= 0) return null

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "TOTAL EXIT GUARD",
            headline = "내리기 전, 창문 ${session.openWindowsCount}개가 열려 있어요",
            bodyMessage = "'${session.destinationName}' 주차가 완료되었으나 열려 있는 창문이 감지되었습니다.\n" +
                "버튼을 눌러 내리기 전에 모든 창문을 한 번에 닫을 수 있습니다.",
            contextReasonBadge = "창문 열림(${session.openWindowsCount}개) + 목적지 도착 + P단",
            signalOrigin = latestSignal.origin,
            voiceTtsMessage = "현재 창문 ${session.openWindowsCount}개가 열려 있습니다. 내리기 전에 창문을 닫아주세요.",
            primaryAckLabel = "확인할게요",
            availableControls = listOf(VehicleControlCommand.CLOSE_ALL_WINDOWS)
        )
    }
}

