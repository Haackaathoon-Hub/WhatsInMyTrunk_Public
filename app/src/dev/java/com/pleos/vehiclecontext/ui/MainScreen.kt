package com.pleos.vehiclecontext.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pleos.vehiclecontext.ui.dev.DeveloperConsoleScreen
import com.pleos.vehiclecontext.ui.model.NavigationTab
import com.pleos.vehiclecontext.ui.navigation.AppNavigationBar
import com.pleos.vehiclecontext.ui.theme.PleosTheme
import com.pleos.vehiclecontext.ui.user.GeneralUserScreen

/**
 * AAOS 메인 화면 듀얼 모드 호스트:
 * - 상단: 운전자 모드 / 개발자 콘솔 전환 탑 내비게이션 바 (AppNavigationBar)
 * - 컨텐츠: 운전자 모드(GeneralUserScreen)와 개발자 콘솔(DeveloperConsoleScreen)을 전환
 * - 공유 런타임: MainViewModel 및 VehicleContextEngine 싱글톤 상태를 두 뷰 간에 중단 없이 동기화
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSimulateRealAaosBackgroundPopup: () -> Unit = {},
    initialTab: NavigationTab = NavigationTab.GENERAL_USER
) {
    val session by viewModel.session.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PleosTheme.colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // R1: Top Navigation Bar with mode tabs and real-time vehicle status pills
            AppNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                currentGear = session.currentGear,
                autonomousMode = session.autonomousMode,
                autonomousProfile = session.autonomousProfile,
                isVhalConnected = viewModel.capabilities.isNotEmpty()
            )

            // Dynamic view switching preserving singleton runtime state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 200),
                    label = "NavigationModeTransition"
                ) { tab ->
                    when (tab) {
                        NavigationTab.GENERAL_USER -> {
                            GeneralUserScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        NavigationTab.DEVELOPER_CONSOLE -> {
                            DeveloperConsoleScreen(
                                viewModel = viewModel,
                                onSimulateRealAaosBackgroundPopup = onSimulateRealAaosBackgroundPopup,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
