package com.pleos.vehiclecontext.scenarios.driving

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [확장 시나리오 2] 적재물 보호 주행 가드 (주행 역학 센서 + 트렁크 맥락 결합)
 *
 * 트렁크에 짐(특히 신선식품/파손주의 물품)이 실린 상태에서 주행 중 급가속·급감속이 감지되면
 * 운전 방해 없이 음성(TTS)과 간결한 토스트 배너로 안전 주행을 가이드합니다.
 */
class CargoSafeDrivingScenario : ScenarioPlugin {
    override val id: String = "cargo_safe_driving_guard"
    override val name: String = "적재물 보호 주행 피드백 (주행 센서 결합)"
    override val description: String = "트렁크 적재 상태에서 주행 중 급가속·급감속 감지 시 음성으로 적재물 흔들림 주의 안내"
    override val priority: Int = 5
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.DRIVING

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        val harshSignal = latestSignal as? VehicleSignal.HarshDrivingDetected ?: return null
        if (!session.hasActiveTrunkUsage) return null
        if (session.currentGear != GearState.DRIVE) return null

        val cargoTag = session.latestTrunkUsage?.cargoTag ?: CargoTag.GENERAL

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "CARGO SAFE DRIVING",
            headline = "트렁크 적재물 흔들림 주의 (${harshSignal.description})",
            bodyMessage = "현재 트렁크에 [${cargoTag.displayName}]이 실려 있습니다. 적재물 보호를 위해 부드럽게 가감속해 주세요.",
            contextReasonBadge = "트렁크 적재 중 + ${harshSignal.description} (${harshSignal.intensityG}G)",
            signalOrigin = harshSignal.origin,
            voiceTtsMessage = "트렁크에 실린 짐이 흔들릴 수 있습니다. 부드럽게 주행해 주세요.",
            primaryAckLabel = "알겠어요",
            isDrivingSafeVoiceOnly = true
        )
    }
}

