package com.pleos.vehiclecontext.core.engine

import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * Layer 3: 모든 차량 컨텍스트 시나리오가 구현하는 범용 플러그인 인터페이스.
 *
 * 처음에는 리더 기획안인 `TrunkReminderScenario`(트렁크 하차 전 안내)에 집중하고,
 * 이후 2열 도어 소지품 케어, 적재물 보호 주행, 하차 시 창문/공조 점검 등을
 * 엔진 수정 없이 플러그인 형태로 추가할 수 있습니다.
 */
interface ScenarioPlugin {
    val id: String
    val name: String
    val description: String
    val priority: Int
    val isMvpCore: Boolean
    val domain: ContextDomain get() = ContextDomain.CARGO

    /**
     * 현재 주행 세션(TripSession)과 방금 유입된 신호(VehicleSignal)를 평가하여
     * 사용자에게 제공할 부가가치 액션(ContextAction)이 있으면 반환합니다.
     */
    fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction?
}

