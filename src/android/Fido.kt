@file:OptIn(ExperimentalUnsignedTypes::class)

package com.fkmit.fido

import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Fido {

    fun encodePublicKeyCredentials(assertions: List<Ctap2Session.AssertionData>, clientData: JsonString) = JSONArray().apply {
        assertions.forEach {
            val credentialId = it.credential!!["id"] as ByteArray
            val userId = it.user!!["id"] as ByteArray
            put(JSONObject().apply {
                put("id", credentialId.encodeBase64Url())
                put("type", "public-key")
                put("rawId", credentialId.toUByteArray().toJsonArray())
                put("response", JSONObject().apply {
                    put("clientDataJSON", clientData.encodeToByteArray().toUByteArray().toJsonArray())
                    put("authenticatorData", it.authenticatorData.toUByteArray().toJsonArray())
                    put("signature", it.signature.toUByteArray().toJsonArray())
                    put("userHandle", userId.toUByteArray().toJsonArray())
                })
            })
        }
    }.toString()

    fun encodeCredentialList(credentials: List<FidoCredential>) = JSONArray().apply {
        credentials.forEach {
            put(JSONObject().apply {
                put("userName", it.userName)
                put("userId", it.userId.encodeBase64())
            })
        }
    }.toString()

    fun getCredentials(session: Ctap2Session, clientPin: ClientPin, pinUvAuthToken: ByteArray): List<FidoCredential> {
        val management = CredentialManagement(session, clientPin.pinUvAuth, pinUvAuthToken)
        return management.enumerateRps().map { rpData ->
            management.enumerateCredentials(rpData.rpIdHash).map { credentialData ->
                FidoCredential (
                    rpId = rpData.rp["id"] as String,
                    //credentialId = credentialData.credentialId["id"] as ByteArray,
                    credentialType = credentialData.credentialId["type"] as String,
                    userId = credentialData.user["id"] as ByteArray,
                    userName = credentialData.user["name"] as String,
                    //displayName = credentialData.user["displayName"] as String?,
                    publicKeyCredentialDescriptor = credentialData.credentialId
                )
            }
        }.flatten()
    }

    fun getAssertions(session: Ctap2Session, clientPinProtocol: PinUvAuthProtocol, pinUvAuthToken: ByteArray, credential: FidoCredential?, rpId: UrlString, clientData: JsonString): List<Ctap2Session.AssertionData> {
        val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientData.toByteArray())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pinUvAuthToken, "HmacSHA256"))
        val pinUvAuthParam = mac.doFinal(clientDataHash).copyOf(16)
        return session.getAssertions(
            rpId,
            clientDataHash,
            credential?.let { listOf(it.publicKeyCredentialDescriptor) },
            null,
            null,
            pinUvAuthParam,
            clientPinProtocol.version,
            null
        )
    }

    fun getAssertions(session: Ctap2Session, rpId: UrlString, clientData: JsonString): List<Ctap2Session.AssertionData> {
        val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientData.toByteArray())
        return session.getAssertions(
            rpId,
            clientDataHash,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }
}