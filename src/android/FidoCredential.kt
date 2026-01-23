package com.fkmit.fido

class FidoCredential(
    val rpId: String,
    val credentialId: ByteArray,
    val credentialType: String,
    val userId: ByteArray,
    val userName: String,
    val displayName: String?,
    val publicKeyCredentialDescriptor: Map<String, Any?>
)