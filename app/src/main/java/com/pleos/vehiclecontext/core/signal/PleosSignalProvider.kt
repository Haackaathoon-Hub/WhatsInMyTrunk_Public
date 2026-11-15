package com.pleos.vehiclecontext.core.signal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.pleos.vehiclecontext.core.location.PlaceSnapshot
import com.pleos.vehiclecontext.core.location.VehicleLocationResolver
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.DrivingMode
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 실제 Pleos Connect SDK (Vehicle SDK 2.3.2, NaviHelper SDK 2.0.3) 및
 * AAOS 표준 CarPropertyManager (GEAR_SELECTION) 연동 어댑터.
 *
 * 공모전 가이드라인 준수사항:
 * 1. AAOS 표준 API(GEAR_SELECTION 등)를 우선 검토하고 제조사 특화 기능(트렁크/도어/창문/시트/내비)은 Pleos SDK 사용
 * 2. 기능 실행 전 @RequiresCustomPermission 권한 체크 및 checkXXXCapabilities() 검증 수행
 * 3. 미지원 환경(일반 Android, JVM 단위 테스트 등)에서도 앱이 비정상 종료되지 않도록 runCatching으로 방어
 */
class PleosSignalProvider(
    private val context: Context? = null,
    private val autoStart: Boolean = true
) : SignalProvider {

    private val _signals = MutableSharedFlow<VehicleSignal>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val signals: Flow<VehicleSignal> = _signals.asSharedFlow()

    val diagnostics = BridgeDiagnostics()

    private val _routeTarget = MutableStateFlow<RouteTarget?>(null)
    val routeTarget: StateFlow<RouteTarget?> = _routeTarget.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    @Volatile private var vehicleInstance: Vehicle? = null
    @Volatile private var naviHelperInstance: NaviHelper? = null

    @Volatile private var aaosGearBound = false
    @Volatile private var pleosGearBound = false
    @Volatile private var pleosDoorBound = false
    @Volatile private var pleosWindowBound = false
    @Volatile private var pleosHvacBound = false
    @Volatile private var pleosSeatBound = false
    @Volatile private var pleosDrivingModeBound = false
    @Volatile private var naviHelperBound = false

    private val openWindowZones = mutableSetOf<WindowValue>()
    private val occupiedRearSeats = mutableSetOf<SeatValue>()
    private val latestDoor = mutableMapOf<DoorZone, Boolean>()
    private val doorResyncZones = mutableSetOf<DoorZone>()
    @Volatile private var trunkMinPos: Int = 0
    @Volatile private var trunkMaxPos: Int = 100
    private var lastGear: GearState? = null
    private var lastDestination: VehicleSignal.DestinationUpdated? = null
    private var lastArrivalName: String? = null
    private var routeActive = false
    private var routeDestination: String? = null
    private var routeRevision = 0L

    private var latestGpsPlace: PlaceSnapshot? = null
    private var latestNaviOrigin: PlaceSnapshot? = null
    private var latestNaviDestination: PlaceSnapshot? = null
    private var naviBookmarks: List<PlaceSnapshot> = emptyList()
    private var naviRecentDestinations: List<PlaceSnapshot> = emptyList()
    private var lastLocationContextKey: String? = null
    private var gpsListenerRegistered = false
    @Volatile private var removeGpsListener: (() -> Unit)? = null

    @Volatile private var latestCabinTemp: Float = 22.0f
    @Volatile private var hasReceivedHvacTemp: Boolean = false
    @Volatile private var hvacTargetArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    @Volatile private var lastCruiseMode: AutonomousDrivingMode? = null

    private val diagnosticNotes = ConcurrentHashMap<String, String>()
    private val sdkSupportedCache = ConcurrentHashMap<String, Boolean>()
    private val bindingExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PleosVehicleBinder").apply { isDaemon = true }
    }

    init {
        if (autoStart) {
            refreshVehicleBindings()
        }
    }

    fun vehicleHandle(): Vehicle? = vehicleInstance

    fun trunkPositionBounds(): Pair<Int, Int> = trunkMinPos to trunkMaxPos

    /**
     * 앱 제어(OPEN_TRUNK/CLOSE_TRUNK) 직후 다음 실제 SDK 콜백/폴링이
     * 캐시 중복 필터(latestDoor)에 의해 무시되지 않고 엔진 상태를 실측치와 재동기화하도록 표시합니다.
     */
    @Synchronized
    fun markDoorCommandIssued(zone: DoorZone) {
        doorResyncZones.add(zone)
    }

    @Synchronized
    fun activeDestination(): String? = routeDestination.takeIf { routeActive }

    @Synchronized
    private fun updateRoute(active: Boolean, destination: String? = null) {
        routeRevision++
        routeActive = active
        routeDestination = destination?.trim()?.takeIf { it.isNotBlank() && active }
        _routeTarget.value = null
    }

    fun publishSnapshot() {
        val snapshot = synchronized(this) {
            buildList {
                lastDestination?.let { add(it) }
                latestDoor.forEach { (zone, open) ->
                    add(VehicleSignal.DoorStateChanged(zone = zone, isOpen = open, origin = SignalOrigin.REAL_SDK))
                }
                lastGear?.let { add(VehicleSignal.GearChanged(it, SignalOrigin.REAL_SDK)) }
            }
        }
        snapshot.forEach { _signals.tryEmit(it) }
        refreshNow()
    }

    fun refreshNow() {
        if (context == null) return
        bindingExecutor.execute {
            runCatching {
                val doorApi = vehicleInstance?.getDoor()
                if (doorApi != null && hasPermission(PLEOS_PERM_CAR_DOORS)) {
                    listOf(
                        DoorValue.REAR to DoorZone.TRUNK,
                        DoorValue.ROW_2_LEFT to DoorZone.REAR_LEFT,
                        DoorValue.ROW_2_RIGHT to DoorZone.REAR_RIGHT
                    ).forEach { (value, zone) ->
                        doorApi.getCurrentDoorPosition(
                            DoorArea(value),
                            { pos -> emitDoorSignal(zone, pos) },
                            { }
                        )
                    }
                }
                // AAOS 표준 CarPropertyManager(GEAR_SELECTION)가 활성화된 경우,
                // deprecated Pleos getGear() 폴링으로 D-N-D-R-P 시퀀스가 덮어쓰이지 않도록 생략합니다.
                if (!aaosGearBound && hasPermission(PLEOS_PERM_CAR_DRIVE)) {
                    vehicleInstance?.getGear()?.getCurrentGearSelectionState(
                        { gear ->
                            val mapped = mapPleosGearOrNull(gear)
                            diagnostics.reportGear(gear?.name ?: "null", "PleosPoll", mapped)
                            if (mapped != null) {
                                offer(VehicleSignal.GearChanged(mapped, SignalOrigin.REAL_SDK))
                            }
                        },
                        { }
                    )
                }
                val hvacApi = vehicleInstance?.getHVAC()
                if (hvacApi != null && pleosHvacBound && hasPermission(PLEOS_PERM_CAR_CLIMATE)) {
                    hvacApi.getHvacTemperature(
                        hvacTargetArea,
                        TemperatureUnit.CELSIUS,
                        { temperature -> updateCabinTemp(temperature) },
                        { }
                    )
                }
            }
        }
    }

    /**
     * 런타임 권한 승인 이후 또는 앱 초기화 시 모든 실제 차량 SDK 바인딩과 초기 상태 조회를 수행합니다.
     * 메인(UI) 스레드 블로킹(ANR)을 방지하기 위해 단일 백그라운드 스레드에서 순차 연결합니다.
     */
    fun refreshVehicleBindings() {
        if (context == null) return
        diagnostics.report("door", "차량 서비스 연결 요청 중")
        bindingExecutor.execute {
            initAaosGearListener()
            initPleosVehicle()
            initPleosNaviHelper()
        }
        initPlatformLocationTracking()
        refreshLocationContext()
        if (autoStart && pollJob == null) {
            pollJob = scope.launch {
                while (isActive) {
                    delay(2500)
                    refreshNow()
                }
            }
        }
    }

    /**
     * AAOS 표준 CarPropertyManager를 통한 GEAR_SELECTION (ID: 289408000) 초기 조회 및 리스너 연동.
     */
    private fun initAaosGearListener() {
        val ctx = context ?: return
        if (aaosGearBound) return

        runCatching {
            val pm = ctx.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                recordDiagnostic(KEY_AAOS_GEAR, "비자동차(Non-AAOS) 단말")
                return@runCatching
            }

            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val carObj = createCarMethod.invoke(null, ctx) ?: return@runCatching

            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            val carPropertyManager = getCarManagerMethod.invoke(carObj, "property") ?: return@runCatching

            // 초기 기어 상태 단발 조회 (CAR_POWERTRAIN 권한 없으면 SecurityException 발생 -> 무시)
            runCatching {
                val getIntProp = carPropertyManager.javaClass.getMethod(
                    "getIntProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val initialRaw = getIntProp.invoke(
                    carPropertyManager,
                    AAOS_GEAR_SELECTION_PROPERTY_ID,
                    0
                ) as? Int
                if (initialRaw != null) {
                    val mapped = mapAaosGearOrNull(initialRaw)
                    diagnostics.reportGear("VHAL($initialRaw)", "AAOS초기", mapped)
                    if (mapped != null) {
                        offer(VehicleSignal.GearChanged(mapped, SignalOrigin.REAL_SDK))
                    }
                }
            }

            val callbackInterface = Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            val proxy = Proxy.newProxyInstance(
                ctx.classLoader,
                arrayOf(callbackInterface)
            ) { proxyObj, method, args ->
                when (method.name) {
                    "onChangeEvent" -> {
                        if (args != null && args.isNotEmpty()) {
                            val propValueObj = args[0]
                            val propId = propValueObj.javaClass.getMethod("getPropertyId").invoke(propValueObj) as? Int
                            if (propId == AAOS_GEAR_SELECTION_PROPERTY_ID) {
                                val rawVal = propValueObj.javaClass.getMethod("getValue").invoke(propValueObj) as? Int
                                val mapped = if (rawVal != null) mapAaosGearOrNull(rawVal) else null
                                diagnostics.reportGear("VHAL(${rawVal ?: "null"})", "AAOS", mapped)
                                if (mapped != null) {
                                    offer(VehicleSignal.GearChanged(mapped, SignalOrigin.REAL_SDK))
                                }
                            }
                        }
                        null
                    }
                    "hashCode" -> System.identityHashCode(proxyObj)
                    "equals" -> proxyObj === args?.getOrNull(0)
                    "toString" -> "AaosGearCallbackProxy@${System.identityHashCode(proxyObj)}"
                    else -> null
                }
            }

            val registerMethod = carPropertyManager.javaClass.getMethod(
                "registerCallback",
                callbackInterface,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            registerMethod.invoke(carPropertyManager, proxy, AAOS_GEAR_SELECTION_PROPERTY_ID, 0.0f)
            aaosGearBound = true
            sdkSupportedCache[KEY_AAOS_GEAR] = true
            diagnosticNotes[KEY_AAOS_GEAR] = "AAOS CarPropertyManager GEAR_SELECTION 리스너 연결됨"
        }.onFailure { err ->
            val causeMsg = err.cause?.message ?: err.message ?: err.javaClass.simpleName
            recordDiagnostic(KEY_AAOS_GEAR, "AAOS CarProperty 연동 대기/실패: $causeMsg")
        }
    }

    /**
     * Pleos Vehicle SDK의 Vehicle 초기화 및 Gear/Door/Window/HVAC/Seat/DrivingMode 연동.
     */
    private fun initPleosVehicle() {
        val ctx = context ?: return
        runCatching {
            val vehicle = vehicleInstance ?: Vehicle(ctx).also {
                it.initialize()
                vehicleInstance = it
            }

            bindPleosGear(vehicle)
            bindPleosDoor(vehicle)
            bindPleosWindow(vehicle)
            bindPleosHvac(vehicle)
            bindPleosSeat(vehicle)
            bindPleosDrivingMode(vehicle)
        }.onFailure { err ->
            recordDiagnostic("vehicle_init", "Pleos Vehicle 초기화 실패: ${err.message}")
        }
    }

    private fun bindPleosGear(vehicle: Vehicle) {
        if (pleosGearBound) return
        if (!hasPermission(PLEOS_PERM_CAR_DRIVE)) {
            recordDiagnostic(KEY_PLEOS_GEAR, "권한 미부여 ($PLEOS_PERM_CAR_DRIVE)")
            return
        }

        runCatching {
            val gearApi = vehicle.getGear()
            gearApi.registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) {
                    val mapped = mapPleosGearOrNull(gear)
                    diagnostics.reportGear(gear?.name ?: "null", "Pleos", mapped)
                    if (mapped != null) {
                        offer(VehicleSignal.GearChanged(mapped, SignalOrigin.REAL_SDK))
                    }
                }

                override fun onFailed(e: Exception) {
                    recordDiagnostic(KEY_PLEOS_GEAR, "기어 리스너 오류: ${e.message}")
                }
            })
            gearApi.getCurrentGearSelectionState(
                { gear ->
                    val mapped = mapPleosGearOrNull(gear)
                    diagnostics.reportGear(gear?.name ?: "null", "Pleos초기", mapped)
                    if (mapped != null) {
                        offer(VehicleSignal.GearChanged(mapped, SignalOrigin.REAL_SDK))
                    }
                },
                { e ->
                    recordDiagnostic(KEY_PLEOS_GEAR, "초기 기어 조회 실패: ${e.message}")
                }
            )
            pleosGearBound = true
            sdkSupportedCache[KEY_PLEOS_GEAR] = true
            diagnosticNotes[KEY_PLEOS_GEAR] = "Pleos Gear SDK 연결됨"
        }.onFailure { err ->
            recordDiagnostic(KEY_PLEOS_GEAR, "Pleos Gear 바인딩 실패: ${err.message}")
        }
    }

    private fun bindPleosDoor(vehicle: Vehicle) {
        if (pleosDoorBound) return
        if (!hasPermission(PLEOS_PERM_CAR_DOORS)) {
            recordDiagnostic(KEY_DOOR, "권한 미부여 ($PLEOS_PERM_CAR_DOORS)")
            return
        }

        runCatching {
            val doorApi = vehicle.getDoor()
            doorApi.checkDoorPositionCapabilities(
                { capMap ->
                    sdkSupportedCache[KEY_DOOR] = true
                    runCatching {
                        val rearBounds = capMap.entries
                            .firstOrNull { DoorValue.REAR in it.key.values }
                            ?.value
                        if (rearBounds != null) {
                            trunkMinPos = rearBounds.minValue
                            trunkMaxPos = rearBounds.maxValue.takeIf { it > rearBounds.minValue } ?: 100
                        }
                    }
                    diagnosticNotes[KEY_DOOR] = "Pleos Door 역량 확인 (${trunkMinPos}..${trunkMaxPos})"
                    diagnostics.report("door", "Door 역량 확인 (${trunkMinPos}..${trunkMaxPos})")
                    registerDoorAreaListeners(doorApi)
                },
                { err ->
                    sdkSupportedCache[KEY_DOOR] = false
                    recordDiagnostic(KEY_DOOR, "Door 역량 확인 실패(${err.message}) - 기본 영역 등록 시도")
                    registerDoorAreaListeners(doorApi)
                }
            )
            pleosDoorBound = true
        }.onFailure { err ->
            recordDiagnostic(KEY_DOOR, "Door 바인딩 실패: ${err.message}")
        }
    }

    private fun registerDoorAreaListeners(doorApi: Door) {
        val targets = listOf(
            DoorValue.REAR to DoorZone.TRUNK,
            DoorValue.ROW_2_LEFT to DoorZone.REAR_LEFT,
            DoorValue.ROW_2_RIGHT to DoorZone.REAR_RIGHT
        )

        for ((doorValue, zone) in targets) {
            runCatching {
                val area = DoorArea(doorValue)
                doorApi.registerDoorPosition(area, object : DoorPositionListener {
                    override fun onPositionUpdated(pos: Int?) {
                        emitDoorSignal(zone, pos)
                    }

                    override fun onFailed(e: Exception) {
                        recordDiagnostic(KEY_DOOR, "${zone.displayName} 리스너 오류: ${e.message}")
                    }
                })
                doorApi.getCurrentDoorPosition(
                    area,
                    { pos -> emitDoorSignal(zone, pos) },
                    { e -> recordDiagnostic(KEY_DOOR, "${zone.displayName} 초기 위치 조회 실패: ${e.message}") }
                )
            }
        }
    }

    private fun emitDoorSignal(zone: DoorZone, pos: Int?) {
        diagnostics.reportDoorPosition(zone, pos, boundsLabel = "${trunkMinPos}..${trunkMaxPos}")
        val isOpen = (pos ?: 0) > trunkMinPos
        offer(
            VehicleSignal.DoorStateChanged(
                zone = zone,
                isOpen = isOpen,
                origin = SignalOrigin.REAL_SDK
            )
        )
    }

    private fun bindPleosWindow(vehicle: Vehicle) {
        if (pleosWindowBound) return
        if (!hasPermission(PLEOS_PERM_CAR_WINDOWS)) {
            recordDiagnostic(KEY_WINDOW, "권한 미부여 ($PLEOS_PERM_CAR_WINDOWS)")
            return
        }

        val defaultZones = listOf(
            WindowValue.ROW_1_LEFT,
            WindowValue.ROW_1_RIGHT,
            WindowValue.ROW_2_LEFT,
            WindowValue.ROW_2_RIGHT
        )

        runCatching {
            val windowApi = vehicle.getWindow()
            windowApi.checkWindowPositionCapabilities(
                { capMap ->
                    val supportedZones = capMap.keys
                        .flatMap { it.values.toList() }
                        .distinct()
                        .ifEmpty { defaultZones }
                    sdkSupportedCache[KEY_WINDOW] = capMap.isNotEmpty()
                    diagnosticNotes[KEY_WINDOW] = "Window 리스너 ${supportedZones.size}개 영역 연결됨"
                    registerWindowZones(windowApi, supportedZones)
                },
                { err ->
                    sdkSupportedCache[KEY_WINDOW] = false
                    recordDiagnostic(KEY_WINDOW, "Window 역량 확인 실패(${err.message}) - 기본 4영역 등록")
                    registerWindowZones(windowApi, defaultZones)
                }
            )
            pleosWindowBound = true
        }.onFailure { err ->
            recordDiagnostic(KEY_WINDOW, "Window 바인딩 실패: ${err.message}")
        }
    }

    private fun registerWindowZones(windowApi: Window, zones: List<WindowValue>) {
        for (zoneValue in zones) {
            runCatching {
                val area = WindowArea(zoneValue)
                windowApi.registerWindowPosition(
                    area,
                    object : WindowPositionListener {
                        override fun onWindowPositionUpdated(position: Int?) {
                            updateWindowState(zoneValue, position)
                        }

                        override fun onFailed(e: Exception) {
                            recordDiagnostic(KEY_WINDOW, "창문(${zoneValue.name}) 오류: ${e.message}")
                        }
                    }
                )
                windowApi.getCurrentWindowPosition(
                    area,
                    { position -> updateWindowState(zoneValue, position) },
                    { e -> recordDiagnostic(KEY_WINDOW, "창문(${zoneValue.name}) 초기 조회 실패: ${e.message}") }
                )
            }
        }
    }

    private fun updateWindowState(zoneValue: WindowValue, position: Int?) {
        val isOpen = (position ?: 0) > 0
        synchronized(openWindowZones) {
            if (isOpen) {
                openWindowZones.add(zoneValue)
            } else {
                openWindowZones.remove(zoneValue)
            }
        }
        emitCabinStatus()
    }

    /**
     * 실내 공조 목표 온도(HVAC_TEMPERATURE_SET) 역량 확인, 초기 조회 및 실시간 변경 리스너 등록.
     */
    private fun bindPleosHvac(vehicle: Vehicle) {
        if (pleosHvacBound) return
        if (!hasPermission(PLEOS_PERM_CAR_CLIMATE)) {
            recordDiagnostic(KEY_HVAC, "권한 미부여 ($PLEOS_PERM_CAR_CLIMATE)")
            return
        }

        val fallbackArea = SeatArea(SeatValue.ROW_1_LEFT)
        runCatching {
            val hvacApi = vehicle.getHVAC()
            hvacApi.checkHvacTemperatureCapabilities(
                { _, areaBounds, _ ->
                    val targetArea = areaBounds.keys.firstOrNull { SeatValue.ROW_1_LEFT in it.values }
                        ?: areaBounds.keys.firstOrNull()
                        ?: fallbackArea
                    sdkSupportedCache[KEY_HVAC] = areaBounds.isNotEmpty()
                    diagnosticNotes[KEY_HVAC] = "HVAC 공조 목표 온도 리스너 연결됨"
                    registerHvacListener(hvacApi, targetArea)
                },
                { err ->
                    sdkSupportedCache[KEY_HVAC] = false
                    recordDiagnostic(KEY_HVAC, "HVAC 역량 확인 실패(${err.message}) - 운전석(ROW_1_LEFT) 영역 등록")
                    registerHvacListener(hvacApi, fallbackArea)
                }
            )
            pleosHvacBound = true
        }.onFailure { err ->
            recordDiagnostic(KEY_HVAC, "HVAC 바인딩 실패: ${err.message}")
        }
    }

    private fun registerHvacListener(hvacApi: HVAC, area: SeatArea) {
        hvacTargetArea = area
        runCatching {
            hvacApi.registerHvacTemperature(
                area,
                object : HvacTemperatureListener {
                    override fun onHvacTemperatureUpdated(temperature: Float?) {
                        updateCabinTemp(temperature)
                    }

                    override fun onFailed(e: Exception) {
                        recordDiagnostic(KEY_HVAC, "HVAC 목표 온도 리스너 오류: ${e.message}")
                    }
                }
            )
            hvacApi.getHvacTemperature(
                area,
                TemperatureUnit.CELSIUS,
                { temperature -> updateCabinTemp(temperature) },
                { e ->
                    recordDiagnostic(KEY_HVAC, "HVAC 초기 목표 온도 조회 실패: ${e.message}")
                }
            )
        }
    }

    private fun updateCabinTemp(temperature: Float?) {
        if (temperature == null) return
        val changed = !hasReceivedHvacTemp || kotlin.math.abs(latestCabinTemp - temperature) >= 0.05f
        latestCabinTemp = temperature
        hasReceivedHvacTemp = true
        if (changed) {
            emitCabinStatus()
        }
    }

    /**
     * 2열(ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT) 실제 좌석 착석 센서 연동.
     */
    private fun bindPleosSeat(vehicle: Vehicle) {
        if (pleosSeatBound) return
        if (!hasPermission(PLEOS_PERM_CAR_SEATS)) {
            recordDiagnostic(KEY_SEAT, "권한 미부여 ($PLEOS_PERM_CAR_SEATS)")
            return
        }

        val rearSeats = listOf(
            SeatValue.ROW_2_LEFT,
            SeatValue.ROW_2_CENTER,
            SeatValue.ROW_2_RIGHT
        )

        runCatching {
            val seatApi = vehicle.getSeat()
            seatApi.checkSeatOccupancyCapabilities(
                { supportedAreas ->
                    sdkSupportedCache[KEY_SEAT] = supportedAreas.isNotEmpty()
                    diagnosticNotes[KEY_SEAT] = "Seat 착석 센서(${supportedAreas.size}개 지원 영역) 연결됨"
                    registerRearSeatOccupancy(seatApi, rearSeats)
                },
                { err ->
                    sdkSupportedCache[KEY_SEAT] = false
                    recordDiagnostic(KEY_SEAT, "Seat 역량 확인 실패(${err.message}) - 2열 기본 영역 등록")
                    registerRearSeatOccupancy(seatApi, rearSeats)
                }
            )
            pleosSeatBound = true
        }.onFailure { err ->
            recordDiagnostic(KEY_SEAT, "Seat 바인딩 실패: ${err.message}")
        }
    }

    private fun registerRearSeatOccupancy(seatApi: Seat, seatValues: List<SeatValue>) {
        for (seatValue in seatValues) {
            runCatching {
                val area = SeatArea(seatValue)
                seatApi.registerSeatOccupancy(
                    area,
                    object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                            updateRearSeatState(seatValue, occupancy == true)
                        }

                        override fun onFailed(e: Exception) {
                            recordDiagnostic(KEY_SEAT, "시트(${seatValue.name}) 오류: ${e.message}")
                        }
                    }
                )
                seatApi.getCurrentSeatOccupancy(
                    area,
                    { occupied -> updateRearSeatState(seatValue, occupied == true) },
                    { e -> recordDiagnostic(KEY_SEAT, "시트(${seatValue.name}) 초기 조회 실패: ${e.message}") }
                )
            }
        }
    }

    private fun updateRearSeatState(seatValue: SeatValue, isOccupied: Boolean) {
        synchronized(occupiedRearSeats) {
            if (isOccupied) {
                occupiedRearSeats.add(seatValue)
            } else {
                occupiedRearSeats.remove(seatValue)
            }
        }
        emitCabinStatus()
    }

    /**
     * ADAS / 크루즈 컨트롤 작동 상태(DrivingMode) 실제 SDK 연동.
     */
    private fun bindPleosDrivingMode(vehicle: Vehicle) {
        if (pleosDrivingModeBound) return
        if (!hasPermission(PLEOS_PERM_CAR_DRIVE)) {
            recordDiagnostic(KEY_DRIVING_MODE, "권한 미부여 ($PLEOS_PERM_CAR_DRIVE)")
            return
        }

        runCatching {
            val drivingModeApi: DrivingMode = vehicle.getDrivingMode()
            drivingModeApi.checkCruiseControlStateCapabilities(
                { _ ->
                    sdkSupportedCache[KEY_DRIVING_MODE] = true
                    diagnosticNotes[KEY_DRIVING_MODE] = "Pleos DrivingMode 크루즈 상태 리스너 연결됨"
                    registerCruiseListener(drivingModeApi)
                },
                { err ->
                    sdkSupportedCache[KEY_DRIVING_MODE] = false
                    recordDiagnostic(KEY_DRIVING_MODE, "DrivingMode 역량 확인 실패(${err.message})")
                    registerCruiseListener(drivingModeApi)
                }
            )
            pleosDrivingModeBound = true
        }.onFailure { err ->
            recordDiagnostic(KEY_DRIVING_MODE, "DrivingMode 바인딩 실패: ${err.message}")
        }
    }

    private fun registerCruiseListener(drivingModeApi: DrivingMode) {
        runCatching {
            drivingModeApi.registerCruiseControlState(object : CruiseControlStateListener {
                override fun onCruiseControlStateUpdated(state: CruiseControl?) {
                    emitCruiseControlSignal(state)
                }

                override fun onFailed(e: Exception) {
                    recordDiagnostic(KEY_DRIVING_MODE, "CruiseControl 리스너 오류: ${e.message}")
                }
            })
            drivingModeApi.getCruiseControlState(
                { state -> emitCruiseControlSignal(state) },
                { e -> recordDiagnostic(KEY_DRIVING_MODE, "CruiseControl 초기 조회 실패: ${e.message}") }
            )
        }
    }

    private fun emitCruiseControlSignal(state: CruiseControl?) {
        val mapped = mapCruiseControlMode(state)
        diagnostics.reportCruise("${state?.name ?: "null"} -> ${mapped.displayName}")
        if (mapped == lastCruiseMode) return
        lastCruiseMode = mapped
        _signals.tryEmit(
            VehicleSignal.AutonomousModeChanged(
                mode = mapped,
                origin = SignalOrigin.REAL_SDK
            )
        )
    }

    private fun emitCabinStatus() {
        val windowList = synchronized(openWindowZones) { openWindowZones.map { it.name } }
        val seatList = synchronized(occupiedRearSeats) { occupiedRearSeats.map { it.name } }
        val openCount = windowList.size
        val rearOccupied = seatList.isNotEmpty()
        diagnostics.reportCabin(
            openWindows = openCount,
            windowDetail = if (windowList.isEmpty()) "전체 닫힘" else windowList.joinToString(","),
            temp = latestCabinTemp,
            rearSeatDetail = if (seatList.isEmpty()) "비어 있음" else seatList.joinToString(",")
        )
        _signals.tryEmit(
            VehicleSignal.CabinStatusUpdated(
                rearSeatOccupied = rearOccupied,
                openWindowsCount = openCount,
                cabinTempCelsius = latestCabinTemp,
                origin = SignalOrigin.REAL_SDK
            )
        )
    }

    /**
     * Pleos NaviHelper SDK 목적지/경유지 도착, 경로 상태 및 오류 콜백 연동.
     */
    private fun initPleosNaviHelper() {
        val ctx = context ?: return
        runCatching {
            val naviHelper = naviHelperInstance ?: NaviHelper(ctx).also {
                it.initialize()
                naviHelperInstance = it
            }

            if (!naviHelperBound) {
                naviHelper.addListener(object : NaviHelperEventListener {
                    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                        updateRoute(false)
                        val destName = destinationArrivedInfo.name.ifBlank { "목적지" }
                        diagnostics.report("navigation", "실제 도착 이벤트: $destName")
                        offer(
                            VehicleSignal.DestinationArrived(
                                destinationName = destName,
                                isFinalDestination = true,
                                origin = SignalOrigin.REAL_SDK
                            )
                        )
                    }

                    override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
                        val wpName = waypointArrivedInfo.name.ifBlank { "경유지" }
                        offer(
                            VehicleSignal.DestinationArrived(
                                destinationName = wpName,
                                isFinalDestination = false,
                                origin = SignalOrigin.REAL_SDK
                            )
                        )
                    }

                    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                        val destName = destinationInfo.name.ifBlank { "목적지" }
                        val validCoords = VehicleLocationResolver.isValidCoordinate(
                            destinationInfo.latitude,
                            destinationInfo.longitude
                        )
                        val destPoi = mapPoiFromDestName(destName)
                        synchronized(this@PleosSignalProvider) {
                            latestNaviDestination = PlaceSnapshot(
                                name = destName.trim(),
                                address = destinationInfo.address.trim(),
                                latitude = destinationInfo.latitude.takeIf { validCoords },
                                longitude = destinationInfo.longitude.takeIf { validCoords },
                                poiCategory = destPoi,
                                sourceLabel = "내비 목적지"
                            )
                            if (routeActive && destinationInfo.name.isNotBlank() &&
                                (routeDestination == null || sameDestination(routeDestination, destinationInfo.name))
                            ) {
                                routeDestination = destinationInfo.name.trim()
                                _routeTarget.value = if (validCoords) {
                                    RouteTarget(
                                        name = destinationInfo.name.trim(),
                                        longitude = destinationInfo.longitude,
                                        latitude = destinationInfo.latitude,
                                        revision = routeRevision
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                        if (destinationInfo.name.isNotBlank()) {
                            diagnostics.report("navigation", "현재 목적지: $destName")
                            offer(
                                VehicleSignal.DestinationUpdated(
                                    destinationName = destName,
                                    destinationPoi = destPoi,
                                    origin = SignalOrigin.REAL_SDK
                                )
                            )
                            publishLocationContext()
                        }
                    }

                    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                        scope.launch {
                            val appCtx = context ?: return@launch
                            val rawAddr = currentLocationInfo.address.trim()
                            val snapshot = if (rawAddr.isNotBlank()) {
                                val short = VehicleLocationResolver.formatShortAddress(rawAddr)
                                PlaceSnapshot(
                                    name = short,
                                    address = rawAddr,
                                    latitude = currentLocationInfo.latitude.takeIf {
                                        VehicleLocationResolver.isValidCoordinate(it, currentLocationInfo.longitude)
                                    },
                                    longitude = currentLocationInfo.longitude.takeIf {
                                        VehicleLocationResolver.isValidCoordinate(currentLocationInfo.latitude, it)
                                    },
                                    poiCategory = VehicleLocationResolver.inferPoiFromPlaceText("$short $rawAddr"),
                                    sourceLabel = "Pleos 내비 현위치"
                                )
                            } else {
                                VehicleLocationResolver.readPlatformLocationSnapshot(
                                    context = appCtx,
                                    latitudeOverride = currentLocationInfo.latitude,
                                    longitudeOverride = currentLocationInfo.longitude,
                                    sourceLabel = "Pleos 내비 GPS"
                                )
                            }
                            if (snapshot != null) {
                                synchronized(this@PleosSignalProvider) {
                                    latestGpsPlace = snapshot
                                    if (routeActive && latestNaviOrigin == null) {
                                        latestNaviOrigin = snapshot.copy(sourceLabel = "내비 출발지")
                                    }
                                }
                                publishLocationContext()
                            }
                        }
                    }

                    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
                        val mapped = bookmarkInfoList.mapNotNull { bm ->
                            val label = bm.name.trim().ifBlank {
                                VehicleLocationResolver.formatShortAddress(bm.address)
                            }
                            if (label.isBlank()) return@mapNotNull null
                            val categoryPoi = when (bm.category.name) {
                                "HOME" -> PoiCategory.HOME
                                "OFFICE" -> PoiCategory.OFFICE
                                else -> VehicleLocationResolver.inferPoiFromPlaceText("$label ${bm.address}")
                            }
                            PlaceSnapshot(
                                name = label,
                                address = bm.address.trim(),
                                latitude = bm.latitude.takeIf {
                                    VehicleLocationResolver.isValidCoordinate(it, bm.longitude)
                                },
                                longitude = bm.longitude.takeIf {
                                    VehicleLocationResolver.isValidCoordinate(bm.latitude, it)
                                },
                                poiCategory = categoryPoi,
                                sourceLabel = "지도 즐겨찾기"
                            )
                        }
                        synchronized(this@PleosSignalProvider) {
                            naviBookmarks = mapped
                        }
                        publishLocationContext()
                    }

                    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {
                        val mapped = recentDestinationInfoList.mapNotNull { recent ->
                            val label = recent.name.trim().ifBlank {
                                VehicleLocationResolver.formatShortAddress(recent.address)
                            }
                            if (label.isBlank()) return@mapNotNull null
                            PlaceSnapshot(
                                name = label,
                                address = recent.address.trim(),
                                latitude = recent.latitude.takeIf {
                                    VehicleLocationResolver.isValidCoordinate(it, recent.longitude)
                                },
                                longitude = recent.longitude.takeIf {
                                    VehicleLocationResolver.isValidCoordinate(recent.latitude, it)
                                },
                                poiCategory = VehicleLocationResolver.inferPoiFromPlaceText("$label ${recent.address}"),
                                sourceLabel = "최근 목적지"
                            )
                        }
                        synchronized(this@PleosSignalProvider) {
                            naviRecentDestinations = mapped
                        }
                        publishLocationContext()
                    }

                    override fun onDestinationChanged() {
                        synchronized(this@PleosSignalProvider) {
                            routeRevision++
                            routeDestination = null
                            _routeTarget.value = null
                            latestNaviDestination = null
                        }
                        runCatching { naviHelper.getRouteStateInfo() }
                        runCatching { naviHelper.getDestinationInfo() }
                        runCatching { naviHelper.getCurrentLocationInfo() }
                    }

                    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                        updateRoute(true, routeStartInfo.name)
                        synchronized(this@PleosSignalProvider) {
                            if (latestNaviOrigin == null && latestGpsPlace != null) {
                                latestNaviOrigin = latestGpsPlace?.copy(sourceLabel = "내비 출발지")
                            }
                        }
                        publishLocationContext(force = true)
                        runCatching {
                            naviHelper.getDestinationInfo()
                            naviHelper.getRouteStateInfo()
                            naviHelper.getCurrentLocationInfo()
                            naviHelper.getBookmarkInfo()
                            naviHelper.getRecentDestinationInfo()
                        }
                    }

                    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                        synchronized(this@PleosSignalProvider) {
                            if (routeActive != routeStateInfo.isRouting) {
                                updateRoute(routeStateInfo.isRouting)
                                if (!routeStateInfo.isRouting) {
                                    latestNaviOrigin = null
                                }
                            }
                        }
                        diagnosticNotes[KEY_NAVI] = if (routeStateInfo.isRouting) {
                            "경로 안내 중 (${routeStateInfo.naviMode})"
                        } else {
                            "경로 안내 대기 중"
                        }
                        if (routeStateInfo.isRouting) {
                            runCatching { naviHelper.getDestinationInfo() }
                            runCatching { naviHelper.getCurrentLocationInfo() }
                        }
                    }

                    override fun onRouteCancelled() {
                        updateRoute(false)
                        synchronized(this@PleosSignalProvider) {
                            latestNaviOrigin = null
                        }
                        publishLocationContext()
                    }

                    override fun onRouteEnded() {
                        updateRoute(false)
                        synchronized(this@PleosSignalProvider) {
                            latestNaviOrigin = null
                        }
                        publishLocationContext()
                    }

                    override fun onError(errorCode: NaviErrorCode) {
                        recordDiagnostic(KEY_NAVI, "NaviHelper 오류 코드: ${errorCode.name}")
                    }
                })
                naviHelperBound = true
                sdkSupportedCache[KEY_NAVI] = true
                diagnostics.report("navigation", "내비 구독 요청 완료 · 목적지 응답 대기")
            }

            // 서비스 연결 직후 현재 목적지/경로/현위치/즐겨찾기/최근목적지 조회
            runCatching {
                naviHelper.getRouteStateInfo()
                naviHelper.getDestinationInfo()
                naviHelper.getCurrentLocationInfo()
                naviHelper.getBookmarkInfo()
                naviHelper.getRecentDestinationInfo()
            }
        }.onFailure { err ->
            recordDiagnostic(KEY_NAVI, "NaviHelper 바인딩 실패: ${err.message}")
        }
    }

    /**
     * 팝업 오픈 시점 또는 수동 새로고침 버튼에서 GPS, Geocoder 및 Pleos NaviHelper 위치·목적지·북마크를 즉시 재동기화합니다.
     */
    fun refreshLocationContext() {
        val ctx = context ?: return
        scope.launch {
            val gpsPlace = VehicleLocationResolver.readPlatformLocationSnapshot(ctx)
            if (gpsPlace != null) {
                synchronized(this@PleosSignalProvider) {
                    latestGpsPlace = gpsPlace
                }
            }
            publishLocationContext(force = true)
        }
        bindingExecutor.execute {
            runCatching {
                val helper = naviHelperInstance ?: return@runCatching
                helper.getCurrentLocationInfo()
                helper.getDestinationInfo()
                helper.getBookmarkInfo()
                helper.getRecentDestinationInfo()
            }
        }
    }

    private fun initPlatformLocationTracking() {
        val ctx = context ?: return
        val shouldRegister = synchronized(this) {
            if (gpsListenerRegistered) false else {
                gpsListenerRegistered = true
                true
            }
        }
        if (!shouldRegister) return
        runCatching {
            val unregister = VehicleLocationResolver.registerPassiveGpsListener(ctx) { location ->
                scope.launch {
                    val gpsPlace = VehicleLocationResolver.readPlatformLocationSnapshot(
                        context = ctx,
                        latitudeOverride = location.latitude,
                        longitudeOverride = location.longitude,
                        sourceLabel = "GPS(${location.provider ?: "system"})"
                    )
                    if (gpsPlace != null) {
                        synchronized(this@PleosSignalProvider) {
                            latestGpsPlace = gpsPlace
                        }
                        publishLocationContext()
                    }
                }
            }
            removeGpsListener = unregister
        }
    }

    private fun publishLocationContext(force: Boolean = false) {
        val signal = synchronized(this) {
            val resolved = VehicleLocationResolver.resolveRouteContext(
                naviOrigin = latestNaviOrigin,
                currentGpsPlace = latestGpsPlace,
                activeDestination = latestNaviDestination,
                bookmarks = naviBookmarks,
                recentDestinations = naviRecentDestinations,
                fallbackDestinationName = lastDestination?.destinationName.orEmpty()
            )
            val fingerprint = listOf(
                resolved.originName,
                resolved.originAddress,
                resolved.originSourceLabel,
                resolved.destinationName,
                resolved.originCandidates.joinToString(",") { it.name },
                resolved.destinationCandidates.joinToString(",") { it.name },
                resolved.suggestedPresetKey ?: ""
            ).joinToString("|")
            if (!force && fingerprint == lastLocationContextKey) {
                return
            }
            lastLocationContextKey = fingerprint
            val coordLabel = resolved.originLatitude?.let { lat ->
                val lon = resolved.originLongitude ?: 0.0
                "(${String.format("%.4f", lat)}, ${String.format("%.4f", lon)})"
            } ?: "좌표 없음"
            diagnostics.reportNaviSnapshot(
                routeState = if (routeActive) "경로 안내 중" else "안내 대기",
                destName = resolved.destinationName.ifBlank { "미지정" },
                gpsDetail = "${resolved.originName.ifBlank { "미확인" }} $coordLabel [${resolved.originSourceLabel}]"
            )
            VehicleSignal.LocationContextUpdated(
                originName = resolved.originName,
                originAddress = resolved.originAddress,
                originLatitude = resolved.originLatitude,
                originLongitude = resolved.originLongitude,
                originPoi = resolved.originPoi,
                originSourceLabel = resolved.originSourceLabel,
                destinationName = resolved.destinationName,
                destinationAddress = resolved.destinationAddress,
                destinationLatitude = resolved.destinationLatitude,
                destinationLongitude = resolved.destinationLongitude,
                originSuggestions = resolved.originCandidates.map { it.name },
                destinationSuggestions = resolved.destinationCandidates.map { it.name },
                suggestedPresetKey = resolved.suggestedPresetKey,
                origin = SignalOrigin.REAL_SDK
            )
        }
        _signals.tryEmit(signal)
    }

    internal fun mapPoiFromDestName(name: String): PoiCategory {
        return when {
            name.contains("마트") || name.contains("백화점") || name.contains("쇼핑") -> PoiCategory.MART_SHOPPING
            name.contains("캠핑") || name.contains("공원") || name.contains("레저") -> PoiCategory.LEISURE_CAMPING
            name.contains("공항") || name.contains("역") || name.contains("터미널") -> PoiCategory.AIRPORT_STATION
            name.contains("회사") || name.contains("오피스") || name.contains("사옥") -> PoiCategory.OFFICE
            name.contains("집") || name.contains("아파트") || name.contains("자택") -> PoiCategory.HOME
            else -> PoiCategory.UNKNOWN
        }
    }

    internal fun mapPleosGearOrNull(gear: VehicleGear?): GearState? {
        return when (gear) {
            VehicleGear.GEAR_PARK -> GearState.PARK
            VehicleGear.GEAR_DRIVE -> GearState.DRIVE
            VehicleGear.GEAR_REVERSE -> GearState.REVERSE
            VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
            else -> null
        }
    }

    internal fun mapPleosGear(gear: VehicleGear?): GearState {
        return mapPleosGearOrNull(gear) ?: GearState.PARK
    }

    internal fun mapCruiseControlMode(state: CruiseControl?): AutonomousDrivingMode {
        return when (state) {
            CruiseControl.ACTIVATED,
            CruiseControl.ENABLED,
            CruiseControl.FORCED_DEACTIVATION_WARNING -> AutonomousDrivingMode.SMART_CRUISE_ADAS
            else -> AutonomousDrivingMode.MANUAL
        }
    }

    /**
     * AAOS VHAL GEAR_SELECTION 정수 값 -> 도메인 GearState 매핑 (미확인 값 null 반환으로 D-N-D-R-P 오판 방지)
     */
    fun mapAaosGearOrNull(rawGear: Int): GearState? {
        return when (rawGear) {
            4 -> GearState.PARK
            8 -> GearState.DRIVE
            2 -> GearState.REVERSE
            1 -> GearState.NEUTRAL
            else -> null
        }
    }

    /**
     * AAOS VHAL GEAR_SELECTION 정수 값 -> 도메인 GearState 매핑 (하위 호환 기본값 PARK)
     */
    fun mapAaosGear(rawGear: Int): GearState {
        return mapAaosGearOrNull(rawGear) ?: GearState.PARK
    }

    /**
     * 도메인별 실제 디바이스/권한/SDK 역량 상태를 검증합니다.
     */
    override fun checkCapabilities(): List<CapabilityReport> {
        val isAutomotiveOs = runCatching {
            context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ?: false
        }.getOrDefault(false)

        val hasDoorPerm = hasAllPermissions(PLEOS_PERM_CAR_DOORS, PLEOS_PERM_CONTROL_CAR_DOORS)
        val hasNaviPerm = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasGearPerm = hasPermission(AAOS_PERM_CAR_POWERTRAIN) || hasPermission(PLEOS_PERM_CAR_DRIVE)
        val hasWindowHvacPerm = hasAllPermissions(
            PLEOS_PERM_CAR_WINDOWS,
            PLEOS_PERM_CONTROL_CAR_WINDOWS,
            PLEOS_PERM_CAR_CLIMATE,
            PLEOS_PERM_CONTROL_CAR_CLIMATE
        )
        val hasSeatPerm = hasPermission(PLEOS_PERM_CAR_SEATS)

        return listOf(
            CapabilityReport(
                domainName = "트렁크/도어 개폐 (Door 영역)",
                sdkName = "Pleos Vehicle SDK (Door)",
                isSupportedOnDevice = sdkSupportedCache[KEY_DOOR] ?: isAutomotiveOs,
                hasPermission = hasDoorPerm,
                note = buildCapabilityNote(KEY_DOOR, isAutomotiveOs, hasDoorPerm, "checkDoorPositionCapabilities & 제어 연동")
            ),
            CapabilityReport(
                domainName = "목적지·경유지 도착 및 경로 상태",
                sdkName = "Pleos NaviHelper SDK",
                isSupportedOnDevice = sdkSupportedCache[KEY_NAVI] ?: isAutomotiveOs,
                hasPermission = hasNaviPerm,
                note = buildCapabilityNote(KEY_NAVI, isAutomotiveOs, hasNaviPerm, "도착/경유지/경로상태 실시간 수신")
            ),
            CapabilityReport(
                domainName = "주차/주행 기어 상태 (P/D/R/N) & ADAS",
                sdkName = "AAOS GEAR_SELECTION + Pleos Gear/DrivingMode",
                isSupportedOnDevice = (sdkSupportedCache[KEY_AAOS_GEAR] == true ||
                    sdkSupportedCache[KEY_PLEOS_GEAR] == true ||
                    isAutomotiveOs),
                hasPermission = hasGearPerm,
                note = diagnosticNotes[KEY_PLEOS_GEAR]
                    ?: diagnosticNotes[KEY_AAOS_GEAR]
                    ?: buildCapabilityNote(KEY_DRIVING_MODE, isAutomotiveOs, hasGearPerm, "GEAR_SELECTION + CruiseControl 연동")
            ),
            CapabilityReport(
                domainName = "실내 창문 개폐 및 공조(HVAC) 온도",
                sdkName = "Pleos Vehicle SDK (Window & HVAC)",
                isSupportedOnDevice = (sdkSupportedCache[KEY_WINDOW] == true ||
                    sdkSupportedCache[KEY_HVAC] == true ||
                    isAutomotiveOs),
                hasPermission = hasWindowHvacPerm,
                note = diagnosticNotes[KEY_HVAC]
                    ?: diagnosticNotes[KEY_WINDOW]
                    ?: buildCapabilityNote(KEY_WINDOW, isAutomotiveOs, hasWindowHvacPerm, "WindowPosition + HvacTemperature 연동")
            ),
            CapabilityReport(
                domainName = "2열 동승자 착석 감지",
                sdkName = "Pleos Vehicle SDK (Seat Occupancy)",
                isSupportedOnDevice = sdkSupportedCache[KEY_SEAT] ?: isAutomotiveOs,
                hasPermission = hasSeatPerm,
                note = buildCapabilityNote(KEY_SEAT, isAutomotiveOs, hasSeatPerm, "ROW_2_LEFT/CENTER/RIGHT 착석 센서 연동")
            )
        )
    }

    private fun buildCapabilityNote(
        key: String,
        isAutomotiveOs: Boolean,
        hasPermission: Boolean,
        readyMessage: String
    ): String {
        diagnosticNotes[key]?.let { return it }
        return when {
            !isAutomotiveOs -> "일반 안드로이드 환경 - 모의 입력(Mock) 병행 동작"
            !hasPermission -> "차량 권한 미승인 - 설정/권한 요청 필요"
            else -> readyMessage
        }
    }

    private fun hasPermission(permission: String): Boolean {
        val ctx = context ?: return false
        return runCatching {
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private fun hasAllPermissions(vararg permissions: String): Boolean {
        return permissions.all { hasPermission(it) }
    }

    private fun sameDestination(expected: String?, actual: String): Boolean =
        expected?.filterNot(Char::isWhitespace)?.equals(actual.filterNot(Char::isWhitespace), ignoreCase = true) == true

    @Synchronized
    private fun offer(signal: VehicleSignal) {
        when (signal) {
            is VehicleSignal.DoorStateChanged -> {
                val forceResync = doorResyncZones.remove(signal.zone)
                if (!forceResync && latestDoor[signal.zone] == signal.isOpen) return
                latestDoor[signal.zone] = signal.isOpen
                if (signal.zone == DoorZone.TRUNK) {
                    diagnostics.report(
                        "door",
                        if (signal.isOpen) "트렁크 열림 감지" else "트렁크 닫힘 감지",
                        receivedDoor = true
                    )
                }
            }
            is VehicleSignal.GearChanged -> {
                if (lastGear == signal.gear) return
                lastGear = signal.gear
                if (signal.gear == GearState.DRIVE || signal.gear == GearState.REVERSE) {
                    lastArrivalName = null
                }
                diagnostics.report("gear", "${signal.gear.label} · 실제 차량 신호")
            }
            is VehicleSignal.DestinationUpdated -> {
                if (lastDestination?.destinationName == signal.destinationName) return
                lastDestination = signal
            }
            is VehicleSignal.DestinationArrived -> {
                if (lastArrivalName == signal.destinationName) return
                lastArrivalName = signal.destinationName
            }
            else -> Unit
        }
        _signals.tryEmit(signal)
        if (signal is VehicleSignal.DoorStateChanged &&
            signal.zone == DoorZone.TRUNK &&
            !signal.isOpen
        ) {
            refreshLocationContext()
        }
    }

    private fun recordDiagnostic(domain: String, detail: String) {
        diagnosticNotes[domain] = detail
        val bridgeDomain = when (domain) {
            KEY_NAVI -> "navigation"
            KEY_AAOS_GEAR, KEY_PLEOS_GEAR, KEY_DRIVING_MODE -> "gear"
            else -> "door"
        }
        diagnostics.report(bridgeDomain, detail)
        runCatching { android.util.Log.w(TAG, "[$domain] $detail") }
    }

    /**
     * 실제 SDK 콜백 또는 테스트에서 엔진으로 이벤트를 전달하는 엔트리포인트
     */
    fun onRealSdkSignalReceived(signal: VehicleSignal) {
        _signals.tryEmit(signal)
    }

    companion object {
        private const val TAG = "PleosSignalProvider"
        const val AAOS_GEAR_SELECTION_PROPERTY_ID = 289408000 // 0x11400400

        const val AAOS_PERM_CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"
        const val PLEOS_PERM_CAR_DRIVE = "pleos.car.permission.CAR_DRIVE"
        const val PLEOS_PERM_CAR_DOORS = "pleos.car.permission.CAR_DOORS"
        const val PLEOS_PERM_CONTROL_CAR_DOORS = "pleos.car.permission.CONTROL_CAR_DOORS"
        const val PLEOS_PERM_CAR_WINDOWS = "pleos.car.permission.CAR_WINDOWS"
        const val PLEOS_PERM_CONTROL_CAR_WINDOWS = "pleos.car.permission.CONTROL_CAR_WINDOWS"
        const val PLEOS_PERM_CAR_CLIMATE = "pleos.car.permission.CAR_CLIMATE"
        const val PLEOS_PERM_CONTROL_CAR_CLIMATE = "pleos.car.permission.CONTROL_CAR_CLIMATE"
        const val PLEOS_PERM_CAR_SEATS = "pleos.car.permission.CAR_SEATS"
        const val PLEOS_PERM_CONTROL_CAR_SEATS = "pleos.car.permission.CONTROL_CAR_SEATS"

        val REQUIRED_VEHICLE_PERMISSIONS = arrayOf(
            PLEOS_PERM_CAR_DRIVE,
            PLEOS_PERM_CAR_DOORS,
            PLEOS_PERM_CONTROL_CAR_DOORS,
            PLEOS_PERM_CAR_WINDOWS,
            PLEOS_PERM_CONTROL_CAR_WINDOWS,
            PLEOS_PERM_CAR_CLIMATE,
            PLEOS_PERM_CONTROL_CAR_CLIMATE,
            PLEOS_PERM_CAR_SEATS,
            PLEOS_PERM_CONTROL_CAR_SEATS
        )

        private const val KEY_AAOS_GEAR = "aaos_gear"
        private const val KEY_PLEOS_GEAR = "pleos_gear"
        private const val KEY_DOOR = "door"
        private const val KEY_WINDOW = "window"
        private const val KEY_HVAC = "hvac"
        private const val KEY_SEAT = "seat"
        private const val KEY_DRIVING_MODE = "driving_mode"
        private const val KEY_NAVI = "navi"
    }
}
