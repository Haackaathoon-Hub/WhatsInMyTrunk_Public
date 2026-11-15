package com.pleos.vehiclecontext.gleo

import android.os.SystemClock
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.TripSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 선택적 데모 AVD / Gleo 음성 핸드오프 조정기.
 */
internal class GleoDemoHandoff(private val nowMs: () -> Long) {
    private data class Offer(val requestId: String, val expiresAtMs: Long)

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()
    private var reservedId: String? = null
    private var offer: Offer? = null

    fun arm() {
        _armed.value = true
    }

    fun eligibleRequest(session: TripSession, vehicleMode: Boolean, hasDraft: Boolean): String? {
        if (!_armed.value ||
            !vehicleMode ||
            hasDraft ||
            session.currentGear != GearState.PARK ||
            DoorZone.TRUNK in session.openDoors ||
            session.unloadConfirmationNeeded
        ) {
            return null
        }
        return session.autoVoiceRegistrationId?.takeIf { it == session.pendingLoadingPromptRequestId }
    }

    fun reserve(engine: TripContextEngine, requestId: String): Boolean {
        if (!_armed.value || !engine.claimAutoVoiceRegistration(requestId)) return false
        reservedId = requestId
        offer = null
        return true
    }

    fun ready(requestId: String): Boolean {
        if (!_armed.value || reservedId != requestId) return false
        offer = Offer(requestId, nowMs() + CLAIM_WINDOW_MS)
        return true
    }

    fun claim(requestId: String, eligibleId: String?): Boolean {
        val active = offer ?: return false
        if (!_armed.value ||
            active.requestId != requestId ||
            eligibleId != requestId ||
            nowMs() >= active.expiresAtMs
        ) {
            return false
        }
        offer = null
        return true
    }

    fun cancelOffer() {
        offer = null
    }

    fun expireUnclaimed(engine: TripContextEngine, requestId: String) {
        if (offer?.requestId == requestId) disarm(engine)
    }

    fun disarm(engine: TripContextEngine) {
        reservedId?.let(engine::releaseAutoVoiceRegistration)
        reservedId = null
        offer = null
        _armed.value = false
    }

    companion object {
        const val CLAIM_WINDOW_MS = 6_000L
    }
}

internal val gleoDemoHandoff = GleoDemoHandoff(SystemClock::elapsedRealtime)
