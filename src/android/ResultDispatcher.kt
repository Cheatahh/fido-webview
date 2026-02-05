package com.fkmit.fido

import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONObject

/**
 * Dispatches results to a Cordova `CallbackContext` as JSON messages.
 *
 * This inline value class wraps a `CallbackContext` and provides a single
 * helper to send a structured message containing a numeric status code and
 * an optional payload.
 *
 * @param callback the Cordova `CallbackContext` used to deliver plugin results
 */
@JvmInline
value class ResultDispatcher(private val callback: CallbackContext) {

    /**
     * Send a message to the wrapped `CallbackContext`.
     *
     * The message is a `PluginResult` with status `OK` whose payload is a JSON
     * object containing:
     *  - `statusCode`: the integer code from [MessageCodes]
     *  - `payload`: the optional payload provided by the caller
     *
     * The `keepCallback` flag is set to `true` when the provided [code] is
     * non-terminal so the callback can receive further
     * messages; for terminal codes the callback will be released.
     *
     * @param code the message code indicating success, error, or signal
     * @param payload an optional value to include in the message; may be `null`
     */
    fun sendMessage(code: MessageCodes, payload: Any?) {
        log { "-- sendMessage (code = $code, payload = $payload) --" }
        val result = PluginResult(PluginResult.Status.OK, JSONObject().apply {
            put("statusCode", code.code)
            put("payload", payload)
        })
        result.keepCallback = !code.isTerminal
        callback.sendPluginResult(result)
    }

}