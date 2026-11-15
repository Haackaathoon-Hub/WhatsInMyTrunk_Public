package com.pleos.vehiclecontext.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pleos.vehiclecontext.core.runtime.VehicleContextRuntime
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Pleos Gleo AppAction 브로드캐스트 수신기.
 */
class GleoActionReceiver internal constructor(
    private val actionsFor: (Context) -> GleoCargoActions
) : BroadcastReceiver() {
    constructor() : this({ context ->
        VehicleContextRuntime.ensureInitialized(context)
        VehicleContextRuntime.gleoActions
    })

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in GleoCargoActions.actions) return
        val response = runCatching {
            val parameters = readParameters(intent)
            actionsFor(context).handle(action, parameters)
        }.getOrElse { error ->
            android.util.Log.e("PleosGleo", "ACTION_FAILED $action", error)
            JSONObject()
                .put("functionName", action)
                .put("status", "fail")
                .put("message", "The cargo action could not be processed. Please check the parameters.")
        }
        android.util.Log.i("PleosGleo", "ACTION_RESULT $action status=${response.optString("status")}")
        context.sendBroadcast(
            Intent(RESPONSE_ACTION)
                .putExtra("packageName", context.packageName)
                .putExtra("result", response.optString("status"))
                .putExtra("response", response.toString())
        )
    }

    companion object {
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

        fun readParameters(intent: Intent): JSONObject {
            val raw = intent.getStringExtra("ACTION_PARAM") ?: intent.getStringExtra("actionParameters")
            var value: Any? = if (raw == null) {
                JSONObject()
            } else {
                require(raw.length <= 8192)
                JSONTokener(raw).nextValue()
            }
            if (value is String) value = JSONTokener(value).nextValue()
            val root = value as? JSONObject ?: throw IllegalArgumentException("Expected an object")
            val parameters = root.optJSONObject("actionParameters") ?: root
            listOf(
                "cargo_name",
                "quantity",
                "purpose",
                "target_place",
                "keep_in_car",
                "cargo_id",
                "query",
                "operation",
                "existing_target_place"
            ).forEach { key ->
                if (!parameters.has(key) && intent.hasExtra(key)) {
                    parameters.put(key, intent.extras?.get(key))
                }
            }
            return parameters
        }
    }
}
