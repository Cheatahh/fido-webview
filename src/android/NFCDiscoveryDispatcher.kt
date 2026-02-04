package com.fkmit.fido

import android.nfc.TagLostException
import android.util.Log
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.application.InvalidPinException
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.core.smartcard.SmartCardConnection

interface NFCDiscoveryDispatcher {

    var currentNFCDevice: YubiKeyDevice?

    fun startDeviceDiscovery(callback: (YubiKeyDevice) -> Unit)

    fun stopDeviceDiscovery()

    private fun runWithCatching(dispatch: ResultDispatcher, block: () -> Unit) {
        Log.e("DEBUG", "runWithCatching")
        try {
            block()
        } catch (_: TagLostException) {
            currentNFCDevice = null
            dispatch.sendMessage(MessageCodes.FailureDeviceLost, null)
        } catch (_: InvalidPinException) {
            dispatch.sendMessage(MessageCodes.FailureInvalidPin, null)
        } catch (e: CtapException) {
            dispatch.sendMessage(if(e.ctapError == CtapException.ERR_NO_CREDENTIALS) MessageCodes.FailureNoCredentials else MessageCodes.FailureUnsupportedDevice, null)
        } catch (e: Exception) {
            dispatch.sendMessage(MessageCodes.Failure, e.message)
        }
        Log.e("DEBUG", "runWithCatching2")
    }

    fun useDeviceConnection(dispatch: ResultDispatcher, callback: (SmartCardConnection) -> Unit) {
        runCatching {
            requireNotNull(currentNFCDevice).let {
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                it.openConnection(SmartCardConnection::class.java)
            }
        }.onSuccess { connection ->
            runWithCatching(dispatch) { connection.use(callback) }
        }.onFailure {
            stopDeviceDiscovery()
            startDeviceDiscovery(InvokeOnce { device ->
                dispatch.sendMessage(MessageCodes.SignalDeviceDiscovered, null)
                runWithCatching(dispatch) {
                    device.openConnection(SmartCardConnection::class.java).use(callback)
                }
            })
        }
    }

}