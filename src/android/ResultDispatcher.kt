package com.fkmit.fido

import android.util.Log
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONObject

@JvmInline
value class ResultDispatcher(private val callback: CallbackContext) {

    fun sendMessage(code: MessageCodes, payload: Any?) {
        Log.d("FIDO", "Sending Message: $code, $payload")
        val result = PluginResult(code.resultStatus, JSONObject().apply {
            put("statusCode", code.code)
            put("payload", payload)
        })
        result.keepCallback = code.isTerminal
        callback.sendPluginResult(result)
    }

}