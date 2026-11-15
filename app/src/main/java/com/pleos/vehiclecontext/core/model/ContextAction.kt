package com.pleos.vehiclecontext.core.model

/**
 * 시나리오가 발동했을 때 차량 및 자율주행 시스템에 요청할 수 있는 물리적 제어/상태 명령
 */
enum class VehicleControlCommand(val buttonLabel: String, val actionFeedback: String) {
    OPEN_TRUNK(
        "지금 트렁크 열기",
        "Vehicle SDK를 통해 전동 트렁크 개방 명령을 전송했습니다."
    ),
    CLOSE_TRUNK(
        "트렁크 닫기",
        "Vehicle SDK를 통해 전동 트렁크 닫힘 명령을 전송하고 제어 점유를 해제했습니다."
    ),
    CLOSE_ALL_WINDOWS(
        "창문 모두 닫기",
        "Vehicle SDK를 통해 열린 창문/선루프 닫기 명령을 전송했습니다."
    ),
    KEEP_IN_TRUNK_PERMANENTLY(
        "차에 계속 보관할 짐이에요",
        "상시 보관 물품으로 기록하여 반복 알림에서 제외합니다."
    ),
    SWITCH_AD_CARGO_COMFORT(
        "자율주행 '적재물 보호(Comfort)' 모드 적용",
        "자율주행 제어기에 완만한 가감속 및 차선변경 최소화 프로파일(Cargo Comfort Guard)을 적용했습니다."
    ),
    HOLD_AVP_AND_OPEN_TRUNK(
        "자율주차 출발 보류 & 트렁크 열기",
        "차량이 혼자 주차장으로 떠나지 않도록 자율 발렛 파킹(AVP)을 일시 홀드하고 트렁크를 개방했습니다."
    ),
    ACTIVATE_CABIN_COOLING(
        "실내 급속 냉방(20°C) 가동",
        "Vehicle SDK HVAC 제어를 통해 실내 목표 온도를 20.0°C로 설정하고 냉방을 가동했습니다."
    )
}

/**
 * Layer 4 (Action & UX Dispatcher)로 전달되는 범용 부가가치 출력 모델
 */
data class ContextAction(
    val scenarioId: String,
    val scenarioTitle: String,
    val headline: String,
    val bodyMessage: String,
    val contextReasonBadge: String,
    val signalOrigin: SignalOrigin,
    val voiceTtsMessage: String,
    val aiReasoningNote: String? = null,
    val primaryAckLabel: String = "확인할게요",
    val dismissLabel: String = "닫기",
    val availableControls: List<VehicleControlCommand> = emptyList(),
    val isDrivingSafeVoiceOnly: Boolean = false,
    val canDisplayInAutonomousDriving: Boolean = false,
    val triggeredAtMs: Long = System.currentTimeMillis()
)
