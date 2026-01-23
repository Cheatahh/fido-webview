@file:OptIn(ExperimentalUnsignedTypes::class)

package com.fkmit.fido

import android.util.Log
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import org.json.JSONArray

object RequestHandlers {
    fun getAssertion(args: JSONArray, nfc: NFCDiscoveryDispatcher, dispatch: ResultDispatcher) {

        Log.d("FIDO", "getAssertion")

        require(args.length() == 4) {
            "Invalid parameters, expected <CLIENT_DATA> <USER_PIN> <RP_ID> <USER_ID?>."
        }
        val clientData = requireNotNull(args.optString(0).takeIf(String::isNotBlank)) {
            "Parameter <CLIENT_DATA> must be a string."
        }
        val userPin = requireNotNull(args.optString(1).takeIf(String::isNotBlank)) {
            "Parameter <USER_PIN> must be a string."
        }.toCharArray()
        val rpId = requireNotNull(args.optString(2).takeIf(String::isNotBlank)) {
            "Parameter <RP_ID> must be a string."
        }
        val userId = args.optString(3).takeIf(String::isNotBlank)?.decodeBase64() ?: byteArrayOf()

        Log.d("FIDO", "getAssertion - params ok")

        nfc.useDeviceConnection(dispatch) ctx@ { connection ->

            Log.d("FIDO", "getAssertion - got connection")

            dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 0f / 3f)
            val session = Ctap2Session(connection)
            val pinProtocol = PinUvAuthProtocolV1()
            val clientPin = ClientPin(session, pinProtocol)
            var pinUvAuthToken = clientPin.getPinToken(userPin, ClientPin.PIN_PERMISSION_CM, null)

            dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 1f / 3f)
            val credentials = Fido.getCredentials(session, clientPin, pinUvAuthToken).filter {
                it.rpId == rpId && it.credentialType == "public-key" && (userId.isEmpty() || it.userId contentEquals userId)
            }
            require(credentials.isNotEmpty()) {
                "No matching credential found."
            }
            if(credentials.size > 1) {
                dispatch.sendMessage(MessageCodes.SuccessUserChoiceRequired, Fido.encodeCredentialList(credentials))
                return@ctx
            }

            dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 2f / 3f)
            pinUvAuthToken = clientPin.getPinToken(userPin, ClientPin.PIN_PERMISSION_GA, rpId)
            val assertion = Fido.getAssertion(session, pinProtocol, pinUvAuthToken, credentials.first(), rpId, clientData)

            dispatch.sendMessage(MessageCodes.SignalProgressUpdate, 3f / 3f)
            val result = Fido.encodePublicKeyCredential(assertion, credentials.first(), clientData)

            dispatch.sendMessage(MessageCodes.Success, result)
        }
    }
}