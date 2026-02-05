package com.fkmit.fido

/**
 * Base flag for success message codes.
 * Combined with specific subcodes to form a full message code.
 */
private const val FLAG_SUCCESS = 0x1000

/**
 * Base flag for error message codes.
 * Combined with specific subcodes to form a full message code.
 */
private const val FLAG_ERROR = 0x2000

/**
 * Base flag for signal (non-terminal) message codes.
 * Combined with specific subcodes to form a full message code.
 */
private const val FLAG_SIGNAL = 0x3000

/**
 * Encapsulates application message codes used to indicate outcomes and signals.
 *
 * @property code the integer code combining a base flag and an optional subcode
 * @property isTerminal true when the message represents a terminal outcome (no further processing expected)
 */
enum class MessageCodes(val code: Int, val isTerminal: Boolean) {

    /**
     * General success code.
     * Uses [FLAG_SUCCESS]. This is a terminal code.
     */
    Success(FLAG_SUCCESS, true),

    /**
     * General failure code.
     * Uses [FLAG_ERROR]. This is a terminal code.
     */
    Failure(FLAG_ERROR, true),

    /**
     * Failure due to an invalid PIN provided by the user.
     * Composed from [FLAG_ERROR] with subcode 0x0001. Terminal.
     */
    FailureInvalidPin(FLAG_ERROR or 0x0001, true),

    /**
     * Failure because the device is not supported.
     * Composed from [FLAG_ERROR] with subcode 0x0002. Terminal.
     */
    FailureUnsupportedDevice(FLAG_ERROR or 0x0002, true),

    /**
     * Failure indicating the device was lost or disconnected during the operation.
     * Composed from [FLAG_ERROR] with subcode 0x0003. Terminal.
     */
    FailureDeviceLost(FLAG_ERROR or 0x0003, true),

    /**
     * Failure indicating no credentials were found when expected.
     * Composed from [FLAG_ERROR] with subcode 0x0004. Terminal.
     */
    FailureNoCredentials(FLAG_ERROR or 0x0004, true),

    /**
     * Failure indicating too many credentials were present to complete the operation.
     * Composed from [FLAG_ERROR] with subcode 0x0005. Terminal.
     */
    FailureTooManyCredentials(FLAG_ERROR or 0x0005, true),

    /**
     * Progress update signal.
     * Uses [FLAG_SIGNAL] with subcode 0x0001. Non-terminal (informational).
     */
    SignalProgressUpdate(FLAG_SIGNAL or 0x0001, false),

    /**
     * Signal emitted when a device is discovered.
     * Uses [FLAG_SIGNAL] with subcode 0x0002. Non-terminal (informational).
     */
    SignalDeviceDiscovered(FLAG_SIGNAL or 0x0002, false)

}