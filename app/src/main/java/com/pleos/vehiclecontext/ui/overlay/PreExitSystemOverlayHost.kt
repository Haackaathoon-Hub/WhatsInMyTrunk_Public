package com.pleos.vehiclecontext.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * SYSTEM_ALERT_WINDOW 권한이 허용된 차량 환경에서 앱 UI가 완전히 닫혀 있거나
 * 내비게이션/홈 등 다른 앱을 보고 있는 중에도 현재 화면 위로 즉시 [PreExitOverlayContent]를
 * 띄우기 위한 WindowManager(TYPE_APPLICATION_OVERLAY) 호스트입니다.
 */
class PreExitSystemOverlayHost(private val serviceContext: Context) {

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun canShowSystemOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(serviceContext)
    }

    fun show(
        onAcknowledge: () -> Unit,
        onExecuteControl: (VehicleControlCommand) -> Unit,
        onDismiss: () -> Unit
    ): Boolean {
        if (!canShowSystemOverlay()) return false
        dismiss()

        val wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val owner = OverlayLifecycleOwner().apply { onCreate() }

        val composeView = ComposeView(serviceContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                PleosTheme {
                    val activePrompt: ContextAction? by VehicleContextRuntime.engine.activePrompt.collectAsState()
                    val session by VehicleContextRuntime.engine.session.collectAsState()
                    val prompt = activePrompt
                    val loadingReqId = session.pendingLoadingPromptRequestId
                    when {
                        prompt != null -> {
                            PreExitOverlayContent(
                                prompt = prompt,
                                session = session,
                                onAcknowledge = onAcknowledge,
                                onExecuteControl = onExecuteControl,
                                onDismiss = onDismiss
                            )
                        }
                        loadingReqId != null && loadingReqId.startsWith("load-") && !session.unloadConfirmationNeeded -> {
                            UnifiedVehicleAlertPopup(
                                spec = buildTrunkLoadingDetectedSpec(session),
                                onPrimaryClick = {
                                    // 백그라운드에서 '직접 추가' 선택 시 트렁크 자동 개방 및 감지 팝업 재노출 없이 등록 폼으로 바로 진입
                                    VehicleContextRuntime.engine.promoteLoadingAlertToManualForm()
                                    val launchIntent = serviceContext.packageManager
                                        ?.getLaunchIntentForPackage(serviceContext.packageName)
                                        ?.apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        }
                                    if (launchIntent != null) {
                                        runCatching { serviceContext.startActivity(launchIntent) }
                                    }
                                    dismiss()
                                },
                                onSecondaryClick = {
                                    VehicleContextRuntime.engine.skipLoadingRegistration()
                                    dismiss()
                                },
                                onDismiss = {
                                    VehicleContextRuntime.engine.skipLoadingRegistration()
                                    dismiss()
                                }
                            )
                        }
                    }
                }
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        return runCatching {
            owner.onStart()
            owner.onResume()
            wm.addView(composeView, params)
            overlayView = composeView
            lifecycleOwner = owner
            true
        }.onFailure {
            owner.onDestroy()
        }.getOrDefault(false)
    }

    fun dismiss() {
        val view = overlayView
        val owner = lifecycleOwner
        overlayView = null
        lifecycleOwner = null

        if (view != null) {
            val wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching {
                if (view.isAttachedToWindow) {
                    wm?.removeViewImmediate(view)
                }
            }
        }
        owner?.onDestroy()
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = registry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun onStart() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun onResume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }
}
