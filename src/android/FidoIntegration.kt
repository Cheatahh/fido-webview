package com.fkmit.fido

import android.app.Activity
import android.nfc.NfcAdapter
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcDispatcher
import com.yubico.yubikit.android.transport.nfc.NfcReaderDispatcher
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyManager
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyManager
import com.yubico.yubikit.core.YubiKeyDevice
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray

private const val NFC_TIMEOUT = 5000

/**
 * Cordova plugin integration that manages YubiKit NFC and USB discovery and
 * exposes FIDO operations to the Cordova layer.
 *
 * Implements [NFCDiscoveryDispatcher] to provide a centralized mechanism for
 * discovering `YubiKeyDevice` instances and opening `SmartCardConnection`s.
 *
 * Discovery is managed via [YubiKitManager] backed by a [UsbYubiKeyManager] and
 * an [NfcYubiKeyManager] with a small inline [NfcDispatcher] implementation.
 */
class FidoIntegration : CordovaPlugin(), NFCDiscoveryDispatcher {

    /**
     * Lazily-initialized manager for NFC-backed YubiKey access.
     */
    private val nfcYubikeyManager by lazy {
        NfcYubiKeyManager(cordova.activity, object : NfcDispatcher {
            private var nfcAdapter: NfcAdapter? = null
            private var nfcReaderDispatcher: NfcReaderDispatcher? = null
            override fun enable(activity: Activity, nfcConfiguration: NfcConfiguration, handler: NfcDispatcher.OnTagHandler) {
                nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
                nfcReaderDispatcher = NfcReaderDispatcher(nfcAdapter!!)
                nfcReaderDispatcher?.enable(activity, nfcConfiguration, handler)
            }
            override fun disable(activity: Activity) {
                nfcReaderDispatcher?.disable(activity)
            }
        }).also { log { "nfcYubikeyManager initialized" } }
    }

    /**
     * Lazily-initialized top-level YubiKit manager that coordinates USB and NFC discovery.
     */
    private val yubikitManager by lazy {
        YubiKitManager(UsbYubiKeyManager(cordova.activity), nfcYubikeyManager)
            .also { log { "yubikitManager initialized" } }
    }

    /**
     * Optional discovery callback currently registered by callers. Stored so discovery
     * can be restarted when the activity lifecycle changes.
     */
    private var yubikitDiscoveryCallback: ((YubiKeyDevice) -> Unit)? = null

    /**
     * The most recently discovered `YubiKeyDevice`, or `null` when none is available.
     * This property fulfills the [NFCDiscoveryDispatcher] contract and is updated by
     * `startDeviceDiscovery` and discovery callbacks.
     */
    override var currentNFCDevice: YubiKeyDevice? = null

    /**
     * Cordova action dispatcher.
     *
     * Recognized actions:
     *  - `"getAssertion"`: delegates to [RequestHandlers.getAssertion]
     *  - `"nfcDevNull"`: starts discovery and replies with success (no-op device)
     *  - `"reset"`: stops discovery and clears the cached device
     *
     * Errors thrown during action handling are caught and reported to the provided
     * [callback] via a [ResultDispatcher].
     *
     * @param action the action name invoked from JavaScript
     * @param args the JSON array of arguments
     * @param callback the Cordova [CallbackContext] used to return results
     * @return `true` for handled actions, `false` otherwise
     */
    override fun execute(action: String, args: JSONArray, callback: CallbackContext): Boolean {
        log { "-- execute (action = $action, args = $args) --" }
        val dispatch = ResultDispatcher(callback)
        runCatching {
            when(action) {
                "getAssertion" -> {
                    RequestHandlers.getAssertion(args, this@FidoIntegration, dispatch)
                }
                "nfcDevNull" -> {
                    startDeviceDiscovery {}
                    dispatch.sendMessage(MessageCodes.Success, null)
                }
                "reset" -> {
                    stopDeviceDiscovery()
                    currentNFCDevice = null
                    dispatch.sendMessage(MessageCodes.Success, null)
                }
                else -> return false
            }
        }.onFailure { error ->
            dispatch.sendMessage(MessageCodes.Failure, error.message)
        }
        return true
    }

    /**
     * Start discovery for YubiKey devices and deliver discovered devices to [callback].
     *
     * This implementation:
     *  - Ensures YubiKit is initialized
     *  - Stops any previous discovery
     *  - Stores the provided [callback] so discovery can be restarted on lifecycle changes
     *  - Starts both NFC and USB discovery. NFC discovery uses a timeout defined by [NFC_TIMEOUT].
     *
     * This method is synchronized to prevent concurrent discovery state changes.
     *
     * @param callback function invoked for each discovered [YubiKeyDevice]
     */
    override fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit) {
        log { "-- startDeviceDiscovery --" }
        synchronized(this) {
            stopDeviceDiscovery()
            yubikitDiscoveryCallback = callback
            yubikitManager.startNfcDiscovery(NfcConfiguration().timeout(NFC_TIMEOUT), cordova.activity) { device ->
                callback(device)
            }
            yubikitManager.startUsbDiscovery(UsbConfiguration()) { device ->
                callback(device)
            }
        }
    }

    /**
     * Stop any ongoing NFC or USB discovery and clear the stored discovery callback.
     *
     * This method is synchronized to prevent concurrent discovery state changes.
     */
    override fun stopDeviceDiscovery() {
        log { "-- stopDeviceDiscovery --" }
        synchronized(this) {
            yubikitDiscoveryCallback = null
            yubikitManager.stopNfcDiscovery(cordova.activity)
            yubikitManager.stopUsbDiscovery()
        }
    }

    /**
     * Cordova lifecycle hook. If a discovery callback is currently registered,
     * restart discovery when the activity resumes.
     *
     * @param multitasking unused; required by the Cordova API
     */
    override fun onResume(multitasking: Boolean) {
        log { "-- onResume --" }
        yubikitDiscoveryCallback?.apply(::startDeviceDiscovery)
        super.onResume(multitasking)
    }

    /**
     * Cordova lifecycle hook. Temporarily stops discovery while preserving the
     * previously-registered callback so discovery can be resumed in [onResume].
     *
     * @param multitasking unused; required by the Cordova API
     */
    override fun onPause(multitasking: Boolean) {
        log { "-- onPause --" }
        yubikitDiscoveryCallback.also { swap ->
            stopDeviceDiscovery()
            yubikitDiscoveryCallback = swap
        }
        super.onPause(multitasking)
    }

}