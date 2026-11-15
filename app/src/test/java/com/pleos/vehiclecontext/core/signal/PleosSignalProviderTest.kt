package com.pleos.vehiclecontext.core.signal

import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.pleos.vehiclecontext.core.actuator.VehicleController
import com.pleos.vehiclecontext.core.model.AutonomousDrivingMode
import com.pleos.vehiclecontext.core.model.DoorZone
import com.pleos.vehiclecontext.core.model.GearState
import com.pleos.vehiclecontext.core.model.PoiCategory
import com.pleos.vehiclecontext.core.model.SignalOrigin
import com.pleos.vehiclecontext.core.model.VehicleSignal
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosSignalProviderTest {

    @Test
    fun `mapAaosGear maps standard VHAL integer codes accurately`() {
        val provider = PleosSignalProvider(context = null)

        assertEquals(GearState.PARK, provider.mapAaosGear(4))
        assertEquals(GearState.DRIVE, provider.mapAaosGear(8))
        assertEquals(GearState.REVERSE, provider.mapAaosGear(2))
        assertEquals(GearState.NEUTRAL, provider.mapAaosGear(1))
        assertEquals(GearState.PARK, provider.mapAaosGear(999))
    }

    @Test
    fun `mapPleosGear maps Pleos VehicleGear enum values accurately`() {
        val provider = PleosSignalProvider(context = null)

        assertEquals(GearState.PARK, provider.mapPleosGear(VehicleGear.GEAR_PARK))
        assertEquals(GearState.DRIVE, provider.mapPleosGear(VehicleGear.GEAR_DRIVE))
        assertEquals(GearState.REVERSE, provider.mapPleosGear(VehicleGear.GEAR_REVERSE))
        assertEquals(GearState.NEUTRAL, provider.mapPleosGear(VehicleGear.GEAR_NEUTRAL))
        assertEquals(GearState.PARK, provider.mapPleosGear(null))
    }

    @Test
    fun `mapCruiseControlMode maps active ADAS and manual states accurately`() {
        val provider = PleosSignalProvider(context = null)

        assertEquals(
            AutonomousDrivingMode.SMART_CRUISE_ADAS,
            provider.mapCruiseControlMode(CruiseControl.ACTIVATED)
        )
        assertEquals(
            AutonomousDrivingMode.SMART_CRUISE_ADAS,
            provider.mapCruiseControlMode(CruiseControl.ENABLED)
        )
        assertEquals(
            AutonomousDrivingMode.SMART_CRUISE_ADAS,
            provider.mapCruiseControlMode(CruiseControl.FORCED_DEACTIVATION_WARNING)
        )
        assertEquals(
            AutonomousDrivingMode.MANUAL,
            provider.mapCruiseControlMode(CruiseControl.USER_OVERRIDE)
        )
        assertEquals(
            AutonomousDrivingMode.MANUAL,
            provider.mapCruiseControlMode(CruiseControl.SUSPENDED)
        )
        assertEquals(
            AutonomousDrivingMode.MANUAL,
            provider.mapCruiseControlMode(null)
        )
    }

    @Test
    fun `mapPoiFromDestName classifies Korean POI names accurately`() {
        val provider = PleosSignalProvider(context = null)

        assertEquals(PoiCategory.MART_SHOPPING, provider.mapPoiFromDestName("이마트 성수점"))
        assertEquals(PoiCategory.HOME, provider.mapPoiFromDestName("우리집 아파트"))
        assertEquals(PoiCategory.AIRPORT_STATION, provider.mapPoiFromDestName("인천공항 제1터미널"))
        assertEquals(PoiCategory.OFFICE, provider.mapPoiFromDestName("현대차 양재 사옥"))
        assertEquals(PoiCategory.LEISURE_CAMPING, provider.mapPoiFromDestName("난지 캠핑장"))
        assertEquals(PoiCategory.UNKNOWN, provider.mapPoiFromDestName("동네 카페"))
    }

    @Test
    fun `checkCapabilities reports all 5 vehicle domains safely when context is null`() {
        val provider = PleosSignalProvider(context = null)
        provider.refreshVehicleBindings()

        val reports = provider.checkCapabilities()

        assertEquals(5, reports.size)
        assertTrue(reports.all { !it.hasPermission && !it.isSupportedOnDevice })
        assertTrue(reports.any { it.sdkName.contains("Seat Occupancy") })
        assertTrue(reports.any { it.sdkName.contains("DrivingMode") })
        assertTrue(reports.any { it.sdkName.contains("NaviHelper") })
    }

    @Test
    fun `onRealSdkSignalReceived emits real vehicle signals onto flow`() = runTest {
        val provider = PleosSignalProvider(context = null)

        val deferred = async { provider.signals.first() }
        testScheduler.advanceUntilIdle()

        val expected = VehicleSignal.CabinStatusUpdated(
            rearSeatOccupied = true,
            openWindowsCount = 2,
            cabinTempCelsius = 27.5f,
            origin = SignalOrigin.REAL_SDK
        )
        provider.onRealSdkSignalReceived(expected)

        assertEquals(expected, deferred.await())

        val doorDeferred = async { provider.signals.first() }
        testScheduler.advanceUntilIdle()
        val doorSignal = VehicleSignal.DoorStateChanged(
            zone = DoorZone.TRUNK,
            isOpen = true,
            origin = SignalOrigin.REAL_SDK
        )
        provider.onRealSdkSignalReceived(doorSignal)
        assertEquals(doorSignal, doorDeferred.await())
    }

    @Test
    fun `VehicleController denies control safely without runtime permissions or hardware context`() {
        val controller = VehicleController(context = null)

        assertFalse(controller.openTrunk())
        assertTrue(controller.lastError?.contains(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_DOORS) == true)

        assertFalse(controller.closeTrunk())
        assertTrue(controller.lastError?.contains(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_DOORS) == true)

        assertFalse(controller.closeAllWindows())
        assertTrue(controller.lastError?.contains(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_WINDOWS) == true)

        assertFalse(controller.activateCabinCooling(20.0f))
        assertTrue(controller.lastError?.contains(PleosSignalProvider.PLEOS_PERM_CONTROL_CAR_CLIMATE) == true)
    }

    @Test
    fun `mapAaosGearOrNull and mapPleosGearOrNull ignore unknown gears instead of returning PARK`() {
        val provider = PleosSignalProvider(context = null)

        assertEquals(null, provider.mapAaosGearOrNull(0))
        assertEquals(null, provider.mapAaosGearOrNull(999))
        assertEquals(GearState.DRIVE, provider.mapAaosGearOrNull(8))
        assertEquals(GearState.REVERSE, provider.mapAaosGearOrNull(2))
        assertEquals(GearState.NEUTRAL, provider.mapAaosGearOrNull(1))
        assertEquals(GearState.PARK, provider.mapAaosGearOrNull(4))

        assertEquals(null, provider.mapPleosGearOrNull(null))
        assertEquals(null, provider.mapPleosGearOrNull(VehicleGear.GEAR_UNKNOWN))
        assertEquals(GearState.DRIVE, provider.mapPleosGearOrNull(VehicleGear.GEAR_DRIVE))
    }

    @Test
    fun `BridgeDiagnostics updates real-time telemetry fields and feed sequence`() {
        val diag = BridgeDiagnostics()
        diag.reportGear("VHAL(8)", "AAOS", GearState.DRIVE)
        diag.reportDoorPosition(DoorZone.TRUNK, 100, boundsLabel = "0..100")
        diag.reportCabin(openWindows = 1, windowDetail = "ROW_1_LEFT", temp = 24.5f, rearSeatDetail = "ROW_2_LEFT")
        diag.reportCruise("ACTIVATED -> 스마트 크루즈")
        diag.reportNaviSnapshot(routeState = "경로 안내 중", destName = "집", gpsDetail = "이마트 성수점")

        val snap = diag.status.value
        assertEquals("VHAL(8) -> ${GearState.DRIVE.label}", snap.gearRaw)
        assertEquals("AAOS", snap.gearSource)
        assertEquals("pos=100 (열림)", snap.trunkDoorRaw)
        assertEquals("0..100", snap.doorBoundsLabel)
        assertEquals("1개 열림 (ROW_1_LEFT)", snap.windowOpenSummary)
        assertEquals(24.5f, snap.cabinTempCelsius)
        assertEquals("ROW_2_LEFT", snap.rearSeatSummary)
        assertEquals("경로 안내 중", snap.naviRouteState)
        assertEquals("집", snap.naviDestinationRaw)
        assertTrue(snap.sensorUpdateSeq >= 5L)
        assertTrue(snap.sensorFeed.isNotEmpty())
    }
}
