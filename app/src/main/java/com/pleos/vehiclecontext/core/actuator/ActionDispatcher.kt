package com.pleos.vehiclecontext.core.actuator

import com.pleos.vehiclecontext.core.model.ContextAction
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Layer 4 (Action & UX Dispatcher):
 * 시나리오 엔진에서 결정된 액션을 차량 내 화면(Visual Card), 음성 합성(TTS),
 * 그리고 차량 하드웨어 제어(Vehicle SDK)로 분배하고 실행 로그를 관리합니다.
 */
class ActionDispatcher(
    private val vehicleController: VehicleController? = null,
    private val ttsSpeaker: ((String) -> Unit)? = null
) {
    constructor(ttsSpeaker: ((String) -> Unit)?) : this(vehicleController = null, ttsSpeaker = ttsSpeaker)

    private val _lastVoiceAnnouncement = MutableStateFlow<String?>(null)
    val lastVoiceAnnouncement: StateFlow<String?> = _lastVoiceAnnouncement.asStateFlow()

    private val _lastControlExecution = MutableStateFlow<String?>(null)
    val lastControlExecution: StateFlow<String?> = _lastControlExecution.asStateFlow()

    fun dispatchVoiceIfPresent(action: ContextAction) {
        if (action.voiceTtsMessage.isNotBlank()) {
            _lastVoiceAnnouncement.value = action.voiceTtsMessage
            ttsSpeaker?.invoke(action.voiceTtsMessage)
        }
    }

    fun executeVehicleControl(command: VehicleControlCommand): String {
        when (command) {
            VehicleControlCommand.OPEN_TRUNK,
            VehicleControlCommand.HOLD_AVP_AND_OPEN_TRUNK -> {
                vehicleController?.openTrunk()
            }
            VehicleControlCommand.CLOSE_TRUNK -> {
                vehicleController?.closeTrunk()
            }
            VehicleControlCommand.CLOSE_ALL_WINDOWS -> {
                vehicleController?.closeAllWindows()
            }
            VehicleControlCommand.ACTIVATE_CABIN_COOLING -> {
                vehicleController?.activateCabinCooling()
            }
            else -> {
                // 상시 보관 및 자율주행 모드 변경 등 소프트웨어/엔진 레벨 제어
            }
        }

        val message = "[차량 제어 실행] ${command.buttonLabel}: ${command.actionFeedback}"
        _lastControlExecution.value = message
        return message
    }
}

