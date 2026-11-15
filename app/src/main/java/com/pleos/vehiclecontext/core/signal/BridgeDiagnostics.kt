package com.pleos.vehiclecontext.core.signal

import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RouteTarget(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val revision: Long
)

data class BridgeStatus(
    val door: String = "아직 연결하지 않았습니다.",
    val gear: String = "기어 신호 대기",
    val navigation: String = "내비 신호 대기",
    val doorEventCount: Int = 0,
    val lastDoorValue: String = "미확인",
    val lastReceivedAtMs: Long = 0L,
    val events: List<String> = emptyList(),
    // 실시간 차량 센서 텔레메트리 (개발자 콘솔 라이브 모니터용)
    val gearRaw: String = "수신 대기",
    val gearSource: String = "미연결",
    val gearUpdatedAtMs: Long = 0L,
    val trunkDoorRaw: String = "미확인",
    val rearLeftDoorRaw: String = "미확인",
    val rearRightDoorRaw: String = "미확인",
    val doorBoundsLabel: String = "미확인(기본 0..100)",
    val windowOpenSummary: String = "0개 열림",
    val cabinTempCelsius: Float = 22.0f,
    val rearSeatSummary: String = "미착석",
    val cruiseRaw: String = "MANUAL",
    val naviRouteState: String = "안내 대기",
    val naviDestinationRaw: String = "미지정",
    val gpsSnapshotRaw: String = "GPS 수신 대기",
    val sensorUpdateSeq: Long = 0L,
    val lastSensorAtMs: Long = 0L,
    val sensorFeed: List<String> = emptyList()
)

class BridgeDiagnostics {
    private val _status = MutableStateFlow(BridgeStatus())
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    private fun timeTag(now: Long): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
        return fmt.format(Date(now))
    }

    @Synchronized
    fun report(domain: String, message: String, receivedDoor: Boolean = false) {
        val now = System.currentTimeMillis()
        val old = _status.value
        _status.value = old.copy(
            door = if (domain == "door") message else old.door,
            gear = if (domain == "gear") message else old.gear,
            navigation = if (domain == "navigation") message else old.navigation,
            doorEventCount = old.doorEventCount + if (receivedDoor) 1 else 0,
            lastDoorValue = if (receivedDoor) message else old.lastDoorValue,
            lastReceivedAtMs = if (receivedDoor) now else old.lastReceivedAtMs,
            events = (listOf("[$domain] $message") + old.events).take(40)
        )
    }

    @Synchronized
    fun reportGear(raw: String, source: String, mapped: GearState?) {
        val now = System.currentTimeMillis()
        val old = _status.value
        val label = if (mapped != null) "$raw -> ${mapped.label}" else "$raw (무시됨)"
        val entry = "[${timeTag(now)}] GEAR($source): $label"
        _status.value = old.copy(
            gearRaw = label,
            gearSource = source,
            gearUpdatedAtMs = now,
            sensorUpdateSeq = old.sensorUpdateSeq + 1,
            lastSensorAtMs = now,
            sensorFeed = (listOf(entry) + old.sensorFeed).take(30)
        )
    }

    @Synchronized
    fun reportDoorPosition(zone: DoorZone, pos: Int?, boundsLabel: String? = null) {
        val now = System.currentTimeMillis()
        val old = _status.value
        val valueText = if (pos == null) "null" else "pos=$pos (${if (pos > 0) "열림" else "닫힘"})"
        val entry = "[${timeTag(now)}] DOOR(${zone.displayName}): $valueText"
        val shouldAppend = old.sensorFeed.firstOrNull()?.contains("DOOR(${zone.displayName}): $valueText") != true
        _status.value = old.copy(
            trunkDoorRaw = if (zone == DoorZone.TRUNK) valueText else old.trunkDoorRaw,
            rearLeftDoorRaw = if (zone == DoorZone.REAR_LEFT) valueText else old.rearLeftDoorRaw,
            rearRightDoorRaw = if (zone == DoorZone.REAR_RIGHT) valueText else old.rearRightDoorRaw,
            doorBoundsLabel = boundsLabel ?: old.doorBoundsLabel,
            sensorUpdateSeq = old.sensorUpdateSeq + 1,
            lastSensorAtMs = now,
            sensorFeed = if (shouldAppend) (listOf(entry) + old.sensorFeed).take(30) else old.sensorFeed
        )
    }

    @Synchronized
    fun reportCabin(
        openWindows: Int,
        windowDetail: String,
        temp: Float,
        rearSeatDetail: String
    ) {
        val now = System.currentTimeMillis()
        val old = _status.value
        val summary = "${openWindows}개 열림 ($windowDetail)"
        val changed = old.windowOpenSummary != summary ||
            old.cabinTempCelsius != temp ||
            old.rearSeatSummary != rearSeatDetail
        val entry = "[${timeTag(now)}] CABIN: temp=${"%.1f".format(temp)}°C, win=$openWindows, seat=$rearSeatDetail"
        _status.value = old.copy(
            windowOpenSummary = summary,
            cabinTempCelsius = temp,
            rearSeatSummary = rearSeatDetail,
            sensorUpdateSeq = old.sensorUpdateSeq + 1,
            lastSensorAtMs = now,
            sensorFeed = if (changed) (listOf(entry) + old.sensorFeed).take(30) else old.sensorFeed
        )
    }

    @Synchronized
    fun reportCruise(raw: String) {
        val now = System.currentTimeMillis()
        val old = _status.value
        val entry = "[${timeTag(now)}] ADAS/CRUISE: $raw"
        _status.value = old.copy(
            cruiseRaw = raw,
            sensorUpdateSeq = old.sensorUpdateSeq + 1,
            lastSensorAtMs = now,
            sensorFeed = (listOf(entry) + old.sensorFeed).take(30)
        )
    }

    @Synchronized
    fun reportNaviSnapshot(
        routeState: String? = null,
        destName: String? = null,
        gpsDetail: String? = null
    ) {
        val now = System.currentTimeMillis()
        val old = _status.value
        val nextRoute = routeState ?: old.naviRouteState
        val nextDest = destName ?: old.naviDestinationRaw
        val nextGps = gpsDetail ?: old.gpsSnapshotRaw
        val changed = nextRoute != old.naviRouteState || nextDest != old.naviDestinationRaw || nextGps != old.gpsSnapshotRaw
        val entry = "[${timeTag(now)}] NAVI/GPS: route=$nextRoute, dest=$nextDest, gps=$nextGps"
        _status.value = old.copy(
            naviRouteState = nextRoute,
            naviDestinationRaw = nextDest,
            gpsSnapshotRaw = nextGps,
            sensorUpdateSeq = old.sensorUpdateSeq + 1,
            lastSensorAtMs = now,
            sensorFeed = if (changed) (listOf(entry) + old.sensorFeed).take(30) else old.sensorFeed
        )
    }
}
