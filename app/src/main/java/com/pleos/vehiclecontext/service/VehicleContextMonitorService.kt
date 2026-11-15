package com.pleos.vehiclecontext.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.speech.tts.TextToSpeech
import com.pleos.vehiclecontext.MainActivity
import com.pleos.vehiclecontext.R
import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime
import com.pleos.vehiclecontext.ui.overlay.PreExitPromptActivity
import com.pleos.vehiclecontext.ui.overlay.PreExitSystemOverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 실제 현대차 AAOS 환경에서 내비게이션이나 미디어가 화면에 떠 있는 동안
 * 백그라운드에서 차량 센서 신호(트렁크 개폐, 도착, P단)를 감시하다가,
 * 하차 조건 충족 시:
 * 1) AAOS 시스템 헤드업 알림(Heads-Up Notification) 배너 표시
 * 2) P단 정차 상태에서 반투명 하차 확인 모달(PreExitPromptActivity) 오버레이 호출
 * 3) 차량 스피커(TTS) 음성 브리핑 출력
 * 을 수행하는 핵심 백그라운드 서비스입니다.
 */
class VehicleContextMonitorService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastShownPromptTimestamp: Long = 0L
    private var lastShownLoadingReqId: String? = null
    private lateinit var systemOverlayHost: PreExitSystemOverlayHost

    override fun onCreate() {
        super.onCreate()
        VehicleContextRuntime.ensureInitialized(applicationContext)
        systemOverlayHost = PreExitSystemOverlayHost(applicationContext)
        tts = TextToSpeech(applicationContext, this)
        VehicleContextRuntime.registerTtsSpeaker { message ->
            if (isTtsReady) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "pleos_service_tts")
            }
        }

        createNotificationChannels()
        runCatching {
            val notification = buildMonitorForegroundNotification()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    MONITOR_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(MONITOR_NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            android.util.Log.w("MonitorService", "startForeground failed: ${e.message}", e)
        }

        // 컨텍스트 엔진의 activePrompt 및 백그라운드 트렁크 적재 감지를 통합 감시하여 HUN + 통합 팝업 호출
        serviceScope.launch {
            combine(
                VehicleContextRuntime.engine.activePrompt,
                VehicleContextRuntime.engine.session,
                VehicleContextRuntime.activityVisible
            ) { action, session, isAppVisible ->
                Triple(action, session, isAppVisible)
            }.collect { (action, session, isAppVisible) ->
                val settings = VehicleContextRuntime.settingsStore.settings.value
                val loadingReqId = session.pendingLoadingPromptRequestId
                    ?.takeIf { it.startsWith("load-") && !session.unloadConfirmationNeeded }

                when {
                    action != null && settings.arrivalAlertEnabled -> {
                        if (action.triggeredAtMs != lastShownPromptTimestamp) {
                            lastShownPromptTimestamp = action.triggeredAtMs
                            showHeadsUpNotification(action)

                            // P단 정차 시(또는 자율주행 허용 시) 현재 내비/홈 화면 위에 통합 하차 팝업 오버레이 표시
                            if (session.currentGear == GearState.PARK || action.canDisplayInAutonomousDriving) {
                                showUnifiedOverlayOrFallbackModal()
                            }
                        }
                    }
                    action == null && loadingReqId != null && !isAppVisible -> {
                        if (loadingReqId != lastShownLoadingReqId) {
                            lastShownLoadingReqId = loadingReqId
                            showLoadingDetectedNotification(session.destinationName)
                            showUnifiedOverlayOrFallbackModal()
                        }
                    }
                    else -> {
                        if (action == null) lastShownPromptTimestamp = 0L
                        if (loadingReqId == null) lastShownLoadingReqId = null
                        if (action == null && loadingReqId == null) {
                            systemOverlayHost.dismiss()
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(PROMPT_NOTIFICATION_ID)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SIMULATE_BACKGROUND_ARRIVAL_AFTER_DELAY -> {
                val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 3500L)
                serviceScope.launch {
                    // 1. 출발 전 트렁크 적재 기록 및 주행 중 상태로 전환
                    VehicleContextRuntime.engine.resetTripSession("집 (성수동 자택)")
                    VehicleContextRuntime.mockSignalProvider.simulateTrunkOpenAndClose(
                        poi = PoiCategory.MART_SHOPPING,
                        cargoTag = CargoTag.FRESH_FOOD,
                        origin = SignalOrigin.MOCK_SIMULATOR
                    )
                    VehicleContextRuntime.mockSignalProvider.emit(
                        VehicleSignal.VoiceCargoNoteReceived(
                            utterance = "이마트에서 아이스크림이랑 생일 케이크, 와인 두 병 샀어",
                            currentLocationPoi = PoiCategory.MART_SHOPPING
                        )
                    )
                    VehicleContextRuntime.mockSignalProvider.emit(
                        VehicleSignal.GearChanged(GearState.DRIVE, SignalOrigin.MOCK_SIMULATOR)
                    )

                    // 사용자가 홈 화면이나 내비게이션 화면을 보고 있는 상태에서 지정 시간 대기
                    delay(delayMs)

                    // 2. 목적지 도착 + P단 주차 신호 발생 -> 내비/홈 화면 위에 하차 모달 및 HUN 즉시 팝업!
                    VehicleContextRuntime.mockSignalProvider.emit(
                        VehicleSignal.DestinationArrived(
                            destinationName = "집 (성수동 자택)",
                            isFinalDestination = true,
                            origin = SignalOrigin.MOCK_SIMULATOR
                        )
                    )
                    VehicleContextRuntime.mockSignalProvider.emit(
                        VehicleSignal.GearChanged(GearState.PARK, SignalOrigin.MOCK_SIMULATOR)
                    )
                }
            }
        }
        return START_STICKY
    }

    private fun showUnifiedOverlayOrFallbackModal() {
        val shownOverWindow = systemOverlayHost.show(
            onAcknowledge = {
                VehicleContextRuntime.engine.acknowledgeActivePrompt()
                systemOverlayHost.dismiss()
            },
            onExecuteControl = { cmd ->
                VehicleContextRuntime.engine.executePromptControl(cmd)
                systemOverlayHost.dismiss()
            },
            onDismiss = {
                VehicleContextRuntime.engine.dismissActivePrompt()
                systemOverlayHost.dismiss()
            }
        )
        if (!shownOverWindow) {
            launchPreExitOverlayModal()
        }
    }

    private fun launchPreExitOverlayModal() {
        val overlayIntent = Intent(this, PreExitPromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching {
            startActivity(overlayIntent)
        }.onFailure { err ->
            android.util.Log.w("MonitorService", "Background Activity launch blocked: ${err.message}")
        }
    }

    private fun showLoadingDetectedNotification(destinationName: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openModalIntent = Intent(this, PreExitPromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            105,
            openModalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val appLogo = runCatching { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }.getOrNull()
        val destLabel = destinationName.ifBlank { "목적지" }
        val notification = Notification.Builder(this, CHANNEL_PRE_EXIT_HIGH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(appLogo)
            .setSubText("WHAT'S IN MY TRUNK?")
            .setContentTitle("트렁크 적재 감지 — 짐이 실렸나요?")
            .setContentText("목적지 '$destLabel' 도착 시 하차 전 알림을 띄워드려요. 탭하여 직접 추가할 수 있습니다.")
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("목적지 '$destLabel' 도착 후 P단 전환 시 트렁크 짐을 잊지 않도록 알려드립니다.")
            )
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .setAutoCancel(true)
            .build()
        nm.notify(PROMPT_NOTIFICATION_ID, notification)
    }

    private fun showHeadsUpNotification(action: ContextAction) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openModalIntent = Intent(this, PreExitPromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openModalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ackIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = NotificationActionReceiver.ACTION_ACKNOWLEDGE
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openTrunkIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = NotificationActionReceiver.ACTION_OPEN_TRUNK
        }
        val openTrunkPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            openTrunkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appLogo = runCatching { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }.getOrNull()
        val notification = Notification.Builder(this, CHANNEL_PRE_EXIT_HIGH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(appLogo)
            .setSubText("WHAT'S IN MY TRUNK?")
            .setContentTitle(action.headline)
            .setContentText(action.bodyMessage)
            .setStyle(Notification.BigTextStyle().bigText(action.bodyMessage))
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_edit,
                    action.primaryAckLabel,
                    ackPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_lock_lock,
                    "지금 트렁크 열기",
                    openTrunkPendingIntent
                ).build()
            )
            .build()

        nm.notify(PROMPT_NOTIFICATION_ID, notification)
    }

    private fun buildMonitorForegroundNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_MONITOR_LOW)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Pleos 차량 컨텍스트 감시 중")
            .setContentText("트렁크·도어 개폐 및 목적지 도착/P단 신호를 백그라운드에서 모니터링합니다.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR_LOW,
            "차량 컨텍스트 백그라운드 감시",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "주행 중 화면을 가리지 않고 차량 센서 맥락을 추적합니다."
        }

        val promptChannel = NotificationChannel(
            CHANNEL_PRE_EXIT_HIGH,
            "하차 전 트렁크·차량 확인 알림 (Heads-Up)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "목적지 도착 및 P단 주차 시 내비게이션 화면 위에 하차 전 안내 배너를 표시합니다."
        }

        nm.createNotificationChannel(monitorChannel)
        nm.createNotificationChannel(promptChannel)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale.KOREAN)
            isTtsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                res != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onDestroy() {
        systemOverlayHost.dismiss()
        VehicleContextRuntime.registerTtsSpeaker(null)
        tts?.stop()
        tts?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_MONITOR_LOW = "pleos_vehicle_monitor_channel"
        const val CHANNEL_PRE_EXIT_HIGH = "pleos_pre_exit_hun_channel"
        const val MONITOR_NOTIFICATION_ID = 1001
        const val PROMPT_NOTIFICATION_ID = 1002

        const val ACTION_SIMULATE_BACKGROUND_ARRIVAL_AFTER_DELAY =
            "com.pleos.vehiclecontext.ACTION_SIMULATE_BACKGROUND_ARRIVAL_AFTER_DELAY"
        const val EXTRA_DELAY_MS = "extra_delay_ms"
    }
}

