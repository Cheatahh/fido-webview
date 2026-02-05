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

/**
 * Utility object providing helpers for interacting with CTAP2 sessions and
 * encoding FIDO/WebAuthn data for the JavaScript/Cordova layer.
 *
 * Functions include:
 *  - Encoding assertions into a WebAuthn-like JSON string
 *  - Encoding lists of discovered credentials
 *  - Enumerating credentials available on a device (via CredentialManagement)
 *  - Requesting assertions from a `Ctap2Session`, with and without PIN/UvAuth
 */
object Fido {

    /**
     * Encode a list of `AssertionData` into a WebAuthn-style JSON array string.
     *
     * Each assertion is converted into an object containing:
     *  - `id`: Base64 URL-safe encoded credential id
     *  - `type`: credential type (always `"public-key"` here)
     *  - `rawId`: `id` as an array of integer bytes
     *  - `response`: object containing `clientDataJSON`, `authenticatorData`, `signature`, `userHandle`
     *
     * Byte arrays are converted to unsigned JSON arrays (via `toUByteArray().toJsonArray()`)
     * and `clientData` is encoded from the provided string.
     *
     * @param assertions the list of `Ctap2Session.AssertionData` to encode
     * @param clientData the JSON clientData string (will be included as `clientDataJSON`)
     * @return a JSON string representing an array of public key credential objects
     */
    fun encodePublicKeyCredentials(assertions: List<Ctap2Session.AssertionData>, clientData: JsonString) = JSONArray().apply {
        log { "-- encodePublicKeyCredentials --" }
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
        log { "-> ${toString()}" }
    }.toString()

    /**
     * Encode a list of `FidoCredential` into a JSON array string suitable for
     * presenting credential choices to the caller.
     *
     * Each entry contains `userName` and a Base64-encoded `userId`.
     *
     * @param credentials the list of credentials to encode
     * @return a JSON string representing the credential list
     */
    fun encodeCredentialList(credentials: List<FidoCredential>) = JSONArray().apply {
        log { "-- encodeCredentialList --" }
        credentials.forEach {
            put(JSONObject().apply {
                put("userName", it.userName)
                put("userId", it.userId.encodeBase64())
            })
        }
        log { "-> ${toString()}" }
    }.toString()

    /**
     * Enumerate credentials on the device using `CredentialManagement`.
     *
     * This method constructs a `CredentialManagement` instance with the provided
     * `session`, `clientPin` and `pinUvAuthToken`, then enumerates Relying Parties
     * and their associated credentials, mapping them to `FidoCredential` instances.
     *
     * @param session the active `Ctap2Session` to use
     * @param clientPin the `ClientPin` helper for PIN/UvAuth operations
     * @param pinUvAuthToken the token used to authenticate Credential Management calls
     * @return a list of discovered `FidoCredential` objects
     */
    fun getCredentials(session: Ctap2Session, clientPin: ClientPin, pinUvAuthToken: ByteArray): List<FidoCredential> {
        log { "-- getCredentials --" }
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
        }.flatten().onEach { log { "-> $it" } }
    }

    /**
     * Request assertions from the device using PIN/UvAuth parameters.
     *
     * This variant computes `clientDataHash` (SHA-256 of `clientData`) and derives
     * a 16-byte `pinUvAuthParam` by HMAC-SHA256 of the hash with `pinUvAuthToken`.
     * The resulting parameters are passed to `session.getAssertions(...)`.
     *
     * @param session the active `Ctap2Session`
     * @param clientPinProtocol the `PinUvAuthProtocol` instance (to provide version)
     * @param pinUvAuthToken the PIN/UvAuth token used to derive `pinUvAuthParam`
     * @param credential optional credential descriptor to limit the assertion request
     * @param rpId the relying party id for the assertion request
     * @param clientData the clientData JSON string used to compute the clientDataHash
     * @return a list of `Ctap2Session.AssertionData` from the device
     */
    fun getAssertions(session: Ctap2Session, clientPinProtocol: PinUvAuthProtocol, pinUvAuthToken: ByteArray, credential: FidoCredential?, rpId: UrlString, clientData: JsonString): List<Ctap2Session.AssertionData> {
        log { "-- getAssertions (uv) --" }
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
        ).also { log { "-> ${it.size} elements" } }
    }

    /**
     * Request assertions from the device without PIN/UvAuth.
     *
     * Computes `clientDataHash` (SHA-256 of `clientData`) and calls
     * `session.getAssertions(...)` with null PIN parameters.
     *
     * @param session the active `Ctap2Session`
     * @param rpId the relying party id for the assertion request
     * @param clientData the clientData JSON string used to compute the clientDataHash
     * @return a list of `Ctap2Session.AssertionData` from the device
     */
    fun getAssertions(session: Ctap2Session, rpId: UrlString, clientData: JsonString): List<Ctap2Session.AssertionData> {
        log { "-- getAssertions (no uv) --" }
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
        ).also { log { "-> ${it.size} elements" } }
    }
}