package com.pleos.vehiclecontext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime
import com.pleos.vehiclecontext.core.settings.ThemeMode
import com.pleos.vehiclecontext.core.signal.PleosSignalProvider
import com.pleos.vehiclecontext.service.VehicleContextMonitorService
import com.pleos.vehiclecontext.ui.MainScreen
import com.pleos.vehiclecontext.ui.MainViewModel
import com.pleos.vehiclecontext.ui.theme.PleosTheme
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(appContext = applicationContext)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        VehicleContextRuntime.pleosSignalProvider.refreshVehicleBindings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VehicleContextRuntime.ensureInitialized(applicationContext)
        requestRuntimePermissionsIfNeeded()
        startVehicleMonitorService()

        setContent {
            val appSettings by viewModel.appSettings.collectAsState()
            var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
            LaunchedEffect(appSettings.themeMode) {
                while (appSettings.themeMode == ThemeMode.AUTO) {
                    currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    delay(60_000L)
                }
            }
            val darkTheme = appSettings.themeMode.isDark(currentHour)
            PleosTheme(darkTheme = darkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    onSimulateRealAaosBackgroundPopup = {
                        triggerBackgroundPopupAndGoToHome()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        VehicleContextRuntime.setActivityVisible(true)
        VehicleContextRuntime.pleosSignalProvider.refreshNow()
    }

    override fun onPause() {
        VehicleContextRuntime.setActivityVisible(false)
        super.onPause()
    }

    private fun startVehicleMonitorService() {
        runCatching {
            val serviceIntent = Intent(this, VehicleContextMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }.onFailure { e ->
            android.util.Log.w("MainActivity", "Failed to start VehicleContextMonitorService: ${e.message}", e)
        }
    }

    /**
     * 실제 차량 사용 환경 재현:
     * 현재 앱 화면을 닫고(AAOS 홈/내비게이션 화면으로 이동),
     * 3.5초 뒤 백그라운드 서비스가 '목적지 도착 + P단 주차' 이벤트를 발생시켜
     * 내비게이션/홈 화면 위에 AAOS 헤드업 알림(HUN)과 반투명 하차 모달(PreExitPromptActivity)을 직접 띄웁니다.
     */
    private fun triggerBackgroundPopupAndGoToHome() {
        val intent = Intent(this, VehicleContextMonitorService::class.java).apply {
            action = VehicleContextMonitorService.ACTION_SIMULATE_BACKGROUND_ARRIVAL_AFTER_DELAY
            putExtra(VehicleContextMonitorService.EXTRA_DELAY_MS, 3500L)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // 운전자가 내비게이션이나 AAOS 홈 화면을 보고 있는 상태가 되도록 앱을 백그라운드로 전환
        moveTaskToBack(true)
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(PleosSignalProvider.AAOS_PERM_CAR_POWERTRAIN)
            addAll(PleosSignalProvider.REQUIRED_VEHICLE_PERMISSIONS)
        }

        val missing = candidates.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            runCatching {
                permissionLauncher.launch(missing.toTypedArray())
            }.onFailure { err ->
                android.util.Log.w("MainActivity", "Runtime permission request failed: ${err.message}")
            }
        }
    }
}
