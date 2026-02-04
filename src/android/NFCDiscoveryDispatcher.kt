package com.fkmit.fido

import android.util.Log
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.smartcard.SmartCardConnection

interface NFCDiscoveryDispatcher {

    var currentNFCDevice: YubiKeyDevice?

    fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit)

    fun stopDeviceDiscovery()

    fun useDeviceConnection(dispatch: ResultDispatcher, callback: (SmartCardConnection) -> Unit) {
        runCatching {
            requireNotNull(currentNFCDevice).let {
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                it.openConnection(SmartCardConnection::class.java)
            }
        }.onSuccess { connection ->
            connection.use(callback)
        }.onFailure {
            Log.wtf("ERROR", it)
            if(currentNFCDevice != null) {
                currentNFCDevice = null
                dispatch.sendMessage(MessageCodes.SignalDeviceLost, null)
            }
            startDeviceDiscovery(InvokeOnce { device ->
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                device.openConnection(SmartCardConnection::class.java).use(callback)
            })
        }
    }

}