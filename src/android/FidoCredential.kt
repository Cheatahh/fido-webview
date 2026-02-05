package com.fkmit.fido

/**
 * Data holder for a discovered FIDO credential returned by the device.
 *
 * Represents the minimal credential information used by the plugin:
 * - the relying party id (`rpId`)
 * - the credential type (`credentialType`)
 * - the raw user id (`userId`)
 * - a human-readable user name (`userName`)
 * - the raw public key credential descriptor returned by the device
 *
 * Note: Some fields such as `credentialId` and `displayName` are present
 * in device responses but are intentionally commented out here because they
 * are not used directly by the current codebase (robustness).
 *
 * @property rpId the relying party identifier (e.g. "example.com")
 * @property credentialType the credential type (commonly `"public-key"`)
 * @property userId the raw user identifier bytes associated with the credential
 * @property userName the username or display name associated with the credential
 * @property publicKeyCredentialDescriptor the raw descriptor map returned by the device
 *         (contains keys such as `"id"` and `"type"`)
 */
@Suppress("ArrayInDataClass")
data class FidoCredential(
    val rpId: String,
    //val credentialId: ByteArray, // excluded for robustness, as it is unused
    val credentialType: String,
    val userId: ByteArray,
    val userName: String,
    //val displayName: String?, // excluded for robustness, as it is unused
    val publicKeyCredentialDescriptor: Map<String, Any?>
)