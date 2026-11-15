package com.pleos.vehiclecontext.core.engine

import android.content.SharedPreferences
import com.pleos.vehiclecontext.core.model.AiCargoMemory
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.AutonomousDrivingProfile
import com.pleos.vehiclecontext.core.model.CargoItemRecord
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ClosureUsageRecord
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.TripPhase
import com.pleos.vehiclecontext.core.model.TripSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * 리더 기획안 슬라이드 7: "앱 재시작 - 현재 이동의 안내 상태 복원"
 * 현재 TripSession의 개폐 이력, 자율주행 모드, AI 적재 메모리, 목적지 도착 여부,
 * 이미 발화된 안내 ID, 사용자 확인 여부를 영속화하고 앱 프로세스 재시작 시 즉시 복원합니다.
 */
interface SessionPersistence {
    fun save(session: TripSession)
    fun restore(): TripSession?
    fun clear()
}

/**
 * 테스트 및 기본 메모리 백업용 구현체
 */
class InMemorySessionPersistence : SessionPersistence {
    private var snapshot: TripSession? = null

    override fun save(session: TripSession) {
        snapshot = session
    }

    override fun restore(): TripSession? = snapshot

    override fun clear() {
        snapshot = null
    }
}

/**
 * Android SharedPreferences 기반 영속화 구현체 (외부 라이브러리 의존성 없이 JSON 직렬화)
 */
class SharedPrefsSessionPersistence(
    private val prefs: SharedPreferences
) : SessionPersistence {

    override fun save(session: TripSession) {
        val root = JSONObject().apply {
            put("sessionId", session.sessionId)
            put("phase", session.phase.name)
            put("currentGear", session.currentGear.name)
            put("autonomousMode", session.autonomousMode.name)
            put("autonomousProfile", session.autonomousProfile.name)
            put("isAvpDepartureHoldActive", session.isAvpDepartureHoldActive)
            put("destinationName", session.destinationName)
            put("destinationPoi", session.destinationPoi.name)
            put("destinationAddress", session.destinationAddress)
            put("originName", session.originName)
            put("originAddress", session.originAddress)
            put("originPoi", session.originPoi.name)
            put("originSourceLabel", session.originSourceLabel)
            put("isArrivedAtDestination", session.isArrivedAtDestination)
            put("hasReversedDuringDrive", session.hasReversedDuringDrive)
            put("hasExplicitIntermediateStop", session.hasExplicitIntermediateStop)
            put("rearSeatOccupied", session.rearSeatOccupied)
            put("openWindowsCount", session.openWindowsCount)
            put("cabinTempCelsius", session.cabinTempCelsius.toDouble())
            put("harshDrivingCount", session.harshDrivingCount)
            put("promptedIds", JSONArray(session.promptedScenarioIdsForCurrentArrival.toList()))
            put("ackedIds", JSONArray(session.acknowledgedScenarioIds.toList()))
            put("permanentNotes", JSONArray(session.permanentTrunkNotes.toList()))
            session.pendingLoadingPromptRequestId?.let { put("pendingLoadingPromptRequestId", it) }
            put("unloadConfirmationNeeded", session.unloadConfirmationNeeded)

            session.aiCargoMemory?.let { memory ->
                put(
                    "aiCargoMemory",
                    JSONObject().apply {
                        put("rawUserUtterance", memory.rawUserUtterance)
                        put("inferredItems", JSONArray(memory.inferredItems))
                        put("inferredPrimaryTag", memory.inferredPrimaryTag.name)
                        put("isTemperatureSensitive", memory.isTemperatureSensitive)
                        put("requiresSoftAutonomousProfile", memory.requiresSoftAutonomousProfile)
                        put("aiBriefingSummary", memory.aiBriefingSummary)
                    }
                )
            }

            val historyArray = JSONArray()
            session.closureHistory.forEach { record ->
                historyArray.put(
                    JSONObject().apply {
                        put("zone", record.zone.name)
                        put("openedAtPoi", record.openedAtPoi.name)
                        put("cargoTag", record.cargoTag.name)
                        put("origin", record.origin.name)
                        put("closedAtTimestampMs", record.closedAtTimestampMs)
                    }
                )
            }
            put("closureHistory", historyArray)

            val inventoryArray = JSONArray()
            session.cargoInventory.forEach { item ->
                inventoryArray.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("zone", item.zone.name)
                        put("categoryTag", item.categoryTag.name)
                        put("loadedPoi", item.loadedPoi.name)
                        item.originName?.let { put("originName", it) }
                        item.targetDestinationPoi?.let { put("targetDestinationPoi", it.name) }
                        item.targetDestinationName?.let { put("targetDestinationName", it) }
                        item.inferredDestinationPoi?.let { put("inferredDestinationPoi", it.name) }
                        put("destinationConfidence", item.destinationConfidence.toDouble())
                        put("isDestinationAutoInferred", item.isDestinationAutoInferred)
                        put("loadedTimestampMs", item.loadedTimestampMs)
                        put("origin", item.origin.name)
                        put("isPerishable", item.isPerishable)
                        put("isPermanent", item.isPermanent)
                        put("isUnloaded", item.isUnloaded)
                        put("quantity", item.quantity)
                        put("unloadedQuantity", item.unloadedQuantity)
                        put("purpose", item.purpose)
                        put("countAsBundle", item.countAsBundle)
                    }
                )
            }
            put("cargoInventory", inventoryArray)
        }
        prefs.edit().putString(KEY_SESSION_JSON, root.toString()).apply()
    }

    override fun restore(): TripSession? {
        val raw = prefs.getString(KEY_SESSION_JSON, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val historyArray = root.optJSONArray("closureHistory") ?: JSONArray()
            val history = buildList {
                for (i in 0 until historyArray.length()) {
                    val item = historyArray.getJSONObject(i)
                    add(
                        ClosureUsageRecord(
                            zone = DoorZone.valueOf(item.getString("zone")),
                            openedAtPoi = PoiCategory.valueOf(item.getString("openedAtPoi")),
                            cargoTag = CargoTag.valueOf(item.getString("cargoTag")),
                            origin = SignalOrigin.valueOf(item.getString("origin")),
                            closedAtTimestampMs = item.getLong("closedAtTimestampMs")
                        )
                    )
                }
            }

            val inventoryArray = root.optJSONArray("cargoInventory") ?: JSONArray()
            val inventory = buildList {
                for (i in 0 until inventoryArray.length()) {
                    runCatching {
                        val item = inventoryArray.getJSONObject(i)
                        val qty = item.optInt("quantity", 1).coerceIn(1, 999)
                        val unloadedQty = item.optInt("unloadedQuantity", 0).coerceIn(0, qty)
                        add(
                            CargoItemRecord(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                zone = DoorZone.valueOf(item.optString("zone", DoorZone.TRUNK.name)),
                                categoryTag = CargoTag.valueOf(item.optString("categoryTag", CargoTag.GENERAL.name)),
                                loadedPoi = PoiCategory.valueOf(item.optString("loadedPoi", PoiCategory.UNKNOWN.name)),
                                originName = item.optString("originName")
                                    .takeIf { it.isNotBlank() && it != "null" },
                                targetDestinationPoi = item.optString("targetDestinationPoi")
                                    .takeIf { it.isNotBlank() && it != "null" }
                                    ?.let { runCatching { PoiCategory.valueOf(it) }.getOrNull() },
                                targetDestinationName = item.optString("targetDestinationName")
                                    .takeIf { it.isNotBlank() && it != "null" },
                                inferredDestinationPoi = item.optString("inferredDestinationPoi")
                                    .takeIf { it.isNotBlank() && it != "null" }
                                    ?.let { runCatching { PoiCategory.valueOf(it) }.getOrNull() },
                                destinationConfidence = item.optDouble("destinationConfidence", 1.0).toFloat(),
                                isDestinationAutoInferred = item.optBoolean("isDestinationAutoInferred", false),
                                loadedTimestampMs = item.optLong("loadedTimestampMs", System.currentTimeMillis()),
                                origin = SignalOrigin.valueOf(item.optString("origin", SignalOrigin.REAL_SDK.name)),
                                isPerishable = item.optBoolean("isPerishable", false),
                                isPermanent = item.optBoolean("isPermanent", false),
                                isUnloaded = item.optBoolean("isUnloaded", false),
                                quantity = qty,
                                unloadedQuantity = unloadedQty,
                                purpose = item.optString("purpose", ""),
                                countAsBundle = item.optBoolean("countAsBundle", false)
                            )
                        )
                    }
                }
            }

            val aiMemoryObj = root.optJSONObject("aiCargoMemory")
            val aiMemory = aiMemoryObj?.let {
                AiCargoMemory(
                    rawUserUtterance = it.getString("rawUserUtterance"),
                    inferredItems = it.optJSONArray("inferredItems").toStringSet().toList(),
                    inferredPrimaryTag = CargoTag.valueOf(it.getString("inferredPrimaryTag")),
                    isTemperatureSensitive = it.getBoolean("isTemperatureSensitive"),
                    requiresSoftAutonomousProfile = it.getBoolean("requiresSoftAutonomousProfile"),
                    aiBriefingSummary = it.getString("aiBriefingSummary")
                )
            }

            TripSession(
                sessionId = root.getString("sessionId"),
                phase = TripPhase.valueOf(root.getString("phase")),
                currentGear = GearState.valueOf(root.getString("currentGear")),
                autonomousMode = AutonomousDrivingMode.valueOf(
                    root.optString("autonomousMode", AutonomousDrivingMode.MANUAL.name)
                ),
                autonomousProfile = AutonomousDrivingProfile.valueOf(
                    root.optString("autonomousProfile", AutonomousDrivingProfile.NORMAL.name)
                ),
                isAvpDepartureHoldActive = root.optBoolean("isAvpDepartureHoldActive", false),
                destinationName = root.getString("destinationName"),
                destinationPoi = PoiCategory.valueOf(root.getString("destinationPoi")),
                destinationAddress = root.optString("destinationAddress", ""),
                originName = root.optString("originName", ""),
                originAddress = root.optString("originAddress", ""),
                originPoi = runCatching {
                    PoiCategory.valueOf(root.optString("originPoi", PoiCategory.UNKNOWN.name))
                }.getOrDefault(PoiCategory.UNKNOWN),
                originSourceLabel = root.optString("originSourceLabel", "GPS 위치 확인 중"),
                isArrivedAtDestination = root.getBoolean("isArrivedAtDestination"),
                hasReversedDuringDrive = root.optBoolean("hasReversedDuringDrive", false),
                hasExplicitIntermediateStop = root.optBoolean("hasExplicitIntermediateStop", false),
                closureHistory = history,
                cargoInventory = inventory,
                aiCargoMemory = aiMemory,
                rearSeatOccupied = root.optBoolean("rearSeatOccupied", false),
                openWindowsCount = root.optInt("openWindowsCount", 0),
                cabinTempCelsius = root.optDouble("cabinTempCelsius", 22.0).toFloat(),
                harshDrivingCount = root.optInt("harshDrivingCount", 0),
                promptedScenarioIdsForCurrentArrival = root.optJSONArray("promptedIds").toStringSet(),
                acknowledgedScenarioIds = root.optJSONArray("ackedIds").toStringSet(),
                permanentTrunkNotes = root.optJSONArray("permanentNotes").toStringSet(),
                pendingLoadingPromptRequestId = root.optString("pendingLoadingPromptRequestId")
                    .takeIf { it.isNotBlank() && it != "null" },
                // Restored sessions never auto-open the microphone on cold restart.
                autoVoiceRegistrationId = null,
                unloadConfirmationNeeded = root.optBoolean("unloadConfirmationNeeded", false)
            )
        }.getOrNull()
    }

    override fun clear() {
        prefs.edit().remove(KEY_SESSION_JSON).apply()
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0 until length()) {
            result.add(getString(i))
        }
        return result
    }

    companion object {
        private const val KEY_SESSION_JSON = "active_trip_session_v2"
    }
}
