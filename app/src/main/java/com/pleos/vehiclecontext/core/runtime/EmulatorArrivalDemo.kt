package com.pleos.vehiclecontext.core.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.TripSession
import com.pleos.vehiclecontext.core.signal.RouteTarget
import com.pleos.vehiclecontext.core.voice.samePlaceName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal fun canRunEmulatorDemo(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
        (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator"))

/**
 * 디버그 에뮬레이터 환경에서 NaviHelper 경로 기반 도착 시연 GPS 주입을 조율합니다.
 */
internal class EmulatorArrivalDemo(
    scope: CoroutineScope,
    val available: Boolean,
    private val target: StateFlow<RouteTarget?>,
    private val session: StateFlow<TripSession>,
    private val requestFix: (String, RouteTarget) -> Unit,
    private val simulateArrival: (RouteTarget) -> Unit
) {
    private data class Request(
        val id: String,
        val route: RouteTarget,
        val result: CompletableDeferred<Boolean>,
        var claimed: Boolean = false
    )

    private var pending: Request? = null
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()
    private val _status = MutableStateFlow("에뮬레이터 도착 시연 꺼짐")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        if (available) {
            scope.launch {
                combine(_armed, target, session) { enabled, route, trip ->
                    val driving = enabled && trip.currentGear == GearState.DRIVE
                    Pair(route.takeIf { driving }, trip.destinationName.takeIf { driving && trip.isArrivedAtDestination })
                }.distinctUntilChanged().collectLatest { (route, actualArrival) ->
                    if (!_armed.value) return@collectLatest
                    if (actualArrival != null) {
                        _status.value = "$actualArrival · 내비 도착 수신 · P단에서 하차 안내"
                        _armed.value = false
                        return@collectLatest
                    }
                    if (route == null) {
                        _status.value = "기본 내비 경로를 시작하고 D단으로 10초간 유지하세요."
                        return@collectLatest
                    }
                    _status.value = "${route.name} · D단 10초 확인 중"
                    delay(10_000)
                    val request = Request(UUID.randomUUID().toString(), route, CompletableDeferred())
                    pending = request
                    try {
                        _status.value = "에뮬레이터에 목적지 GPS 전송 중"
                        requestFix(request.id, route)
                        if (withTimeoutOrNull(8_000) { request.result.await() } != true) {
                            _status.value = "GPS 전송 실패 · 콘솔 연결 상태를 확인해 주세요."
                            _armed.value = false
                            return@collectLatest
                        }
                        delay(2_000)
                        val cur = session.value
                        if (target.value != route || cur.currentGear != GearState.DRIVE) {
                            return@collectLatest
                        }
                        if (!cur.isArrivedAtDestination || !samePlaceName(cur.destinationName, route.name)) {
                            simulateArrival(route)
                        }
                        _status.value = "${route.name} 도착 시연 완료 · P단으로 전환하세요."
                        _armed.value = false
                    } finally {
                        if (pending === request) pending = null
                    }
                }
            }
        }
    }

    fun setArmed(value: Boolean) {
        if (!available || session.value.currentGear != GearState.PARK) return
        _status.value = if (value) "내비 경로를 시작하고 D단으로 전환하세요." else "에뮬레이터 도착 시연 꺼짐"
        _armed.value = value
    }

    fun claim(id: String): Boolean {
        val request = pending ?: return false
        if (!available || !_armed.value || request.id != id || request.claimed ||
            target.value != request.route || session.value.currentGear != GearState.DRIVE
        ) {
            return false
        }
        request.claimed = true
        return true
    }

    fun complete(id: String, success: Boolean): Boolean {
        val request = pending ?: return false
        return request.id == id && request.claimed && request.result.complete(success)
    }
}
