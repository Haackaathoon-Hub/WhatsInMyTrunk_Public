package com.pleos.vehiclecontext.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pleos.vehiclecontext.core.model.VehicleControlCommand
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime

/**
 * AAOS 시스템 헤드업 알림(Heads-Up Notification) 배너의 액션 버튼
 * ('확인할게요', '지금 트렁크 열기') 클릭을 처리하는 BroadcastReceiver
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        VehicleContextRuntime.ensureInitialized(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ACTION_ACKNOWLEDGE -> {
                VehicleContextRuntime.engine.acknowledgeActivePrompt()
                notificationManager.cancel(VehicleContextMonitorService.PROMPT_NOTIFICATION_ID)
            }
            ACTION_OPEN_TRUNK -> {
                VehicleContextRuntime.engine.executePromptControl(VehicleControlCommand.OPEN_TRUNK)
                notificationManager.cancel(VehicleContextMonitorService.PROMPT_NOTIFICATION_ID)
            }
            ACTION_CLOSE_TRUNK -> {
                VehicleContextRuntime.engine.executePromptControl(VehicleControlCommand.CLOSE_TRUNK)
                notificationManager.cancel(VehicleContextMonitorService.PROMPT_NOTIFICATION_ID)
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.pleos.vehiclecontext.ACTION_ACKNOWLEDGE"
        const val ACTION_OPEN_TRUNK = "com.pleos.vehiclecontext.ACTION_OPEN_TRUNK"
        const val ACTION_CLOSE_TRUNK = "com.pleos.vehiclecontext.ACTION_CLOSE_TRUNK"
    }
}

