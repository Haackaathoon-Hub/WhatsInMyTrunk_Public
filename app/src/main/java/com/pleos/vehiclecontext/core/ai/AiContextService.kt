package com.pleos.vehiclecontext.core.ai

import com.pleos.vehiclecontext.core.model.AiCargoMemory
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.TripSession

/**
 * AI 컨텍스트 추론 서비스 인터페이스 (Pleos Gleo AI / Gemini LLM / 온디바이스 NLU 추상화)
 *
 * 역할:
 * 1. 운전자/동승자의 자연어 음성 발화(STT)와 출발지 POI를 분석해 적재물의 속성(신선/냉동, 파손주의, 상시보관)과
 *    자율주행 제어 요구사항(부드러운 주행 프로파일 필요 여부)을 구조화된 데이터(AiCargoMemory)로 추출합니다.
 * 2. 복합 센서(트렁크, 2열, 온도, 자율주행 모드)와 AI 메모리를 결합해 개인화된 하차/주행 브리핑을 생성합니다.
 */
interface AiContextService {
    fun analyzeCargoUtterance(utterance: String, currentPoi: PoiCategory): AiCargoMemory
    fun enrichActionWithAiContext(session: TripSession, baseAction: ContextAction): ContextAction
}

/**
 * 하이브리드 AI 컨텍스트 에이전트 구현체:
 * 네트워크/API 키 없이도 에뮬레이터에서 즉각 동작하는 온디바이스 시맨틱 분석기와
 * 외부 LLM(Gemini API 등) 프롬프트 빌더를 함께 제공합니다.
 */
class PleosAiContextAgent : AiContextService {

    override fun analyzeCargoUtterance(utterance: String, currentPoi: PoiCategory): AiCargoMemory {
        val text = utterance.trim()
        val inferredItems = mutableListOf<String>()
        var isTempSensitive = false
        var needsSoftAdProfile = false

        // 1. 냉장·냉동·신선식품 키워드 감지
        val tempKeywords = listOf("아이스크림", "냉동", "우유", "생선", "회", "정육", "고기", "신선", "장바구니", "식재료")
        tempKeywords.forEach { kw ->
            if (text.contains(kw)) {
                inferredItems.add("$kw (온도/변질 주의)")
                isTempSensitive = true
            }
        }

        // 2. 파손주의·충격민감 키워드 감지
        val fragileKeywords = listOf("케이크", "와인", "유리", "계란", "꽃다발", "노트북", "카메라", "도자기", "모니터")
        fragileKeywords.forEach { kw ->
            if (text.contains(kw)) {
                inferredItems.add("$kw (파손/흔들림 주의)")
                needsSoftAdProfile = true
            }
        }

        // 3. 상시 보관 키워드 감지
        val permanentKeywords = listOf("캠핑", "세차", "골프백", "우산", "워셔액", "계속 보관", "상시")
        val isPermanentOnly = permanentKeywords.any { text.contains(it) } && !isTempSensitive && !needsSoftAdProfile
        permanentKeywords.forEach { kw ->
            if (text.contains(kw)) {
                inferredItems.add("$kw (상시 보관 후보)")
            }
        }

        if (inferredItems.isEmpty()) {
            inferredItems.add("$text (${currentPoi.displayName} 적재 물품)")
        }

        val primaryTag = when {
            needsSoftAdProfile -> CargoTag.FRAGILE
            isTempSensitive -> CargoTag.FRESH_FOOD
            isPermanentOnly -> CargoTag.PERMANENT_KEEP
            else -> CargoTag.GENERAL
        }

        val summaryParts = mutableListOf<String>()
        summaryParts.add("인식 물품: ${inferredItems.joinToString(", ")}")
        if (needsSoftAdProfile) {
            summaryParts.add("자율주행 '적재물 보호(Comfort)' 프로파일 권장")
        }
        if (isTempSensitive) {
            summaryParts.add("도착 즉시 하차 최우선 알림 설정")
        }

        return AiCargoMemory(
            rawUserUtterance = text,
            inferredItems = inferredItems,
            inferredPrimaryTag = primaryTag,
            isTemperatureSensitive = isTempSensitive,
            requiresSoftAutonomousProfile = needsSoftAdProfile,
            aiBriefingSummary = summaryParts.joinToString(" · ")
        )
    }

    override fun enrichActionWithAiContext(
        session: TripSession,
        baseAction: ContextAction
    ): ContextAction {
        val memory = session.aiCargoMemory ?: return baseAction
        val itemsText = memory.inferredItems.joinToString(", ")

        val enrichedNote = buildString {
            append("🤖 AI 컨텍스트 분석: \"${memory.rawUserUtterance}\" 발화로부터 [$itemsText] 적재를 파악했습니다.")
            if (memory.isTemperatureSensitive && session.cabinTempCelsius >= 24.0f) {
                append(" (현재 차량 온도 ${session.cabinTempCelsius}°C — 신선식품 우선 하차 권장)")
            }
        }

        val enrichedVoice = "${session.destinationName}에 도착했습니다. 트렁크에 실으신 ${memory.inferredItems.firstOrNull() ?: "짐"}을 잊지 말고 꺼내 주세요."

        return baseAction.copy(
            aiReasoningNote = enrichedNote,
            voiceTtsMessage = enrichedVoice
        )
    }

    /**
     * 외부 LLM (Gemini API / Pleos Cloud AI) 연동 시 사용할 구조화 시스템 프롬프트 생성기
     */
    fun buildLlmSystemPrompt(session: TripSession): String = """
        당신은 현대자동차그룹 Pleos Connect 기반의 인카 AI 컨텍스트 에이전트입니다.
        [현재 차량 센서 및 자율주행 맥락]
        - 주행 단계: ${session.phase.title} (기어: ${session.currentGear.label})
        - 자율주행 모드: ${session.autonomousMode.displayName} (${session.autonomousProfile.displayName})
        - 목적지: ${session.destinationName} (도착 여부: ${session.isArrivedAtDestination})
        - 트렁크 사용 이력: ${session.hasActiveTrunkUsage}
        - AI 적재 메모리: ${session.aiCargoMemory?.aiBriefingSummary ?: "없음"}
        - 2열 미착석 적재 추론: ${session.hasUnoccupiedRearSeatLoading}
        - 열린 창문 수: ${session.openWindowsCount}개, 실내 온도: ${session.cabinTempCelsius}°C
        위 센서 데이터를 결합해 운전자에게 가장 안전하고 간결한 하차/주행 브리핑과 차량 제어 명령을 생성하세요.
    """.trimIndent()
}

