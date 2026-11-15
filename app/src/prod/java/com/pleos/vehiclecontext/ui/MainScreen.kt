package com.pleos.vehiclecontext.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pleos.vehiclecontext.ui.theme.PleosTheme
import com.pleos.vehiclecontext.ui.user.GeneralUserScreen

/**
 * 실제 배포용 (prod) AAOS 메인 화면:
 * - 상단 운전자/개발자 모드 전환 바(AppNavigationBar)와 개발자 콘솔(DeveloperConsoleScreen)을 포함하지 않습니다.
 * - 운전자 모드(GeneralUserScreen)만 전체 화면으로 제공합니다.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    @Suppress("UNUSED_PARAMETER") onSimulateRealAaosBackgroundPopup: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PleosTheme.colors.background
    ) {
        GeneralUserScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
