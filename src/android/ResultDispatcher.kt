package com.fkmit.fido

import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONObject

@JvmInline
value class ResultDispatcher(private val callback: CallbackContext) {

    fun sendMessage(code: MessageCodes, payload: Any?) {
        val result = PluginResult(code.resultStatus, JSONObject().apply {
            put("statusCode", code.code)
            put("payload", payload)
        })
        result.keepCallback = !code.isTerminal
        callback.sendPluginResult(result)
    }

}