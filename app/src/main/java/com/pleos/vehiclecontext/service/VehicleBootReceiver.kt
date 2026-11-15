package com.pleos.vehiclecontext.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 실제 차량 시동(AAOS 부팅 완료) 시 사용자의 앱 실행 없이도
 * 백그라운드에서 트렁크/도어 및 주행 맥락 감시를 자동으로 시작하는 Receiver
 */
class VehicleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, VehicleContextMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

