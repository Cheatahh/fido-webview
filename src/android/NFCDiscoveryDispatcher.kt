package com.fkmit.fido

import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.smartcard.SmartCardConnection

interface NFCDiscoveryDispatcher {

    var currentNFCDevice: YubiKeyDevice?

    fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit)

    fun stopDeviceDiscovery()

    fun useDeviceConnection(dispatch: ResultDispatcher, callback: (SmartCardConnection) -> Unit) {
        runCatching {
            dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
            requireNotNull(currentNFCDevice).openConnection(SmartCardConnection::class.java)
        }.onSuccess { connection ->
            connection.use(callback)
        }.onFailure {
            if(currentNFCDevice != null) {
                currentNFCDevice = null
                dispatch.sendMessage(MessageCodes.SignalDeviceLost, null)
            }
            startDeviceDiscovery { device ->
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                device.openConnection(SmartCardConnection::class.java).use(callback)
            }
        }
    }

}