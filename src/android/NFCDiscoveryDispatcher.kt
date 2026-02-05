package com.fkmit.fido

import android.nfc.TagLostException
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.application.InvalidPinException
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.core.smartcard.SmartCardConnection

/**
 * Contract for discovering YubiKey devices (NFC / USB) and providing a safe
 * way to obtain a `SmartCardConnection` for FIDO operations.
 *
 * Implementations are responsible for starting and stopping device discovery,
 * tracking the currently available `YubiKeyDevice`, and converting discovery
 * events into usable `SmartCardConnection` instances for callers.
 */
interface NFCDiscoveryDispatcher {

    /**
     * The most recently discovered NFC-capable `YubiKeyDevice`, or `null`
     * when no device is currently available.
     *
     * Implementations should update this value when discovery finds or loses a device.
     */
    var currentNFCDevice: YubiKeyDevice?

    /**
     * Begin discovering devices and invoke [callback] for each discovered device.
     *
     * The callback should be invoked with the discovered `YubiKeyDevice`. Implementations
     * may choose to discover via NFC and/or USB transports.
     *
     * @param callback invoked when a device is discovered
     */
    fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit)

    /**
     * Stop any ongoing discovery operations and cease invoking discovery callbacks.
     */
    fun stopDeviceDiscovery()

    /**
     * Helper that runs [block] and maps common exceptions to `MessageCodes` which are
     * dispatched via [dispatch]. This centralizes error handling for tag loss, invalid
     * PINs, CTAP errors and other exceptions so callers can report consistent status
     * messages back to the JavaScript/Cordova layer.
     *
     * This function is private to the interface and used internally by other helper methods.
     *
     * @param dispatch the `ResultDispatcher` used to send error/status messages
     * @param block the operation to execute under error handling
     */
    private fun runWithCatching(dispatch: ResultDispatcher, block: () -> Unit) {
        log { "-- runWithCatching --" }
        try {
            block()
        } catch (_: TagLostException) {
            log { "-> TagLostException" }
            currentNFCDevice = null
            dispatch.sendMessage(MessageCodes.FailureDeviceLost, null)
        } catch (_: InvalidPinException) {
            log { "-> InvalidPinException" }
            dispatch.sendMessage(MessageCodes.FailureInvalidPin, null)
        } catch (e: CtapException) {
            log { "-> CtapException(${e.ctapError})" }
            dispatch.sendMessage(when(e.ctapError) {
                CtapException.ERR_NO_CREDENTIALS -> MessageCodes.FailureNoCredentials
                CtapException.ERR_PIN_INVALID -> MessageCodes.FailureInvalidPin
                else -> MessageCodes.FailureUnsupportedDevice
            }, null)
        } catch (e: Exception) {
            log { "-> Exception(${e.message})" }
            dispatch.sendMessage(MessageCodes.Failure, e.message)
        }
    }

    /**
     * Obtain a `SmartCardConnection` and invoke [callback] with it.
     *
     * If `currentNFCDevice` is non-null, this method will open a `SmartCardConnection`
     * from that device and execute [callback] inside a `use` scope, with exceptions
     * mapped via [runWithCatching].
     *
     * If no device is currently available, discovery will be started and a single
     * device will be accepted (via an `InvokeOnce` wrapper). Once a device is found
     * it will be stored in [currentNFCDevice], a device-discovered signal will be sent,
     * and the connection will be opened and passed to [callback].
     *
     * @param dispatch the `ResultDispatcher` used to send status and error messages
     * @param callback invoked with an open `SmartCardConnection`; called within a `use` block
     */
    fun useDeviceConnection(dispatch: ResultDispatcher, callback: (SmartCardConnection) -> Unit) {
        log { "-- useDeviceConnection --" }
        runCatching {
            requireNotNull(currentNFCDevice).let {
                log { "-> currentNFCDevice present" }
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                it.openConnection(SmartCardConnection::class.java)
            }
        }.onSuccess { connection ->
            log { "-> currentNFCDevice used" }
            runWithCatching(dispatch) {
                connection.use(callback)
            }
        }.onFailure {
            log { "-> currentNFCDevice out of date, reconnecting" }
            startDeviceDiscovery(InvokeOnce { device ->
                currentNFCDevice = device
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                runWithCatching(dispatch) {
                    device.openConnection(SmartCardConnection::class.java).use(callback)
                }
            })
        }
    }

}