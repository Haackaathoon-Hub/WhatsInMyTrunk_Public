package com.pleos.vehiclecontext.core.runtime

import android.content.Context
import com.pleos.vehiclecontext.core.actuator.ActionDispatcher
import com.pleos.vehiclecontext.core.actuator.VehicleController
import com.pleos.vehiclecontext.core.ai.PleosAiContextAgent
import com.pleos.vehiclecontext.core.engine.SharedPrefsSessionPersistence
import com.pleos.vehiclecontext.core.engine.TripContextEngine
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.settings.SharedPrefsAppSettingsStore
import com.pleos.vehiclecontext.core.signal.MockSignalProvider
import com.pleos.vehiclecontext.core.signal.PleosSignalProvider
import com.pleos.vehiclecontext.gleo.GleoCargoActions
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousCargoComfortScenario
import com.pleos.vehiclecontext.scenarios.autonomous.AutonomousValetDropoffScenario
import com.pleos.vehiclecontext.scenarios.cabin.CargoTemperatureGuardScenario
import com.pleos.vehiclecontext.scenarios.cabin.RearSeatItemScenario
import com.pleos.vehiclecontext.scenarios.driving.CargoSafeDrivingScenario
import com.pleos.vehiclecontext.scenarios.exit.ExitIntegrityScenario
import com.pleos.vehiclecontext.scenarios.trunk.DestinationReverseReminderScenario
import com.pleos.vehiclecontext.scenarios.trunk.TrunkReminderScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 실제 AAOS 차량 환경에서 백그라운드 서비스(VehicleContextMonitorService),
 * 하차 시점 오버레이 모달(PreExitPromptActivity), 시스템 헤드업 알림(HUN) 리시버,
 * 그리고 설정/시연 콘솔(MainActivity)이 동일한 컨텍스트 엔진 인스턴스를 공유하도록 관리하는 싱글톤 런타임입니다.
 */
object VehicleContextRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var initialized = false

    private val _activityVisible = MutableStateFlow(false)
    val activityVisible: StateFlow<Boolean> = _activityVisible.asStateFlow()

    lateinit var settingsStore: SharedPrefsAppSettingsStore
        private set

    lateinit var pleosSignalProvider: PleosSignalProvider
        private set

    lateinit var mockSignalProvider: MockSignalProvider
        private set

    lateinit var vehicleController: VehicleController
        private set

    lateinit var actionDispatcher: ActionDispatcher
        private set

    lateinit var engine: TripContextEngine
        private set

    lateinit var gleoActions: GleoCargoActions
        private set

    internal lateinit var arrivalDemo: EmulatorArrivalDemo
        private set

    private var ttsHook: ((String) -> Unit)? = null

    fun setActivityVisible(visible: Boolean) {
        _activityVisible.value = visible
    }

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences("pleos_trip_session_prefs", Context.MODE_PRIVATE)
            val persistence = SharedPrefsSessionPersistence(prefs)
            settingsStore = SharedPrefsAppSettingsStore(appCtx)

            val signalProvider = PleosSignalProvider(appCtx)
            pleosSignalProvider = signalProvider
            val controller = VehicleController(
                context = appCtx,
                sharedVehicleProvider = { signalProvider.vehicleHandle() },
                doorBoundsProvider = { signalProvider.trunkPositionBounds() },
                onDoorCommandDispatched = {
                    signalProvider.markDoorCommandIssued(DoorZone.TRUNK)
                    signalProvider.refreshNow()
                }
            )
            vehicleController = controller
            mockSignalProvider = MockSignalProvider()
            actionDispatcher = ActionDispatcher(
                vehicleController = controller,
                ttsSpeaker = { message ->
                    ttsHook?.invoke(message)
                }
            )

            val plugins = listOf(
                AutonomousValetDropoffScenario(),
                AutonomousCargoComfortScenario(),
                CargoSafeDrivingScenario(),
                DestinationReverseReminderScenario(),
                CargoTemperatureGuardScenario(),
                TrunkReminderScenario(),
                RearSeatItemScenario(),
                ExitIntegrityScenario()
            )

            engine = TripContextEngine(
                plugins = plugins,
                persistence = persistence,
                actionDispatcher = actionDispatcher,
                aiService = PleosAiContextAgent(),
                settingsProvider = { settingsStore.settings.value }
            )

            gleoActions = GleoCargoActions(
                engine = engine,
                settingsProvider = { settingsStore.settings.value },
                activeNavigationDestination = { pleosSignalProvider.activeDestination() }
            )

            arrivalDemo = EmulatorArrivalDemo(
                scope = scope,
                available = canRunEmulatorDemo(appCtx),
                target = pleosSignalProvider.routeTarget,
                session = engine.session,
                requestFix = { id, route ->
                    android.util.Log.i(
                        "PleosDemo",
                        "GPS_DEMO_REQUEST id=$id lat=${route.latitude} lon=${route.longitude}"
                    )
                },
                simulateArrival = { route ->
                    engine.processSignal(
                        VehicleSignal.DestinationArrived(
                            destinationName = route.name,
                            isFinalDestination = true,
                            origin = SignalOrigin.MOCK_SIMULATOR
                        )
                    )
                }
            )

            scope.launch {
                merge(pleosSignalProvider.signals, mockSignalProvider.signals).collect { signal ->
                    engine.processSignal(signal)
                }
            }

            initialized = true
        }
    }

    fun registerTtsSpeaker(speaker: ((String) -> Unit)?) {
        ttsHook = speaker
    }
}

