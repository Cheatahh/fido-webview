@file:OptIn(ExperimentalUnsignedTypes::class)

package com.fkmit.fido

import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import org.json.JSONArray

/**
 * Central place for handling incoming FIDO-related requests from the Cordova layer.
 *
 * Each handler in this object:
 *  - Validates and parses incoming arguments
 *  - Uses an [NFCDiscoveryDispatcher] to obtain a `SmartCardConnection`
 *  - Drives the CTAP2 `Ctap2Session` to perform requested operations
 *  - Emits progress, signal and terminal messages via [ResultDispatcher]
 *
 * Handlers are written to map device and protocol errors to the plugin's
 * `MessageCodes` so the JavaScript side can react appropriately.
 */
object RequestHandlers {

    /**
     * Handle an assertion (getAssertion) request.
     *
     * Expected `args` layout (JSON array):
     *  0: CLIENT_DATA (non-empty string)
     *  1: RP_ID (non-empty string)
     *  2: USER_PIN? (optional string; empty = no PIN)
     *  3: USER_ID? (optional Base64 string; empty = no filter)
     *
     * Behavior:
     *  - Validates parameters and throws an IllegalArgumentException on invalid input.
     *  - Uses [NFCDiscoveryDispatcher.useDeviceConnection] to open a `SmartCardConnection` and creates a `Ctap2Session`.
     *  - Emits progress updates using [ResultDispatcher.sendMessage] with `SignalProgressUpdate`.
     *  - If no PIN is provided, requests assertions directly and returns an encoded JSON result.
     *  - If a PIN is provided:
     *      - Uses `ClientPin` to obtain PIN/UvAuth tokens for Credential Management and Assertions.
     *      - Enumerates credentials (if supported) and filters by `rpId`, `credentialType` and optional `userId`.
     *      - Returns appropriate failure codes when no credentials or multiple credentials are found.
     *      - Requests an assertion authenticated with PIN/UvAuth and returns an encoded JSON result.
     *
     * Errors and exceptions:
     *  - Common device/protocol errors are expected to be mapped by the calling `NFCDiscoveryDispatcher`
     *    (via its `runWithCatching` helper). This function logs errors and emits failure messages when
     *    CredentialManagement is not supported.
     *
     * @param args the raw JSON array of arguments from the Cordova call
     * @param nfc an implementation of [NFCDiscoveryDispatcher] used to obtain a device connection
     * @param dispatch the [ResultDispatcher] used to emit progress, signals and final results/errors
     */
    fun getAssertion(args: JSONArray, nfc: NFCDiscoveryDispatcher, dispatch: ResultDispatcher) {

        log { "-- getAssertion --" }

        require(args.length() == 4) {
            "Invalid parameters, expected <CLIENT_DATA> <RP_ID> <USER_PIN?> <USER_ID?>."
        }
        val clientData = requireNotNull(args.optString(0).takeIf(String::isNotBlank)) {
            "Parameter <CLIENT_DATA> must be a string."
        }
        val rpId = requireNotNull(args.optString(1).takeIf(String::isNotBlank)) {
            "Parameter <RP_ID> must be a string."
        }
        val userPin = args.optString(2).takeIf(String::isNotBlank)?.toCharArray()
        val userId = args.optString(3).takeIf(String::isNotBlank)?.decodeBase64()

        log { "-> clientData = $clientData" }
        log { "-> rpId = $rpId" }
        log { "-> userPin = ${userPin?.joinToString()}" }
        log { "-> userId = ${userId?.contentToString()}" }

        nfc.useDeviceConnection(dispatch) ctx@ { connection ->
            log { "connection acquired" }
            dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 0f)
            val session = Ctap2Session(connection)
            log { "session established" }
            when(userPin) {
                null -> {
                    log { "mode: no uv" }
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 1f / 2f)
                    val assertion = Fido.getAssertions(session, rpId, clientData)
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 2f / 2f)
                    val result = Fido.encodePublicKeyCredentials(assertion, clientData)
                    dispatch.sendMessage(MessageCodes.Success, result)
                }
                else -> {
                    log { "mode: uv" }
                    val pinProtocol = PinUvAuthProtocolV1()
                    val clientPin = ClientPin(session, pinProtocol)
                    log { "client pin established" }
                    val credentials = runCatching {
                        log { "requesting PIN_PERMISSION_CM token" }
                        val pinUvAuthToken = clientPin.getPinToken(userPin, ClientPin.PIN_PERMISSION_CM, null)
                        log { "ClientPin.PIN_PERMISSION_CM -> ${pinUvAuthToken.contentToString()}" }
                        dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 1f / 3f)
                        Fido.getCredentials(session, clientPin, pinUvAuthToken).filter {
                            it.rpId == rpId && it.credentialType == "public-key" && (userId === null || it.userId contentEquals userId)
                        }
                    }.onSuccess { credentials ->
                        when {
                            credentials.isEmpty() -> {
                                dispatch.sendMessage(MessageCodes.FailureNoCredentials, null)
                                return@ctx
                            }
                            credentials.size > 1 -> {
                                dispatch.sendMessage(MessageCodes.FailureTooManyCredentials, Fido.encodeCredentialList(credentials))
                                return@ctx
                            }
                        }
                    }.onFailure {
                        dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 1f / 3f)
                        log { "credential manager not supported" }
                    }
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 2f / 3f)
                    log { "requesting PIN_PERMISSION_GA token" }
                    val pinUvAuthToken = clientPin.getPinToken(userPin, ClientPin.PIN_PERMISSION_GA, rpId)
                    log { "ClientPin.PIN_PERMISSION_GA -> ${pinUvAuthToken.contentToString()}" }
                    val assertion = Fido.getAssertions(session, pinProtocol, pinUvAuthToken, credentials.getOrNull()?.firstOrNull(), rpId, clientData)
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 3f / 3f)
                    val result = Fido.encodePublicKeyCredentials(assertion, clientData)
                    dispatch.sendMessage(MessageCodes.Success, result)
                }
            }
        }
    }
}