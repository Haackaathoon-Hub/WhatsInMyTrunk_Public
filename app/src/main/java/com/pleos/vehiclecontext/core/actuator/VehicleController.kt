package com.pleos.vehiclecontext.core.actuator

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import com.pleos.vehiclecontext.core.signal.PleosSignalProvider

/**
 * Pleos Vehicle SDK 기반 물리 액추에이터 제어기.
 * 도어(트렁크), 창문, HVAC(공조) 제어 전 @RequiresCustomPermission 권한을 확인하며,
 * 비차량/모의 환경에서도 크래시 없이 안전하게 동작하도록 모든 SDK 호출을 runCatching으로 보호합니다.
 */
class VehicleController(
    private val context: Context? = null,
    private val sharedVehicleProvider: (() -> Vehicle?)? = null,
    private val doorBoundsProvider: (() -> Pair<Int, Int>)? = null,
    private val onDoorCommandDispatched: ((isOpen: Boolean) -> Unit)? = null
) {
    constructor(context: Context?) : this(
        context = context,
        sharedVehicleProvider = null,
        doorBoundsProvider = null,
        onDoorCommandDispatched = null
    )

    @Volatile
    private var vehicle: Vehicle? = null

    @Volatile
    var lastError: String? = null
        private set

    init {
        if (context != null && sharedVehicleProvider == null) {
            Thread({
                ensureVehicleInitialized()
            }, "PleosActuatorInit").apply {
                isDaemon = true
                start()
            }
        }
    }

    @Synchronized
    private fun ensureVehicleInitialized(): Vehicle? {
        sharedVehicleProvider?.invoke()?.let { return it }
        vehicle?.let { return it }
        val ctx = context ?: return null
        return runCatching {
            Vehicle(ctx).apply {
                initialize()
            }.also { vehicle = it }
        }.onFailure { err ->
            recordError("Vehicle 초기화 실패: ${err.message}")
        }.getOrNull()
    }

    private fun hasControlPermission(permission: String): Boolean {
        val ctx = context ?: run {
            recordError("차량 제어 불가(Context 없음): $permission")
            return false
        }
        val granted = runCatching {
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            recordError("제어 권한 미승인: $permission")
        }
        return granted
    }

    private fun recordError(message: String) {
        lastError = message
        runCatching { android.util.Log.w(TAG, message) }
    }

    /**
     * 트렁크(테일게이트) 개방: 차량별 DoorArea capability maxValue(기본 100) 기준으로 1회 명령만 전송하며,
     * 이후 사용자가 물리 버튼/에뮬레이터로 닫을 수 있도록 상태를 계속 붙잡지 않습니다.
     */
    fun openTrunk(): Boolean {
        if (!hasControlPermission(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_DOORS)) return false
        return runCatching {
            val v = ensureVehicleInitialized() ?: return@runCatching false
            lastError = null
            val targetMax = doorBoundsProvider?.invoke()?.second?.takeIf { it > 0 } ?: 100
            v.getDoor().setDoorPosition(
                DoorArea(DoorValue.REAR),
                targetMax,
                { _, _ -> onDoorCommandDispatched?.invoke(true) },
                { err ->
                    recordError("트렁크 개방 실패: ${err.message}")
                    onDoorCommandDispatched?.invoke(true)
                }
            )
            onDoorCommandDispatched?.invoke(true)
            true
        }.onFailure { err ->
            recordError("트렁크 개방 호출 오류: ${err.message}")
        }.getOrDefault(false)
    }

    /**
     * 트렁크(테일게이트) 닫힘: 차량별 DoorArea capability minValue(기본 0)로 닫고 제어 점유를 즉시 해제합니다.
     */
    fun closeTrunk(): Boolean {
        if (!hasControlPermission(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_DOORS)) return false
        return runCatching {
            val v = ensureVehicleInitialized() ?: return@runCatching false
            lastError = null
            val targetMin = doorBoundsProvider?.invoke()?.first ?: 0
            v.getDoor().setDoorPosition(
                DoorArea(DoorValue.REAR),
                targetMin,
                { _, _ -> onDoorCommandDispatched?.invoke(false) },
                { err ->
                    recordError("트렁크 닫기 실패: ${err.message}")
                    onDoorCommandDispatched?.invoke(false)
                }
            )
            onDoorCommandDispatched?.invoke(false)
            true
        }.onFailure { err ->
            recordError("트렁크 닫기 호출 오류: ${err.message}")
        }.getOrDefault(false)
    }

    /**
     * 1열/2열 전체 4개 창문 닫힘(0%) 제어
     */
    fun closeAllWindows(): Boolean {
        if (!hasControlPermission(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_WINDOWS)) return false
        return runCatching {
            val v = ensureVehicleInitialized() ?: return@runCatching false
            lastError = null
            val windowApi = v.getWindow()
            val targets = listOf(
                WindowValue.ROW_1_LEFT,
                WindowValue.ROW_1_RIGHT,
                WindowValue.ROW_2_LEFT,
                WindowValue.ROW_2_RIGHT
            )
            for (wv in targets) {
                windowApi.setWindowPosition(
                    WindowArea(wv),
                    0,
                    { _, _ -> },
                    { err -> recordError("창문(${wv.name}) 닫기 실패: ${err.message}") }
                )
            }
            true
        }.onFailure { err ->
            recordError("창문 닫기 호출 오류: ${err.message}")
        }.getOrDefault(false)
    }

    /**
     * 실내 HVAC 전원 ON 및 목표 온도(기본 20.0°C) 급속 냉방 설정
     */
    fun activateCabinCooling(targetTemp: Float = 20.0f): Boolean {
        if (!hasControlPermission(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_CLIMATE)) return false
        return runCatching {
            val v = ensureVehicleInitialized() ?: return@runCatching false
            lastError = null
            val hvacApi = v.getHVAC()
            val fallbackSeatArea = SeatArea(SeatValue.ROW_1_LEFT)
            val dispatchPower: (Collection<SeatArea>) -> Unit = { areas ->
                areas.forEach { area ->
                    hvacApi.setHvacPower(
                        area,
                        true,
                        { _, _ -> },
                        { err -> recordError("HVAC 전원 ON 실패: ${err.message}") }
                    )
                }
            }
            val dispatchTemperature: (Collection<SeatArea>) -> Unit = { areas ->
                areas.forEach { area ->
                    hvacApi.setHvacTemperature(
                        area,
                        targetTemp,
                        TemperatureUnit.CELSIUS,
                        { _, _ -> },
                        { err -> recordError("HVAC 온도(${targetTemp}°C) 설정 실패: ${err.message}") }
                    )
                }
            }
            hvacApi.checkHvacPowerCapabilities(
                { _, supportedAreas -> dispatchPower(supportedAreas.ifEmpty { listOf(fallbackSeatArea) }) },
                { dispatchPower(listOf(fallbackSeatArea)) }
            )
            hvacApi.checkHvacTemperatureCapabilities(
                { _, areaBounds, _ ->
                    dispatchTemperature(
                        areaBounds.keys.ifEmpty {
                            listOf(fallbackSeatArea, SeatArea(SeatValue.ROW_1_RIGHT))
                        }
                    )
                },
                { dispatchTemperature(listOf(fallbackSeatArea, SeatArea(SeatValue.ROW_1_RIGHT))) }
            )
            true
        }.onFailure { err ->
            recordError("HVAC 제어 호출 오류: ${err.message}")
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "VehicleController"
    }
}
