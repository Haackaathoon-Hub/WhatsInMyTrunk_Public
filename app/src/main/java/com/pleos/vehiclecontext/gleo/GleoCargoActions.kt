package com.pleos.vehiclecontext.gleo

import com.pleos.vehiclecontext.core.engine.CargoActionResult
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.CargoItemRecord
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.settings.AppSettings
import com.pleos.vehiclecontext.core.settings.isShoppingContext
import com.pleos.vehiclecontext.core.voice.samePlaceName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

sealed interface GleoCargoChange {
    data class Register(
        val name: String,
        val quantity: Int,
        val purpose: String,
        val place: String,
        val keep: Boolean
    ) : GleoCargoChange

    data class Unload(val item: CargoItemRecord, val quantity: Int) : GleoCargoChange
    data class Move(val item: CargoItemRecord, val place: String) : GleoCargoChange
    data class Keep(val item: CargoItemRecord) : GleoCargoChange
}

data class GleoDraft internal constructor(
    val id: String,
    val title: String,
    val details: String,
    internal val change: GleoCargoChange,
    internal val createdAtMs: Long
)

/**
 * Pleos Gleo 음성/AppAction 연동을 단일 TripContextEngine 위에 통합합니다.
 * 브로드캐스트 수신 시 draft를 생성하고, 앱 UI에서 운전자가 1탭 확인/취소하면 엔진 상태를 갱신합니다.
 */
class GleoCargoActions(
    private val engine: TripContextEngine,
    private val settingsProvider: () -> AppSettings = { AppSettings() },
    private val isVehicleMode: () -> Boolean = { true },
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val activeNavigationDestination: () -> String? = { null }
) {
    private val _pending = MutableStateFlow<GleoDraft?>(null)
    val pending: StateFlow<GleoDraft?> = _pending.asStateFlow()

    fun handle(action: String, parameters: JSONObject): JSONObject {
        val result = when {
            !isVehicleMode() -> response("fail", "Switch the app to Vehicle mode to use Gleo.")
            action == LIST -> list(parameters)
            action !in actions -> response("fail", "This cargo action is not supported.")
            else -> {
                if (pending.value?.let(::expired) == true) clearPending()
                when (action) {
                    REGISTER -> register(parameters)
                    UPDATE -> update(parameters)
                    else -> error("Action was already checked")
                }
            }
        }
        return result.put("functionName", action).put("mode", "voice")
    }

    fun confirm(draftId: String): CargoActionResult {
        val draft = pending.value?.takeIf { it.id == draftId }
            ?: return CargoActionResult(false, "확인할 Gleo 요청이 없습니다.")
        val currentSession = engine.session.value
        if (!isVehicleMode() || currentSession.currentGear != GearState.PARK || expired(draft)) {
            clearPending()
            return CargoActionResult(false, "주차 상태에서 Gleo에 다시 요청해 주세요.")
        }
        clearPending()
        return when (val change = draft.change) {
            is GleoCargoChange.Register -> {
                val bundle = !change.keep &&
                    settingsProvider().autoShoppingBundle &&
                    isShoppingContext(change.name, change.place)
                engine.registerStructuredCargo(
                    name = change.name,
                    quantity = change.quantity,
                    purpose = change.purpose,
                    targetDestinationName = if (change.keep) null else change.place,
                    countAsBundle = bundle,
                    origin = SignalOrigin.AI_AGENT_INFERRED,
                    isPermanent = change.keep
                )
            }
            is GleoCargoChange.Unload -> withCurrentItem(change.item) {
                engine.unloadCargoItem(it.id, change.quantity)
            }
            is GleoCargoChange.Move -> withCurrentItem(change.item) {
                engine.updateCargoStorage(itemId = it.id, targetPlace = change.place, keepInCar = false)
            }
            is GleoCargoChange.Keep -> withCurrentItem(change.item) {
                engine.updateCargoStorage(itemId = it.id, targetPlace = "", keepInCar = true)
            }
        }
    }

    fun cancel(draftId: String) {
        if (pending.value?.id != draftId) return
        clearPending()
    }

    fun clearPending() {
        _pending.value = null
    }

    private fun expired(draft: GleoDraft) = clockMs() - draft.createdAtMs >= 120_000L

    private fun activeInventory(): List<CargoItemRecord> =
        engine.session.value.cargoInventory.filter { !it.isUnloaded && it.remainingQuantity > 0 }

    private inline fun withCurrentItem(
        expected: CargoItemRecord,
        apply: (CargoItemRecord) -> CargoActionResult
    ): CargoActionResult {
        val current = activeInventory().firstOrNull { it.id == expected.id }
        if (current != expected) {
            return CargoActionResult(false, "짐 기록이 달라졌습니다. Gleo에 다시 요청해 주세요.")
        }
        return apply(expected)
    }

    private fun register(p: JSONObject): JSONObject {
        val name = text(p, "cargo_name")
        val purpose = text(p, "purpose").ifBlank { "미지정" }
        val rawKeep = p.opt("keep_in_car")
        if (rawKeep != null && rawKeep != JSONObject.NULL && rawKeep !is Boolean) {
            return response("fail", "The keep-in-car option must be a boolean.")
        }
        val keep = rawKeep as? Boolean ?: false
        val place = if (keep) {
            ""
        } else {
            text(p, "target_place").ifBlank {
                activeNavigationDestination()?.trim().orEmpty()
                    .ifBlank { engine.session.value.destinationName }
            }
        }
        if (name.isBlank()) return response("asking", "What cargo would you like to register?")
        if (!p.has("quantity") || p.isNull("quantity")) {
            return response("asking", "How many items would you like to register?")
        }
        val quantity = wholeQuantity(p.opt("quantity"))
            ?: return response("fail", "Use a whole number between one and 999.")
        if (name.length > 80 || purpose.length > 120 || place.length > 160) {
            return response("fail", "Check the name, purpose, destination, and space for a new cargo record.")
        }
        val destination = if (keep) "차에 계속 보관" else "내릴 장소 ${place.ifBlank { "미정" }}"
        return stage(
            change = GleoCargoChange.Register(name, quantity, purpose, place, keep),
            title = "Gleo 짐 등록",
            details = "$name ${quantity}개 · $purpose · $destination"
        )
    }

    private fun list(p: JSONObject): JSONObject {
        val query = text(p, "query")
        val place = text(p, "target_place")
        val matches = activeInventory().filter { item ->
            (query.isBlank() || item.name.contains(query, ignoreCase = true)) &&
                (place.isBlank() || (!item.isPermanent && samePlaceName(item.targetDestinationName, place)))
        }
        val data = JSONArray().apply { matches.take(30).forEach { put(itemJson(it)) } }
        val (bundles, pieces) = matches.partition { it.countAsBundle }
        val count = if (bundles.isEmpty()) {
            "${pieces.sumOf { it.remainingQuantity }} items"
        } else {
            "${pieces.sumOf { it.remainingQuantity }} items and ${bundles.sumOf { it.remainingQuantity }} shopping bundles"
        }
        val message = when {
            matches.isEmpty() -> "No matching cargo is recorded in the trunk."
            matches.size > 30 -> "$count match. The response includes the first 30 records."
            else -> "$count are recorded in the trunk for this query."
        }
        return response("success", message, data)
    }

    private fun update(p: JSONObject): JSONObject {
        val operation = text(p, "operation").lowercase(Locale.ROOT)
        if (operation.isBlank()) {
            return response("asking", "Do you want to record unloading, change the destination, or keep the cargo in the car?")
        }
        if (operation !in setOf("unload", "change_destination", "keep_in_car")) {
            return response("fail", "This cargo update operation is not supported.")
        }
        val id = text(p, "cargo_id")
        val name = text(p, "cargo_name")
        val oldPlace = text(p, "existing_target_place")
        if (id.isBlank() && name.isBlank()) {
            return response("asking", "Which cargo would you like to update?")
        }
        val matches = activeInventory().filter { item ->
            if (id.isNotBlank()) {
                item.id == id
            } else {
                item.name.equals(name, ignoreCase = true) &&
                    (oldPlace.isBlank() || samePlaceName(item.targetDestinationName, oldPlace))
            }
        }
        if (matches.isEmpty()) return response("fail", "No matching cargo was found.")
        if (matches.size > 1) {
            return response(
                "asking",
                "Multiple cargo records match. Specify the existing destination or the cargo identifier.",
                JSONArray().apply { matches.take(30).forEach { put(itemJson(it, includePlace = true)) } }
            )
        }
        val item = matches.single()
        return when (operation) {
            "unload" -> {
                if (!p.has("quantity") || p.isNull("quantity")) {
                    return response("asking", "How many items did you unload?")
                }
                val quantity = wholeQuantity(p.opt("quantity"))
                    ?: return response("fail", "Use a whole number between one and 999.")
                if (quantity > item.remainingQuantity) {
                    return response("fail", "The quantity exceeds the number of items remaining.")
                }
                val remainAfter = (item.remainingQuantity - quantity).coerceAtLeast(0)
                stage(
                    change = GleoCargoChange.Unload(item, quantity),
                    title = "Gleo 하차 확인",
                    details = "${item.name} ${quantity}개 내리기 · 확인 후 ${remainAfter}개 남음"
                )
            }
            "change_destination" -> {
                val place = text(p, "target_place")
                if (place.isBlank()) return response("asking", "What is the new unloading destination?")
                if (place.length > 160) return response("fail", "The destination must be at most 160 characters.")
                val previous = if (item.isPermanent) "차에 계속 보관" else item.targetDestinationName ?: "미정"
                stage(
                    change = GleoCargoChange.Move(item, place),
                    title = "Gleo 내릴 장소 변경",
                    details = "${item.name} · $previous → $place"
                )
            }
            else -> stage(
                change = GleoCargoChange.Keep(item),
                title = "Gleo 상시 보관 전환",
                details = "${item.name} ${item.amountLabel()} · 도착 알림 제외"
            )
        }
    }

    private fun stage(change: GleoCargoChange, title: String, details: String): JSONObject {
        if (engine.session.value.currentGear != GearState.PARK) {
            return response("fail", "Park in P before requesting a cargo change.")
        }
        val current = pending.value
        if (current != null && current.change != change) {
            return response("asking", "Confirm or cancel the existing request in the app before starting another change.")
        }
        if (current == null) {
            _pending.value = GleoDraft(UUID.randomUUID().toString(), title, details, change, clockMs())
        }
        return response(
            "asking",
            "Review and confirm this request on the app screen to save the change.",
            JSONArray().put(JSONObject().put("summary", details))
        )
    }

    private fun text(parameters: JSONObject, key: String): String =
        (parameters.opt(key) as? String)?.trim().orEmpty()

    private fun response(status: String, message: String, data: JSONArray? = null) = JSONObject().apply {
        put("status", status)
        put("message", message)
        if (data != null) put("data", data.toString())
    }

    private fun itemJson(item: CargoItemRecord, includePlace: Boolean = false) = JSONObject().apply {
        put("cargo_id", item.id)
        put("cargo_name", item.name)
        put("remaining_quantity", item.remainingQuantity)
        if (item.countAsBundle) put("quantity_unit", "bundle")
        if (includePlace && !item.targetDestinationName.isNullOrBlank()) {
            put("target_place", item.targetDestinationName)
        }
    }

    companion object {
        const val REGISTER = "REGISTER_TRUNK_CARGO"
        const val LIST = "LIST_TRUNK_CARGO"
        const val UPDATE = "UPDATE_TRUNK_CARGO"
        val actions = setOf(REGISTER, LIST, UPDATE)

        private fun wholeQuantity(value: Any?): Int? {
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            } ?: return null
            return number.takeIf { it.isFinite() && it in 1.0..999.0 && it == it.toInt().toDouble() }?.toInt()
        }
    }
}
