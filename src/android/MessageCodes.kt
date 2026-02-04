package com.fkmit.fido

import org.apache.cordova.PluginResult

private const val FLAG_SUCCESS = 0x1000
private const val FLAG_ERROR = 0x2000
private const val FLAG_SIGNAL = 0x3000

enum class MessageCodes(val code: Int, val resultStatus: PluginResult.Status, val isTerminal: Boolean) {

    Success(FLAG_SUCCESS, PluginResult.Status.OK, true),

    Failure(FLAG_ERROR, PluginResult.Status.ERROR, true),

    FailureInvalidPin(FLAG_ERROR or 0x0001, PluginResult.Status.ERROR, true),

    FailureUnsupportedDevice(FLAG_ERROR or 0x0002, PluginResult.Status.ERROR, true),

    FailureDeviceLost(FLAG_SIGNAL or 0x0003, PluginResult.Status.ERROR, true),

    FailureNoCredentials(FLAG_SIGNAL or 0x0004, PluginResult.Status.ERROR, true),

    FailureTooManyCredentials(FLAG_SUCCESS or 0x0005, PluginResult.Status.ERROR, true),

    SignalProgressUpdate(FLAG_SIGNAL or 0x0001, PluginResult.Status.OK, false),

    SignalDeviceDiscovered(FLAG_SIGNAL or 0x0002, PluginResult.Status.OK, false)

}