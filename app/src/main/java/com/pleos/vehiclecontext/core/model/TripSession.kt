package com.pleos.vehiclecontext.core.model

/**
 * 범용 차량 맥락 지능(Beyond Cargo)을 포괄하는 4대 맥락 도메인
 */
enum class ContextDomain(val displayName: String) {
    CARGO("적재/트렁크 맥락"),
    CABIN("실내/탑승자 맥락"),
    DRIVING("주행 역학/안전 맥락"),
    MOBILITY_EXIT("자율주행/하차 무결성 맥락")
}

/**
 * 트렁크/실내에 적재된 개별 화물 아이템 레코드.
 * 센서 개폐(Zero-Touch)와 AI 음성/프리셋(Micro-Enhancement)을 통합하여 복수 물품을 누적 관리합니다.
 * 물건의 목적지 B와 인간의 목적지 C가 일치하지 않는 경우(중간 경유지 전달 등)를 판별할 수 있도록
 * targetDestinationPoi, targetDestinationName 및 POI 기반 목적지 자동 추론 메타데이터를 포함합니다.
 */
data class CargoItemRecord(
    val id: String,
    val name: String,
    val zone: DoorZone = DoorZone.TRUNK,
    val categoryTag: CargoTag = CargoTag.GENERAL,
    val loadedPoi: PoiCategory = PoiCategory.UNKNOWN,
    val originName: String? = null,
    val targetDestinationPoi: PoiCategory? = null,
    val targetDestinationName: String? = null,
    val inferredDestinationPoi: PoiCategory? = null,
    val destinationConfidence: Float = 1.0f,
    val isDestinationAutoInferred: Boolean = false,
    val loadedTimestampMs: Long = System.currentTimeMillis(),
    val origin: SignalOrigin = SignalOrigin.REAL_SDK,
    val isPerishable: Boolean = false,
    val isPermanent: Boolean = false,
    val isUnloaded: Boolean = false,
    val quantity: Int = 1,
    val unloadedQuantity: Int = 0,
    val purpose: String = "",
    val countAsBundle: Boolean = false
) {
    val remainingQuantity: Int
        get() = (quantity - unloadedQuantity).coerceAtLeast(0)

    fun amountLabel(amount: Int = remainingQuantity): String =
        "$amount${if (countAsBundle) "묶음" else "개"}"
}

/**
 * 로컬에 학습 및 영속 저장되는 운전자 고유의 적재 행동 루틴 패턴
 */
data class CargoRoutinePattern(
    val routineId: String,
    val title: String,
    val originPoi: PoiCategory,
    val destinationPoi: PoiCategory,
    val typicalCategoryTag: CargoTag,
    val confidenceScore: Float,
    val occurrenceCount: Int = 1,
    val lastObservedTimestampMs: Long = System.currentTimeMillis()
)

/**
 * [개인화 서비스] 로컬에 영속 저장되어 맥락 인텔리전스의 적중률을 지속 향상시키는 사용자 습관 프로필
 */
data class PersonalizedCargoProfile(
    val driverId: String = "PRIMARY_DRIVER",
    val routines: List<CargoRoutinePattern> = listOf(
        CargoRoutinePattern(
            routineId = "routine_mart_home",
            title = "정기 마트 장보기 ➔ 귀가 루틴",
            originPoi = PoiCategory.MART_SHOPPING,
            destinationPoi = PoiCategory.HOME,
            typicalCategoryTag = CargoTag.FRESH_FOOD,
            confidenceScore = 0.94f,
            occurrenceCount = 12
        ),
        CargoRoutinePattern(
            routineId = "routine_leisure_home",
            title = "주말 캠핑/레저 ➔ 귀가 루틴",
            originPoi = PoiCategory.LEISURE_CAMPING,
            destinationPoi = PoiCategory.HOME,
            typicalCategoryTag = CargoTag.GENERAL,
            confidenceScore = 0.88f,
            occurrenceCount = 5
        )
    ),
    val averageUnloadDelaySeconds: Int = 30,
    val permanentTrunkPresets: Set<String> = setOf("우산", "장바구니", "골프백"),
    val lastUpdatedTimestampMs: Long = System.currentTimeMillis()
)

/**
 * 리더 기획안 슬라이드 3의 4단계를 일반화한 주행 세션 단계:
 * 01 출발 전 적재/개폐 -> 02 이동 중 대기(경유 포함) -> 03 도착과 주차(목적지+P단 또는 AVP 하차 존) -> 04 하차 안내 완료
 */
enum class TripPhase(val stepNumber: String, val title: String, val description: String) {
    PRE_TRIP("01", "출발 전 / 적재 감시", "트렁크 및 도어 개폐 이력과 AI 적재 메모를 현재 이동 세션에 기록"),
    DRIVING("02", "이동 중 대기", "수동 운전 시 시각 알림 보류, 자율주행(Lv.3+) 시 맞춤 주행 프로파일 연동"),
    INTERMEDIATE_STOP("02-B", "중간 정차 / 경유", "P단 전환이더라도 최종 목적지 도착 전이면 하차 안내 생략"),
    ARRIVED_PARKED("03", "도착 및 주차/하차존 진입", "현재 목적지 도착과 P단(또는 자율발렛 하차존)을 확인하여 하차 전 안내 발동")
}

/**
 * 도어/트렁크가 열렸다가 닫힌(OPEN -> CLOSE) 완결된 사용 이력 레코드
 */
data class ClosureUsageRecord(
    val zone: DoorZone,
    val openedAtPoi: PoiCategory,
    val cargoTag: CargoTag,
    val origin: SignalOrigin,
    val closedAtTimestampMs: Long
)

/**
 * 출발부터 적재, 주행, 도착, 하차 안내까지의 인과관계를 단계별로 추적하는 구조화된 타임라인 스텝 모델
 */
data class TripTimelineStep(
    val phaseLabel: String,
    val title: String,
    val detail: String,
    val origin: SignalOrigin,
    val isHighlight: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Layer 2 (Trip & Context Engine)가 유지 및 영속화하는 현재 이동 세션의 전체 맥락 상태.
 * 차량 센서 이력 + 자율주행 상태 + AI 추론 메모리(AiCargoMemory) + 실시간 적재 인벤토리를 통합 관리합니다.
 */
data class TripSession(
    val sessionId: String = "TRIP-${System.currentTimeMillis()}",
    val phase: TripPhase = TripPhase.PRE_TRIP,
    val currentGear: GearState = GearState.PARK,
    val autonomousMode: AutonomousDrivingMode = AutonomousDrivingMode.MANUAL,
    val autonomousProfile: AutonomousDrivingProfile = AutonomousDrivingProfile.NORMAL,
    val isAvpDepartureHoldActive: Boolean = false,
    val destinationName: String = "집 (성수동 자택)",
    val destinationPoi: PoiCategory = PoiCategory.HOME,
    val destinationAddress: String = "",
    val originName: String = "",
    val originAddress: String = "",
    val originLatitude: Double? = null,
    val originLongitude: Double? = null,
    val originPoi: PoiCategory = PoiCategory.UNKNOWN,
    val originSourceLabel: String = "GPS 위치 확인 중",
    val originSuggestions: List<String> = emptyList(),
    val destinationSuggestions: List<String> = emptyList(),
    val suggestedPresetKey: String? = null,
    val isArrivedAtDestination: Boolean = false,
    val hasReversedDuringDrive: Boolean = false,
    val hasExplicitIntermediateStop: Boolean = false,
    val openDoors: Set<DoorZone> = emptySet(),
    val pendingOpenPoiByZone: Map<DoorZone, PoiCategory> = emptyMap(),
    val pendingCargoTagByZone: Map<DoorZone, CargoTag> = emptyMap(),
    val closureHistory: List<ClosureUsageRecord> = emptyList(),
    val cargoInventory: List<CargoItemRecord> = emptyList(),
    val aiCargoMemory: AiCargoMemory? = null,
    val rearSeatOccupied: Boolean = false,
    val openWindowsCount: Int = 0,
    val cabinTempCelsius: Float = 22.0f,
    val harshDrivingCount: Int = 0,
    /**
     * 리더 기획안 슬라이드 7: "반복 P 전환 시 같은 도착에서 재안내 방지"
     * 현재 목적지 도착 세션에서 이미 안내가 나간 시나리오 ID 집합
     */
    val promptedScenarioIdsForCurrentArrival: Set<String> = emptySet(),
    /**
     * 리더 기획안 슬라이드 4: "'확인할게요'는 확인 의사로만 기록합니다"
     */
    val acknowledgedScenarioIds: Set<String> = emptySet(),
    /**
     * 상시 보관 물품(인벤토리) 목록 - 다음 주행이나 역방향 알림에 활용
     */
    val permanentTrunkNotes: Set<String> = emptySet(),
    /**
     * [개인화 맥락 자원] 로컬에 저장된 사용자 고유의 적재 패턴 및 습관 프로필
     */
    val personalizedProfile: PersonalizedCargoProfile = PersonalizedCargoProfile(),
    /**
     * 이번 주행에서 감지된 개인화 적재 루틴 명칭 (예: "정기 마트 장보기 ➔ 귀가 루틴")
     */
    val matchedRoutineTitle: String? = null,
    /**
     * 적재부터 출발, 주행, 도착, 하차 안내까지의 인과관계를 추적하는 구조화된 타임라인
     */
    val timelineSteps: List<TripTimelineStep> = emptyList(),
    /**
     * 트렁크 개폐(적재 감지) 후 자동/수동 화물 등록 요청 식별자
     */
    val pendingLoadingPromptRequestId: String? = null,
    /**
     * 트렁크 닫힘 직후 자동 음성 입력을 1회만 트리거하기 위한 식별자
     */
    val autoVoiceRegistrationId: String? = null,
    /**
     * 도착 + P단 상태에서 트렁크를 열고 닫았을 때 하차 수량 확인 필요 여부
     */
    val unloadConfirmationNeeded: Boolean = false
) {
    /**
     * 이번 이동에서 활성화된 트렁크 적재 화물이 존재하는지 여부 (상시 보관 제외)
     */
    val hasActiveTrunkUsage: Boolean
        get() = cargoInventory.any {
            it.zone == DoorZone.TRUNK && !it.isPermanent && !it.isUnloaded && it.remainingQuantity > 0
        } ||
            closureHistory.any {
                it.zone == DoorZone.TRUNK && it.cargoTag != CargoTag.PERMANENT_KEEP
            } || (aiCargoMemory != null && aiCargoMemory.inferredPrimaryTag != CargoTag.PERMANENT_KEEP)

    /**
     * 가장 최근의 트렁크 사용 기록
     */
    val latestTrunkUsage: ClosureUsageRecord?
        get() = closureHistory.lastOrNull { it.zone == DoorZone.TRUNK }

    /**
     * 이번 이동에서 2열 도어 열림->닫힘 이력이 있고 사람이 탑승하지 않은(짐 적재 추론) 여부
     */
    val hasUnoccupiedRearSeatLoading: Boolean
        get() = !rearSeatOccupied && closureHistory.any {
            it.zone == DoorZone.REAR_LEFT || it.zone == DoorZone.REAR_RIGHT
        }
}
