package com.fkmit.fido

import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

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

private const val NFC_TIMEOUT = 5000

class FidoIntegration : CordovaPlugin(), NFCDiscoveryDispatcher {

    private var nfcManager: NfcYubiKeyManager? = null
    private var yubikit: YubiKitManager? = null
    private var yubikitDiscovery: ((YubiKeyDevice) -> Unit)? = null

    private fun ensureYubikitInitialized() {
        if(nfcManager === null)
            nfcManager = NfcYubiKeyManager(cordova.activity, object : NfcDispatcher {
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
        if(yubikit === null)
            yubikit = YubiKitManager(UsbYubiKeyManager(cordova.activity), nfcManager)
    }

    override fun execute(action: String, args: JSONArray, callback: CallbackContext): Boolean {
        fun sendStatusResult(code: StatusCodes, payload: Any) {
            val result = PluginResult(code.resultStatus, JSONObject().apply {
                put("statusCode", code.code)
                put("payload", payload)
            })
            result.setKeepCallback(code === StatusCodes.Progress)
            callback.sendPluginResult(result)
        }
        fun useHandler(handler: () -> Any): Boolean {
            runCatching(handler).onSuccess { result ->
                sendStatusResult(StatusCodes.Success, result)
            }.onFailure { err ->
                sendStatusResult(StatusCodes.Failure, err.message.toString())
            }
            return true
        }
        return when(action) {
            "getAssertion" -> useHandler { FidoHandlers.getAssertion(args, this@FidoIntegration) }
            else -> false
        }
    }

    override fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit) {
        synchronized(this) {
            yubikitDiscovery = callback
            yubikit?.startNfcDiscovery(NfcConfiguration().timeout(NFC_TIMEOUT), cordova.activity) { device ->
                callback(device)
            }
            yubikit?.startUsbDiscovery(UsbConfiguration()) { device ->
                callback(device)
            }
        }
    }

    override fun stopDeviceDiscovery() {
        synchronized(this) {
            yubikitDiscovery = null
            yubikit?.stopNfcDiscovery(cordova.activity)
            yubikit?.stopUsbDiscovery()
        }
    }

    override fun onResume(multitasking: Boolean) {
        yubikitDiscovery?.apply(::startDeviceDiscovery)
        super.onResume(multitasking)
    }

    override fun onPause(multitasking: Boolean) {
        stopDeviceDiscovery()
        super.onPause(multitasking)
    }
}