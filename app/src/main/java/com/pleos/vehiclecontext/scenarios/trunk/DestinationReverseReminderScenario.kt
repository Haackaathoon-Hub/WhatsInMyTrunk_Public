package com.pleos.vehiclecontext.scenarios.trunk

import com.pleos.vehiclecontext.core.engine.ScenarioPlugin
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.ContextDomain
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.model.VehicleSignal

/**
 * [확장 시나리오 / Task 2-1] 도착지 기반 상시 보관품 역제안 플러그인
 *
 * 트렁크에 상시 보관 물품(장바구니, 캠핑용품 등)이 등록되어 있을 때,
 * 마트나 레저/캠핑 목적지에 도착해 P단에 주차하면
 * 트렁크에 보관 중인 물품을 꺼내 활용하도록 역방향 리마인드를 제공합니다.
 */
class DestinationReverseReminderScenario : ScenarioPlugin {
    override val id: String = ID
    override val name: String = "도착지 기반 상시 보관품 역제안"
    override val description: String = "목적지 도착 시 트렁크에 상시 보관 중인 물품(장바구니, 레저용품) 하차 안내"
    override val priority: Int = 15
    override val isMvpCore: Boolean = false
    override val domain: ContextDomain = ContextDomain.CARGO

    override fun evaluate(session: TripSession, latestSignal: VehicleSignal): ContextAction? {
        val permanentItemsFromInventory = session.cargoInventory.filter { it.isPermanent }.map { it.name }
        val allPermanentNotes = (session.permanentTrunkNotes + permanentItemsFromInventory).distinct()

        if (allPermanentNotes.isEmpty()) return null
        if (!session.isArrivedAtDestination) return null
        if (session.currentGear != GearState.PARK) return null
        if (session.destinationPoi != PoiCategory.MART_SHOPPING && session.destinationPoi != PoiCategory.LEISURE_CAMPING) {
            return null
        }

        val items = allPermanentNotes.joinToString(", ")

        return ContextAction(
            scenarioId = id,
            scenarioTitle = "TRUNK INVENTORY REMINDER",
            headline = "트렁크 상시 보관 물품을 챙기셨나요?",
            bodyMessage = "'${session.destinationName}'에 도착했습니다. 트렁크에 보관 중인 [${items}]을(를) 꺼내 활용해 보세요!",
            contextReasonBadge = "상시 보관(${items}) + 목적지 도착(${session.destinationPoi.displayName}) + P단",
            signalOrigin = SignalOrigin.AI_AGENT_INFERRED,
            voiceTtsMessage = "${session.destinationName}에 도착했습니다. 트렁크에 보관 중인 ${items}을 챙겨 내리세요.",
            primaryAckLabel = "확인할게요",
            availableControls = listOf(VehicleControlCommand.OPEN_TRUNK)
        )
    }

    companion object {
        const val ID = "destination_reverse_reminder"
    }
}
