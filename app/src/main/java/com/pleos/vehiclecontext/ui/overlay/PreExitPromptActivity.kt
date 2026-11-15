package com.pleos.vehiclecontext.ui.overlay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pleos.vehiclecontext.MainActivity
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime
import com.pleos.vehiclecontext.ui.theme.PleosTheme

/**
 * 실제 AAOS 디스플레이에서 시스템 오버레이 권한이 없거나 헤드업 알림을 탭했을 때
 * 현재 화면 위에 반투명 카드로 뜨는 통합 차량 알림 모달 Activity.
 * 하차 전 짐 알림(PreExit)과 트렁크 적재 감지 알림 모두 [UnifiedVehicleAlertPopup] 규격으로 통일 렌더링합니다.
 */
class PreExitPromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        VehicleContextRuntime.ensureInitialized(applicationContext)

        setContent {
            PleosTheme {
                val activePrompt by VehicleContextRuntime.engine.activePrompt.collectAsState()
                val session by VehicleContextRuntime.engine.session.collectAsState()
                val loadingReqId = session.pendingLoadingPromptRequestId
                    ?.takeIf { it.startsWith("load-") && !session.unloadConfirmationNeeded }

                LaunchedEffect(activePrompt, loadingReqId) {
                    if (activePrompt == null && loadingReqId == null) {
                        finish()
                    }
                }

                val prompt = activePrompt
                when {
                    prompt != null -> {
                        PreExitOverlayContent(
                            prompt = prompt,
                            session = session,
                            onAcknowledge = {
                                VehicleContextRuntime.engine.acknowledgeActivePrompt()
                                finish()
                            },
                            onExecuteControl = { cmd ->
                                VehicleContextRuntime.engine.executePromptControl(cmd)
                                finish()
                            },
                            onDismiss = {
                                VehicleContextRuntime.engine.dismissActivePrompt()
                                finish()
                            }
                        )
                    }
                    loadingReqId != null -> {
                        UnifiedVehicleAlertPopup(
                            spec = buildTrunkLoadingDetectedSpec(session),
                            onPrimaryClick = {
                                VehicleContextRuntime.engine.promoteLoadingAlertToManualForm()
                                startActivity(
                                    Intent(this@PreExitPromptActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    }
                                )
                                finish()
                            },
                            onSecondaryClick = {
                                VehicleContextRuntime.engine.skipLoadingRegistration()
                                finish()
                            },
                            onDismiss = {
                                VehicleContextRuntime.engine.skipLoadingRegistration()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}
