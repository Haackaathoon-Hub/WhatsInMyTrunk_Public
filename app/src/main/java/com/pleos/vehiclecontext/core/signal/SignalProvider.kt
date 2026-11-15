package com.pleos.vehiclecontext.core.signal

import com.pleos.vehiclecontext.core.model.VehicleSignal
import kotlinx.coroutines.flow.Flow

/**
 * 리더 기획안 슬라이드 6: "Pleos 연동 범위와 확인 항목"
 * 각 SDK/속성의 지원 여부와 권한 상태를 표현합니다.
 */
data class CapabilityReport(
    val domainName: String,
    val sdkName: String,
    val isSupportedOnDevice: Boolean,
    val hasPermission: Boolean,
    val note: String
)

/**
 * 실제 차량 SDK(Pleos Vehicle SDK, NaviHelper, AAOS CarPropertyManager)와
 * 모의 시뮬레이터(MockSignalProvider)를 통합하는 추상 인터페이스
 */
interface SignalProvider {
    val signals: Flow<VehicleSignal>
    fun checkCapabilities(): List<CapabilityReport>
}

