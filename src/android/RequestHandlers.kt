@file:OptIn(ExperimentalUnsignedTypes::class)

package com.fkmit.fido

import android.util.Log
import com.yubico.yubikit.core.application.InvalidPinException
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import org.json.JSONArray

object RequestHandlers {

    fun getAssertion(args: JSONArray, nfc: NFCDiscoveryDispatcher, dispatch: ResultDispatcher) {

        require(args.length() == 4) {
            "Invalid parameters, expected <CLIENT_DATA> <RP_ID> <USER_PIN?> <USER_ID?>."
        }
        val clientData = requireNotNull(args.optString(0).takeIf(String::isNotBlank)) {
            "Parameter <CLIENT_DATA> must be a string."
        }
        val rpId = requireNotNull(args.optString(1).takeIf(String::isNotBlank)) {
            "Parameter <RP_ID> must be a string."
        }
        Log.e("FIDO", "$rpId;$clientData")
        val userPin = args.optString(2).takeIf(String::isNotBlank)?.toCharArray()
        val userId = args.optString(3).takeIf(String::isNotBlank)?.decodeBase64()

        nfc.useDeviceConnection(dispatch) ctx@ { connection ->
            Log.e("FIDO", "init")
            dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 0f)
            Log.e("FIDO", "session starting")
            val session = Ctap2Session(connection)
            Log.e("FIDO", "session created")
            when(userPin) {
                null -> {
                    Log.e("FIDO", "mode: no pin")
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 1f / 2f)
                    Log.e("FIDO", "getting assertion")
                    val assertion = Fido.getAssertions(session, rpId, clientData)
                    Log.e("FIDO", "assertions got: ${assertion.size}")
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 2f / 2f)
                    val result = Fido.encodePublicKeyCredentials(assertion, clientData)
                    Log.e("FIDO", "result got: $result")
                    dispatch.sendMessage(MessageCodes.Success, result)
                }
                else -> {
                    Log.e("FIDO", "mode: with pin")
                    val pinProtocol = PinUvAuthProtocolV1()
                    Log.e("FIDO", "getting client pin")
                    val clientPin = ClientPin(session, pinProtocol)
                    Log.e("FIDO", "getting uv token")
                    var pinUvAuthToken = clientPin.getPinToken(userPin, ClientPin.PIN_PERMISSION_CM, null)
                    Log.e("FIDO", "token: ${pinUvAuthToken.contentToString()}")
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 1f / 3f)
                    Log.e("FIDO", "getting credentials")
                    val credentials = runCatching {
                        Fido.getCredentials(session, clientPin, pinUvAuthToken).filter {
                            it.rpId == rpId && it.credentialType == "public-key" && (userId === null || it.userId contentEquals userId)
                        }
                    }.onSuccess { credentials ->
                        Log.e("FIDO", "credentials got: ${credentials.size}")
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
                        Log.e("FIDO", "credential manager not supported")
                    }
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 2f / 3f)
                    Log.e("FIDO", "getting uv token")
                    pinUvAuthToken = clientPin.getPinToken(userPin, ClientPin.PIN_PERMISSION_GA, rpId)
                    Log.e("FIDO", "token: ${pinUvAuthToken.contentToString()}")
                    Log.e("FIDO", "getting assertion")
                    val assertion = Fido.getAssertions(session, pinProtocol, pinUvAuthToken, credentials.getOrNull()?.firstOrNull(), rpId, clientData)
                    Log.e("FIDO", "assertions got: ${assertion.size}")
                    dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 3f / 3f)
                    val result = Fido.encodePublicKeyCredentials(assertion, clientData)
                    Log.e("FIDO", "result got: $result")
                    dispatch.sendMessage(MessageCodes.Success, result)
                }
            }
        }
    }
}