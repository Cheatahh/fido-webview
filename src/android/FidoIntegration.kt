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

class FidoIntegration : CordovaPlugin(), NFCDiscoveryDispatcher {

    private var nfcYubikeyManager: NfcYubiKeyManager? = null
    private var yubikitManager: YubiKitManager? = null
    private var yubikitDiscoveryCallback: ((YubiKeyDevice) -> Unit)? = null
    override var currentNFCDevice: YubiKeyDevice? = null

    private fun ensureYubikitInitialized() {
        if(nfcYubikeyManager === null)
            nfcYubikeyManager = NfcYubiKeyManager(cordova.activity, object : NfcDispatcher {
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
            })
        if(yubikitManager === null)
            yubikitManager = YubiKitManager(UsbYubiKeyManager(cordova.activity), nfcYubikeyManager)
    }

    override fun execute(action: String, args: JSONArray, callback: CallbackContext): Boolean {
        val dispatch = ResultDispatcher(callback)
        runCatching {
            when(action) {
                "getAssertion" -> RequestHandlers.getAssertion(args, this@FidoIntegration, dispatch)
                "reset" -> {
                    stopDeviceDiscovery()
                    dispatch.sendMessage(MessageCodes.Success, null)
                }
                else -> return false
            }
        }.onFailure { error ->
            dispatch.sendMessage(MessageCodes.Failure, error)
        }
        return true
    }

    override fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit) {
        synchronized(this) {
            ensureYubikitInitialized()
            yubikitDiscoveryCallback = callback
            yubikitManager?.startNfcDiscovery(NfcConfiguration().timeout(NFC_TIMEOUT), cordova.activity) { device ->
                stopDeviceDiscovery()
                currentNFCDevice = device
                callback(device)
            }
            yubikitManager?.startUsbDiscovery(UsbConfiguration()) { device ->
                stopDeviceDiscovery()
                currentNFCDevice = device
                callback(device)
            }
        }
    }

    override fun stopDeviceDiscovery() {
        synchronized(this) {
            yubikitDiscoveryCallback = null
            yubikitManager?.stopNfcDiscovery(cordova.activity)
            yubikitManager?.stopUsbDiscovery()
        }
    }

    override fun onResume(multitasking: Boolean) {
        yubikitDiscoveryCallback?.apply(::startDeviceDiscovery)
        super.onResume(multitasking)
    }

    override fun onPause(multitasking: Boolean) {
        stopDeviceDiscovery()
        super.onPause(multitasking)
    }

}