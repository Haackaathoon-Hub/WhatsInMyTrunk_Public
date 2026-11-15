package com.pleos.vehiclecontext.core.signal

import com.pleos.vehiclecontext.core.model.CargoTag
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 시연 및 테스트를 위한 모의 신호 주입기 (Mock Signal Injector).
 * 리더 기획안 슬라이드 7의 6가지 검증 시나리오와 확장 시나리오를 즉시 재현할 수 있습니다.
 */
class MockSignalProvider : SignalProvider {

    private val _signals = MutableSharedFlow<VehicleSignal>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val signals: Flow<VehicleSignal> = _signals.asSharedFlow()

    override fun checkCapabilities(): List<CapabilityReport> = listOf(
        CapabilityReport(
            domainName = "시연용 모의 신호 주입기",
            sdkName = "MockSignalProvider",
            isSupportedOnDevice = true,
            hasPermission = true,
            note = "실제 입력과 구분된 [모의 시뮬레이터 입력] 태그로 모든 센서 이벤트를 재현"
        )
    )

    fun emit(signal: VehicleSignal) {
        _signals.tryEmit(signal)
    }

    /**
     * 트렁크 열림 -> 닫힘 시퀀스를 한 번에 발생시키는 헬퍼
     */
    fun simulateTrunkOpenAndClose(
        poi: PoiCategory = PoiCategory.MART_SHOPPING,
        cargoTag: CargoTag = CargoTag.FRESH_FOOD,
        origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR
    ) {
        emit(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = true,
                currentLocationPoi = poi,
                cargoTag = cargoTag,
                origin = origin
            )
        )
        emit(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.TRUNK,
                isOpen = false,
                currentLocationPoi = poi,
                cargoTag = cargoTag,
                origin = origin
            )
        )
    }

    /**
     * 2열 도어 열림 -> 닫힘 (뒷좌석에 가방/세탁물 등을 싣는 맥락)
     */
    fun simulateRearDoorLoadItem(
        poi: PoiCategory = PoiCategory.OFFICE,
        origin: SignalOrigin = SignalOrigin.MOCK_SIMULATOR
    ) {
        emit(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.REAR_RIGHT,
                isOpen = true,
                currentLocationPoi = poi,
                cargoTag = CargoTag.GENERAL,
                origin = origin
            )
        )
        emit(
            VehicleSignal.DoorStateChanged(
                zone = DoorZone.REAR_RIGHT,
                isOpen = false,
                currentLocationPoi = poi,
                cargoTag = CargoTag.GENERAL,
                origin = origin
            )
        )
    }
}

