package com.pleos.vehiclecontext.gleo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime

/** Debug-only Gleo handoff commands for automated/AVD testing. */
class GleoDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runtime = VehicleContextRuntime
        runtime.ensureInitialized(context)
        val engine = runtime.engine
        val accepted = when (intent.action) {
            "com.pleos.vehiclecontext.GLEO_DEMO_ARM" -> {
                gleoDemoHandoff.arm()
                true
            }
            "com.pleos.vehiclecontext.GLEO_DEMO_CLAIM" -> {
                val eligible = gleoDemoHandoff.eligibleRequest(
                    session = engine.session.value,
                    vehicleMode = true,
                    hasDraft = runtime.gleoActions.pending.value != null
                )
                intent.getStringExtra("request_id")?.let { gleoDemoHandoff.claim(it, eligible) } == true
            }
            "com.pleos.vehiclecontext.GLEO_DEMO_DISARM" -> {
                gleoDemoHandoff.disarm(engine)
                true
            }
            else -> false
        }
        if (isOrderedBroadcast) {
            resultCode = if (accepted) Activity.RESULT_OK else Activity.RESULT_CANCELED
        }
    }
}
