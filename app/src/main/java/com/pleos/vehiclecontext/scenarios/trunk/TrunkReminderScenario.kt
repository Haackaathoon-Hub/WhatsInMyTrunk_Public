package com.pleos.vehiclecontext.scenarios.trunk

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [MVP 핵심 시나리오] 프로젝트 리더 기획안: "WHAT'S IN MY TRUNK? 내리기 전, 트렁크를 한 번 더"
 *
 * 동작 조건:
 * 1. 이번 이동에서 출발 전(또는 경유지) 트렁크 열림 -> 닫힘 이력이 기록됨 (또는 cargoInventory / AI 적재 메모 존재)
 * 2. 현재 목적지 도착(NaviHelper) + P단(GEAR_SELECTION) 조건이 함께 충족됨
 * 3. [B vs C 목적지 맥락 매칭] 현재 정차지가 화물의 목적지(B)와 부합하는지 판별 (중간 정차 오알림 원천 차단)
 * 4. 같은 도착에서 아직 안내되지 않았음 (1회 안내 보장)
 *
 * 부가가치 확장:
 * - 트렁크가 열렸던 장소(마트/공항 등) 및 AI/센서가 파악한 복수 물품(신선식품, 케이크 등)을 인벤토리 기반 브리핑
 * - 단순 '확인할게요' 의사 기록뿐 아니라, Vehicle SDK 연동 '지금 트렁크 열기' 및 '차에 계속 보관' 제어 제공
 */
class TrunkReminderScenario : ScenarioPlugin {
    override val id: String = ID
    override val name: String = "트렁크 하차 전 안내 (MVP 핵심)"
    override val description: String = "이번 이동의 트렁크 개폐 이력 + 목적지 도착 + P단 확인 시 하차 전 1회 안내 및 트렁크 제어 제공"
    override val priority: Int = 10
    override val isMvpCore: Boolean = true
    override val domain: ContextDomain = ContextDomain.CARGO

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        if (!session.hasActiveTrunkUsage) return null
        if (!session.isArrivedAtDestination) return null
        if (session.currentGear != GearState.PARK) return null

        // 1. 활성 트렁크 적재 화물 파악 (상시 보관 및 이미 하차 완료된 화물 제외)
        val activeTrunkItems = session.cargoInventory.filter {
            it.zone == DoorZone.TRUNK && !it.isPermanent && !it.isUnloaded
        }

        // 2. [B vs C 목적지 불일치 해법]: 특정 목적지가 지정되었거나 자동 추론된 화물이 있는 경우,
        // 현재 도착지가 해당 화물의 목적지와 매칭되는지 확인 (중간 정차 오알림 원천 차단)
        val targetItemsForCurrentStop = if (activeTrunkItems.isNotEmpty()) {
            val destinationMatched = activeTrunkItems.filter { item ->
                (item.targetDestinationPoi == null && item.targetDestinationName == null && item.inferredDestinationPoi == null) ||
                    (item.targetDestinationPoi != null && item.targetDestinationPoi == session.destinationPoi) ||
                    (item.targetDestinationName != null && session.destinationName.contains(item.targetDestinationName)) ||
                    (item.inferredDestinationPoi != null && item.inferredDestinationPoi == session.destinationPoi && item.destinationConfidence >= 0.8f)
            }
            if (destinationMatched.isEmpty()) {
                // 이번 정차지(C)가 적재된 화물들의 목적지(B)와 전혀 매칭되지 않는 중간 정차인 경우 침묵
                return null
            }
            destinationMatched
        } else {
            emptyList()
        }

        val latestRecord = session.latestTrunkUsage
        val poiCategory = latestRecord?.openedAtPoi ?: session.cargoInventory.lastOrNull()?.loadedPoi ?: PoiCategory.MART_SHOPPING
        val cargoTag = session.aiCargoMemory?.inferredPrimaryTag
            ?: latestRecord?.cargoTag
            ?: session.cargoInventory.lastOrNull()?.categoryTag
            ?: CargoTag.GENERAL

        val poiText = when (poiCategory) {
            PoiCategory.MART_SHOPPING -> "마트/백화점에서"
            PoiCategory.AIRPORT_STATION -> "공항/역에서"
            PoiCategory.LEISURE_CAMPING -> "캠핑/레저 장소에서"
            PoiCategory.OFFICE -> "출발지에서"
            else -> "출발 전"
        }

        // 구체적인 물품 명칭 추출 (cargoInventory 우선 + AI 메모 fallback)
        val itemNames = if (targetItemsForCurrentStop.isNotEmpty()) {
            targetItemsForCurrentStop.map { it.name }.distinct().joinToString(", ")
        } else {
            session.aiCargoMemory?.inferredItems?.joinToString(", ")
        }

        val hasPerishable = targetItemsForCurrentStop.any { it.isPerishable } ||
            session.aiCargoMemory?.isTemperatureSensitive == true ||
            cargoTag == CargoTag.FRESH_FOOD

        val tagNote = when {
            !itemNames.isNullOrBlank() && hasPerishable ->
                "[$itemNames] 등 신선·온도 관리가 필요한 짐이 있으니 잊지 말고 챙겨 내리세요."
            !itemNames.isNullOrBlank() ->
                "기억해 둔 [$itemNames]을(를) 잊지 말고 챙겨 내리세요."
            cargoTag == CargoTag.FRESH_FOOD ->
                "신선식품·장바구니 등 잊기 쉬운 짐이 있는지 확인해 보세요."
            cargoTag == CargoTag.FRAGILE ->
                "파손 주의 물품을 안전하게 꺼내 주세요."
            else ->
                "내리기 전에 트렁크에 실은 짐을 한 번 더 확인해 보세요."
        }

        val routineTitle = session.matchedRoutineTitle
        val routineNote = if (!routineTitle.isNullOrBlank()) {
            "\n[개인화 맥락] 평소 자주 이용하시는 '$routineTitle'을 감지했습니다."
        } else ""

        val reasonBadge = if (!routineTitle.isNullOrBlank()) {
            "개인화 루틴($routineTitle) + 목적지 도착 + P단"
        } else if (!itemNames.isNullOrBlank()) {
            "트렁크 적재($itemNames) + 목적지 도착 + P단"
        } else {
            "트렁크 개폐(${poiCategory.displayName}) + 목적지 도착 + P단"
        }

        val headline = if (!routineTitle.isNullOrBlank()) {
            "평소 장보기 귀가 패턴을 감지했어요! 트렁크를 확인하셨나요?"
        } else {
            "내리기 전, 트렁크를 확인하셨나요?"
        }

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "WHAT'S IN MY TRUNK?",
            headline = headline,
            bodyMessage = "'${session.destinationName}'에 도착해 P단 주차가 완료되었습니다.$routineNote\n" +
                "이번 이동 중 $poiText 트렁크를 사용한 이력이 있습니다. $tagNote",
            contextReasonBadge = reasonBadge,
            signalOrigin = latestRecord?.origin ?: targetItemsForCurrentStop.firstOrNull()?.origin ?: SignalOrigin.AI_AGENT_INFERRED,
            voiceTtsMessage = "${session.destinationName}에 도착했습니다. 내리기 전에 $poiText 트렁크에 실으신 짐을 확인해 주세요.",
            primaryAckLabel = "확인할게요",
            availableControls = listOf(
                VehicleControlCommand.OPEN_TRUNK,
                VehicleControlCommand.KEEP_IN_TRUNK_PERMANENTLY
            )
        )
    }

    companion object {
        const val ID = "trunk_reminder_mvp"
    }
}
