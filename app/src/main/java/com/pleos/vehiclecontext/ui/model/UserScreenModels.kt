package com.pleos.vehiclecontext.ui.model

import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.TripSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AAOS 탑 내비게이션 바 모드 전환 탭
 */
enum class NavigationTab(val label: String, val icon: String) {
    GENERAL_USER("운전자 모드", "🚗"),
    DEVELOPER_CONSOLE("개발자 콘솔", "🛠️")
}

/**
 * 운전자 모드(General User Mode) 멀티 Depth 서브 세그먼트 탭:
 * - DASHBOARD: 운전자 핵심 대시보드 (Hero 스마트 하차 알림 + 퀵 화물 등록)
 * - CARGO_LIST: 트렁크 보관함 상세 (적재 인벤토리 및 관리)
 * - FRESHNESS: 신선식품 케어 상세 (실시간 온도 모니터링 & 공조 제어)
 * - TIMELINE: 오늘 여정 기록 상세 (시계열 센서 및 인과관계 로그)
 */
enum class UserSubTab(val label: String, val icon: String) {
    DASHBOARD("대시보드", "🏠"),
    CARGO_LIST("트렁크 보관함", "📦"),
    FRESHNESS("신선 케어", "❄️"),
    TIMELINE("여정 기록", "⏱️")
}

/**
 * 신선/냉동 식재료 골든타임 위험도 레벨
 */
enum class FreshnessRiskLevel(val label: String) {
    SAFE("안전"),
    WARNING("주의"),
    CRITICAL("위험")
}

/**
 * 운전자 화면 트렁크 적재 현황(Widget 1) 바인딩 모델
 */
data class CargoItemPresentation(
    val id: String,
    val name: String,
    val categoryBadge: String,
    val icon: String,
    val locationNote: String,
    val loadTimeFormatted: String,
    val isPerishable: Boolean,
    val itemTitle: String = name,
    val quantityLabel: String = "",
    val destinationLabel: String = "",
    val purposeLabel: String = "",
    val originLabel: String = "",
    val isPermanent: Boolean = false
)

/**
 * 신선/냉동 스마트 케어(Widget 2) 바인딩 모델
 */
data class FreshnessCareStatus(
    val hasPerishableCargo: Boolean,
    val itemName: String,
    val currentTempCelsius: Float,
    val elapsedMinutes: Int,
    val limitMinutes: Int,
    val remainingMinutes: Int,
    val progress: Float,
    val riskLevel: FreshnessRiskLevel
) {
    val elapsedProgress: Float get() = (1f - progress).coerceIn(0f, 1f)
}

/**
 * TripSession 도메인 모델을 운전자 친화적 Presentation 모델로 변환하는 매퍼
 */
object UserScreenDataMapper {

    fun toCargoItemList(session: TripSession): List<CargoItemPresentation> {
        val result = mutableListOf<CargoItemPresentation>()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val defaultLoadTime = session.latestTrunkUsage?.closedAtTimestampMs?.let {
            timeFormat.format(Date(it))
        } ?: "방금 전"

        val activeInventory = session.cargoInventory.filter {
            !it.isUnloaded && !it.isPermanent && it.remainingQuantity > 0
        }
        // 1. 통합 적재 인벤토리(cargoInventory) 우선 반영 (수량/용도/내릴장소/부분하차 실시간 반영)
        if (activeInventory.isNotEmpty()) {
            activeInventory.forEachIndexed { index, record ->
                val formattedTime = timeFormat.format(Date(record.loadedTimestampMs))
                val (icon, badge, isPerishable) = parseItemAttributes(record.name, record.isPerishable)
                val baseName = if (record.name.contains("유모차")) "유모차" else record.name
                val displayName = if (record.countAsBundle || record.remainingQuantity > 1) {
                    "$baseName (${record.amountLabel()})"
                } else {
                    baseName
                }
                val placePart = when {
                    !record.originName.isNullOrBlank() && !record.targetDestinationName.isNullOrBlank() ->
                        " · ${record.originName} → ${record.targetDestinationName}"
                    !record.targetDestinationName.isNullOrBlank() ->
                        " · ${record.targetDestinationName}"
                    !record.originName.isNullOrBlank() ->
                        " · 출발: ${record.originName}"
                    else -> ""
                }
                val purposePart = record.purpose.takeIf { it.isNotBlank() } ?: "스마트 적재"
                val destText = when {
                    record.isPermanent -> "상시 보관"
                    !record.targetDestinationName.isNullOrBlank() -> record.targetDestinationName
                    else -> "목적지 미정"
                }
                result.add(
                    CargoItemPresentation(
                        id = record.id.ifEmpty { "cargo-inv-$index" },
                        name = displayName,
                        categoryBadge = badge,
                        icon = icon,
                        locationNote = "트렁크$placePart ($purposePart)",
                        loadTimeFormatted = formattedTime,
                        isPerishable = isPerishable,
                        itemTitle = baseName,
                        quantityLabel = record.amountLabel(),
                        destinationLabel = destText,
                        purposeLabel = record.purpose.takeIf { it.isNotBlank() }.orEmpty(),
                        originLabel = record.originName.orEmpty().trim(),
                        isPermanent = record.isPermanent
                    )
                )
            }
        } else if (session.aiCargoMemory != null && session.aiCargoMemory.inferredItems.isNotEmpty()) {
            // 2. 레거시/단독 AI 메모리 폴백
            session.aiCargoMemory.inferredItems.forEachIndexed { index, rawItem ->
                val cleanName = rawItem.substringBefore(" (").trim()
                val (icon, badge, isPerishable) = parseItemAttributes(cleanName, session.aiCargoMemory.isTemperatureSensitive)
                val displayName = if (cleanName.contains("유모차")) "유모차" else cleanName
                result.add(
                    CargoItemPresentation(
                        id = "ai-item-$index",
                        name = displayName,
                        categoryBadge = badge,
                        icon = icon,
                        locationNote = "트렁크 (스마트 적재)",
                        loadTimeFormatted = defaultLoadTime,
                        isPerishable = isPerishable,
                        itemTitle = displayName,
                        quantityLabel = "1건",
                        destinationLabel = session.destinationName.ifBlank { "목적지 미정" },
                        purposeLabel = "AI 적재 인식",
                        originLabel = session.originName.trim()
                    )
                )
            }
        } else {
            // 3. 센서 트렁크 개폐 이력 기반 적재 화물 변환
            val trunkRecords = session.closureHistory.filter {
                it.zone == DoorZone.TRUNK && it.cargoTag != CargoTag.PERMANENT_KEEP
            }
            trunkRecords.forEachIndexed { index, record ->
                val formattedTime = timeFormat.format(Date(record.closedAtTimestampMs))
                when (record.cargoTag) {
                    CargoTag.FRESH_FOOD -> {
                        result.add(
                            CargoItemPresentation(
                                id = "trunk-fresh-$index",
                                name = "신선/냉동 식재료",
                                categoryBadge = "신선식품",
                                icon = "🥩",
                                locationNote = "트렁크 (온도 감시)",
                                loadTimeFormatted = formattedTime,
                                isPerishable = true,
                                itemTitle = "신선/냉동 식재료",
                                quantityLabel = "1건",
                                destinationLabel = session.destinationName.ifBlank { "목적지 미정" },
                                purposeLabel = "온도 감시"
                            )
                        )
                    }
                    CargoTag.FRAGILE -> {
                        result.add(
                            CargoItemPresentation(
                                id = "trunk-fragile-$index",
                                name = "파손주의 물품",
                                categoryBadge = "파손주의",
                                icon = "📦",
                                locationNote = "트렁크 (완충 적재)",
                                loadTimeFormatted = formattedTime,
                                isPerishable = false,
                                itemTitle = "파손주의 물품",
                                quantityLabel = "1건",
                                destinationLabel = session.destinationName.ifBlank { "목적지 미정" },
                                purposeLabel = "완충 적재"
                            )
                        )
                    }
                    else -> {
                        result.add(
                            CargoItemPresentation(
                                id = "trunk-gen-$index",
                                name = "일반 적재 화물",
                                categoryBadge = "일반화물",
                                icon = "🧳",
                                locationNote = "트렁크",
                                loadTimeFormatted = formattedTime,
                                isPerishable = false,
                                itemTitle = "일반 적재 화물",
                                quantityLabel = "1건",
                                destinationLabel = session.destinationName.ifBlank { "목적지 미정" },
                                purposeLabel = "일반 적재"
                            )
                        )
                    }
                }
            }
        }

        // 4. 상시 보관 물품 (permanentTrunkNotes + cargoInventory.isPermanent) 병합
        val allPermanentNotes = (
            session.permanentTrunkNotes +
                session.cargoInventory.filter { it.isPermanent && !it.isUnloaded }.map { it.name }
            ).distinct()
        allPermanentNotes.forEach { note ->
            if (result.none { it.name.contains(note) }) {
                val icon = when {
                    note.contains("골프") -> "⛳"
                    note.contains("장바구니") -> "🛒"
                    note.contains("우산") -> "☂️"
                    note.contains("캠핑") -> "🪑"
                    note.contains("세차") -> "🧴"
                    note.contains("유모차") -> "👶"
                    else -> "📦"
                }
                val badge = when {
                    note.contains("골프") -> "스포츠/레저"
                    note.contains("유모차") -> "유아용품"
                    else -> "상시보관"
                }
                result.add(
                    CargoItemPresentation(
                        id = "perm-$note",
                        name = note,
                        categoryBadge = badge,
                        icon = icon,
                        locationNote = "트렁크 상시 보관",
                        loadTimeFormatted = "상시",
                        isPerishable = false,
                        itemTitle = note,
                        quantityLabel = "1건",
                        destinationLabel = "차량 상시 보관",
                        purposeLabel = "고정 비치",
                        isPermanent = true
                    )
                )
            }
        }

        return result
    }

    fun resolveEditableRecord(
        session: TripSession,
        item: CargoItemPresentation
    ): com.pleos.vehiclecontext.core.model.CargoItemRecord {
        session.cargoInventory.firstOrNull { it.id == item.id }?.let { return it }
        return com.pleos.vehiclecontext.core.model.CargoItemRecord(
            id = item.id,
            name = item.itemTitle.ifBlank { item.name },
            zone = DoorZone.TRUNK,
            categoryTag = when {
                item.isPermanent -> CargoTag.PERMANENT_KEEP
                item.isPerishable -> CargoTag.FRESH_FOOD
                item.categoryBadge == "파손주의" -> CargoTag.FRAGILE
                else -> CargoTag.GENERAL
            },
            loadedPoi = session.originPoi,
            originName = item.originLabel.ifBlank { session.originName.takeIf { it.isNotBlank() } },
            targetDestinationPoi = session.destinationPoi,
            targetDestinationName = if (item.isPermanent) {
                null
            } else {
                item.destinationLabel.takeIf {
                    it.isNotBlank() && it != "목적지 미정" && it != "차량 상시 보관" && it != "상시 보관"
                } ?: session.destinationName
            },
            isPerishable = item.isPerishable,
            isPermanent = item.isPermanent,
            quantity = 1,
            purpose = item.purposeLabel
        )
    }

    private fun parseItemAttributes(cleanName: String, isSessionTempSensitive: Boolean): Triple<String, String, Boolean> {
        return when {
            cleanName.contains("아이스크림") || cleanName.contains("냉동") -> Triple("🍦", "신선식품", true)
            cleanName.contains("신선") || cleanName.contains("우유") || cleanName.contains("고기") ||
                cleanName.contains("생선") || cleanName.contains("정육") || cleanName.contains("식재료") -> Triple("🥩", "신선식품", true)
            cleanName.contains("장바구니") -> Triple("🛒", "신선식품", isSessionTempSensitive)
            cleanName.contains("케이크") -> Triple("🎂", "파손주의", false)
            cleanName.contains("와인") -> Triple("🍷", "파손주의", false)
            cleanName.contains("유리") || cleanName.contains("계란") || cleanName.contains("도자기") -> Triple("📦", "파손주의", false)
            cleanName.contains("유모차") -> Triple("👶", "유아용품", false)
            cleanName.contains("골프") -> Triple("⛳", "스포츠/레저", false)
            cleanName.contains("캠핑") -> Triple("🪑", "레저/아웃도어", false)
            cleanName.contains("우산") -> Triple("☂️", "상시보관", false)
            cleanName.contains("세차") -> Triple("🧴", "차량용품", false)
            isSessionTempSensitive -> Triple("🥩", "신선식품", true)
            else -> Triple("📦", "일반화물", false)
        }
    }

    fun toFreshnessCareStatus(
        session: TripSession,
        currentTimeMs: Long = System.currentTimeMillis()
    ): FreshnessCareStatus {
        val hasPerishableCargo = session.cargoInventory.any { it.isPerishable && !it.isUnloaded } ||
            session.aiCargoMemory?.isTemperatureSensitive == true ||
            session.latestTrunkUsage?.cargoTag == CargoTag.FRESH_FOOD ||
            session.closureHistory.any { it.zone == DoorZone.TRUNK && it.cargoTag == CargoTag.FRESH_FOOD }

        if (!hasPerishableCargo) {
            return FreshnessCareStatus(
                hasPerishableCargo = false,
                itemName = "신선/냉동 물품 없음",
                currentTempCelsius = session.cabinTempCelsius,
                elapsedMinutes = 0,
                limitMinutes = 60,
                remainingMinutes = 60,
                progress = 1.0f,
                riskLevel = FreshnessRiskLevel.SAFE
            )
        }

        val itemName = session.cargoInventory.firstOrNull { it.isPerishable && !it.isUnloaded }?.name
            ?: session.aiCargoMemory?.inferredItems?.firstOrNull { raw ->
                raw.contains("아이스크림") || raw.contains("냉동") || raw.contains("신선") ||
                    raw.contains("우유") || raw.contains("생선") || raw.contains("고기") ||
                    raw.contains("정육") || raw.contains("식재료")
            }?.substringBefore(" (")?.trim() ?: "신선/냉동 식재료"

        val freshClosure = session.closureHistory.lastOrNull {
            it.zone == DoorZone.TRUNK && it.cargoTag == CargoTag.FRESH_FOOD
        } ?: session.closureHistory.lastOrNull { it.zone == DoorZone.TRUNK }

        val loadTimeMs = session.cargoInventory.lastOrNull { it.isPerishable && !it.isUnloaded }?.loadedTimestampMs
            ?: freshClosure?.closedAtTimestampMs
            ?: currentTimeMs
        val elapsedMinutes = ((currentTimeMs - loadTimeMs).coerceAtLeast(0L) / 60_000L).toInt()

        val limitMinutes = when {
            session.cabinTempCelsius >= 32.0f -> 15
            session.cabinTempCelsius >= 28.0f -> 30
            session.cabinTempCelsius >= 25.0f -> 45
            session.cabinTempCelsius >= 20.0f -> 60
            else -> 90
        }

        val remainingMinutes = (limitMinutes - elapsedMinutes).coerceAtLeast(0)
        val progress = if (limitMinutes > 0) {
            (remainingMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        } else {
            0.0f
        }

        val riskLevel = when {
            remainingMinutes <= 5 || session.cabinTempCelsius >= 32.0f -> FreshnessRiskLevel.CRITICAL
            remainingMinutes <= limitMinutes / 2 || session.cabinTempCelsius >= 26.0f -> FreshnessRiskLevel.WARNING
            else -> FreshnessRiskLevel.SAFE
        }

        return FreshnessCareStatus(
            hasPerishableCargo = true,
            itemName = itemName,
            currentTempCelsius = session.cabinTempCelsius,
            elapsedMinutes = elapsedMinutes,
            limitMinutes = limitMinutes,
            remainingMinutes = remainingMinutes,
            progress = progress,
            riskLevel = riskLevel
        )
    }
}
