package com.pleos.vehiclecontext.core.runtime

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EmulatorGpsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!canRunEmulatorDemo(context)) return
        val token = intent.getStringExtra("request_id") ?: return
        VehicleContextRuntime.ensureInitialized(context)
        val demo = VehicleContextRuntime.arrivalDemo
        val accepted = when (intent.action) {
            "com.pleos.vehiclecontext.GPS_DEMO_CLAIM" -> demo.claim(token)
            "com.pleos.vehiclecontext.GPS_DEMO_COMPLETE" -> demo.complete(token, intent.getBooleanExtra("success", false))
            else -> false
        }
        resultCode = if (accepted) Activity.RESULT_OK else Activity.RESULT_CANCELED
    }
}
