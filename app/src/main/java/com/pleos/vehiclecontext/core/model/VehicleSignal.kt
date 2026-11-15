package com.pleos.vehiclecontext.core.model

/**
 * 리더 기획안 슬라이드 7: "실제 입력과 모의 입력을 구분해 동작을 시연합니다"
 * 모든 센서/내비/자율주행/AI 신호는 출처(실제 Pleos/AAOS SDK vs 시연 시뮬레이터)를 명시합니다.
 */
enum class SignalOrigin(val displayName: String) {
    REAL_SDK("실제 차량 SDK 입력"),
    MOCK_SIMULATOR("모의 시뮬레이터 입력"),
    AI_AGENT_INFERRED("AI 에이전트 추론")
}

/**
 * AAOS GEAR_SELECTION 기반 차량 기어 상태
 */
enum class GearState(val label: String) {
    PARK("P (주차)"),
    DRIVE("D (주행)"),
    REVERSE("R (후진)"),
    NEUTRAL("N (중립)")
}

/**
 * 자율주행(ADAS / Autonomous Driving) 작동 모드.
 * 주행 주도권이 운전자에게 있는지 시스템에 있는지에 따라
 * 화면 UI 노출 허용 여부(allowsRichUiWhileMoving)와 하차 시나리오(AVP Drop-off)가 달라집니다.
 */
enum class AutonomousDrivingMode(
    val displayName: String,
    val allowsRichUiWhileMoving: Boolean
) {
    MANUAL("수동 운전 (Lv.0~1)", allowsRichUiWhileMoving = false),
    SMART_CRUISE_ADAS("스마트 크루즈 ADAS (Lv.2)", allowsRichUiWhileMoving = false),
    HIGHWAY_PILOT_L3("조건부 자율주행 (Lv.3+ Eyes-Off)", allowsRichUiWhileMoving = true),
    AUTONOMOUS_VALET_DROPOFF("자율 발렛 파킹 하차 존 (AVP Drop-off)", allowsRichUiWhileMoving = true)
}

/**
 * 자율주행 제어 프로파일 (적재물 및 동승자 상태에 따라 동적 제어)
 */
enum class AutonomousDrivingProfile(val displayName: String) {
    NORMAL("표준 주행 프로파일"),
    CARGO_COMFORT_GUARD("적재물·동승자 보호 Comfort (완만한 가감속·차선변경 최소화)"),
    DYNAMIC("다이나믹 프로파일")
}

/**
 * Pleos Vehicle SDK Door 영역 확장 도메인
 * 트렁크(MVP)에서 시작해 1/2열 도어까지 범용적으로 수용합니다.
 */
enum class DoorZone(val displayName: String) {
    TRUNK("트렁크"),
    REAR_LEFT("2열 좌측 도어"),
    REAR_RIGHT("2열 우측 도어"),
    FRONT_LEFT("운전석 도어"),
    FRONT_RIGHT("조수석 도어")
}

/**
 * FusedLocation / NaviHelper 결합용 장소 맥락(POI) 카테고리
 */
enum class PoiCategory(val displayName: String) {
    UNKNOWN("일반 장소"),
    MART_SHOPPING("마트/백화점"),
    HOME("집 (귀가)"),
    AIRPORT_STATION("공항/기차역"),
    OFFICE("직장/오피스"),
    LEISURE_CAMPING("캠핑/레저")
}

/**
 * 적재 물품 성격 태그 (음성/AI 대화 태그 또는 장소 기반 추론)
 */
enum class CargoTag(val displayName: String) {
    GENERAL("일반 짐"),
    FRESH_FOOD("장보기/신선·냉동식품"),
    FRAGILE("파손주의/케이크·와인·유리"),
    PERMANENT_KEEP("상시 보관 물품")
}

/**
 * AI 컨텍스트 서비스(STT + LLM 추론)가 사용자의 자연어 발화와 장소 맥락에서 추출한 구조화 메모리
 */
data class AiCargoMemory(
    val rawUserUtterance: String,
    val inferredItems: List<String>,
    val inferredPrimaryTag: CargoTag,
    val isTemperatureSensitive: Boolean,
    val requiresSoftAutonomousProfile: Boolean,
    val aiBriefingSummary: String
)

/**
 * Layer 1 (Signal Collector)에서 발생하는 표준화된 차량·자율주행·음성 이벤트 모델
 */
sealed interface VehicleSignal {
    val timestampMs: Long
    val origin: SignalOrigin

    /**
     * 트렁크 또는 도어 열림/닫힘 이벤트 (Vehicle SDK Door 영역)
     */
    data class DoorStateChanged(
        val zone: DoorZone,
        val isOpen: Boolean,
        val currentLocationPoi: PoiCategory = PoiCategory.UNKNOWN,
        val cargoTag: CargoTag = CargoTag.GENERAL,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 기어 변경 이벤트 (AAOS CarPropertyManager GEAR_SELECTION)
     */
    data class GearChanged(
        val gear: GearState,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 목적지 설정 또는 주행 중 경로 변경 이벤트 (NaviHelper SDK)
     */
    data class DestinationUpdated(
        val destinationName: String,
        val destinationPoi: PoiCategory = PoiCategory.HOME,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 현재 목적지 도착 여부 변경 이벤트 (NaviHelper SDK)
     */
    data class DestinationArrived(
        val destinationName: String,
        val isFinalDestination: Boolean = true,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 주행 중 급가속·급감속 감지 이벤트 (적재물 보호 주행 확장 시나리오용)
     */
    data class HarshDrivingDetected(
        val description: String,
        val intensityG: Float = 0.45f,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 실내/차체 환경 상태 업데이트 (2열 착석 여부, 열린 창문 수, 실내 온도 등)
     */
    data class CabinStatusUpdated(
        val rearSeatOccupied: Boolean,
        val openWindowsCount: Int,
        val cabinTempCelsius: Float = 22.0f,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 자율주행(ADAS / Lv.3+ 자율주행 / 자율 발렛 파킹 AVP) 모드 변경 이벤트
     */
    data class AutonomousModeChanged(
        val mode: AutonomousDrivingMode,
        val profile: AutonomousDrivingProfile? = null,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * 차량 내 마이크(SpeechToText SDK)를 통해 수신된 운전자/동승자의 자연어 적재 메모 발화
     */
    data class VoiceCargoNoteReceived(
        val utterance: String,
        val currentLocationPoi: PoiCategory = PoiCategory.MART_SHOPPING,
        override val origin: SignalOrigin = SignalOrigin.AI_AGENT_INFERRED,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal

    /**
     * GPS, Geocoder 및 Pleos NaviHelper(현위치·내비 출발지·목적지·즐겨찾기·최근 목적지) 기반 위치/경로 맥락 업데이트
     */
    data class LocationContextUpdated(
        val originName: String = "",
        val originAddress: String = "",
        val originLatitude: Double? = null,
        val originLongitude: Double? = null,
        val originPoi: PoiCategory = PoiCategory.UNKNOWN,
        val originSourceLabel: String = "GPS",
        val destinationName: String = "",
        val destinationAddress: String = "",
        val destinationLatitude: Double? = null,
        val destinationLongitude: Double? = null,
        val originSuggestions: List<String> = emptyList(),
        val destinationSuggestions: List<String> = emptyList(),
        val suggestedPresetKey: String? = null,
        override val origin: SignalOrigin = SignalOrigin.REAL_SDK,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : VehicleSignal
}
