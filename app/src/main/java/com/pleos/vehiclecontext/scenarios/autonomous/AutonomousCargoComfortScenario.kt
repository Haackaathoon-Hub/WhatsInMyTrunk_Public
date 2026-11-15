package com.pleos.vehiclecontext.scenarios.autonomous

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.AutonomousDrivingProfile
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [자율주행 × AI 킬러 시나리오 2] 적재물 맞춤형 자율주행 프로파일(Cargo Comfort Mode) 능동 제어
 *
 * 동작 조건:
 * - 자율주행 모드(Lv.2 ADAS 또는 Lv.3+ Highway Pilot)가 활성화됨
 * - 트렁크에 파손주의/신선식품(`FRAGILE` / `FRESH_FOOD`)이 적재되었거나 AI가 흔들림 주의 물품(케이크, 와인 등)을 인식함
 * - 현재 자율주행 프로파일이 아직 `CARGO_COMFORT_GUARD`가 아님
 *
 * 제공 가치:
 * - 단순히 운전자에게 "살살 운전하세요"라고 말하는 것을 넘어,
 *   자율주행 제어기의 가감속 곡선과 차선 변경 빈도를 완만하게 조절하는 '적재물 보호 주행 모드' 전환을 제안합니다.
 */
class AutonomousCargoComfortScenario : ScenarioPlugin {
    override val id: String = ID
    override val name: String = "자율주행 적재물 보호 프로파일(Comfort) 연동 제어"
    override val description: String = "파손주의·장보기 적재 상태에서 자율주행 활성화 시 완만한 가감속·차선변경 최소화 프로파일 전환 제안"
    override val priority: Int = 3
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.MOBILITY_EXIT

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        val isAdActive = session.autonomousMode == AutonomousDrivingMode.HIGHWAY_PILOT_L3 ||
            session.autonomousMode == AutonomousDrivingMode.SMART_CRUISE_ADAS
        if (!isAdActive) return null
        if (!session.hasActiveTrunkUsage) return null
        if (session.autonomousProfile == AutonomousDrivingProfile.CARGO_COMFORT_GUARD) return null

        // 자율주행 모드 전환 신호이거나 AI 음성 메모 수신 직후일 때 발동
        val isRelevantTrigger = latestSignal is VehicleSignal.AutonomousModeChanged ||
            latestSignal is VehicleSignal.VoiceCargoNoteReceived
        if (!isRelevantTrigger) return null

        val cargoSummary = session.aiCargoMemory?.inferredItems?.joinToString(", ")
            ?: session.latestTrunkUsage?.cargoTag?.displayName
            ?: CargoTag.GENERAL.displayName

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "AI × AUTONOMOUS DRIVING CONTROL",
            headline = "트렁크 적재물 보호를 위해 자율주행을 'Comfort 모드'로 바꿀까요?",
            bodyMessage = "현재 [${session.autonomousMode.displayName}] 주행 중이며 트렁크에 [$cargoSummary]이(가) 실려 있습니다.\n" +
                "짐이 흔들리지 않도록 자율주행 가감속과 차선 변경을 부드럽게 제어할 수 있습니다.",
            contextReasonBadge = "${session.autonomousMode.displayName} + 적재물($cargoSummary)",
            signalOrigin = SignalOrigin.AI_AGENT_INFERRED,
            voiceTtsMessage = "트렁크에 실린 짐이 흔들리지 않도록 자율주행 스타일을 적재물 보호 컴포트 모드로 전환할까요?",
            aiReasoningNote = "🤖 AI × ADAS 제어 추천: 수동 운전이 아닌 자율주행 활성 상태이므로 차량 스스로 가감속 G-Force를 제한할 수 있습니다.",
            primaryAckLabel = "그대로 유지",
            availableControls = listOf(VehicleControlCommand.SWITCH_AD_CARGO_COMFORT),
            canDisplayInAutonomousDriving = true
        )
    }

    companion object {
        const val ID = "autonomous_cargo_comfort_profile"
    }
}

