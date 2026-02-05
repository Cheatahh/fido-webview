package com.fkmit.fido

import com.yubico.yubikit.core.YubiKeyDevice

/**
 * A one-shot wrapper around a callback that accepts a [YubiKeyDevice].
 *
 * This class implements the function type `(YubiKeyDevice) -> Unit` so it can be passed
 * wherever a function of that signature is expected. The provided [callback] will be
 * executed at most once; subsequent invocations are ignored.
 *
 * Note: This implementation is not synchronized and is not safe for concurrent use
 * from multiple threads. If you need thread-safety, add appropriate synchronization.
 *
 * @param callback the function to invoke the first time a [YubiKeyDevice] is provided
 */
class InvokeOnce(private val callback: (YubiKeyDevice) -> Unit): (YubiKeyDevice) -> Unit {

    /**
     * Tracks whether the callback has already been invoked.
     * When `true`, further calls to [invoke] will return immediately without calling [callback].
     */
    private var isInvoked = false

    /**
     * Invoke the wrapped callback with the given [device], but only on the first call.
     * If the callback has already been invoked once, this method returns immediately.
     * @param device the [YubiKeyDevice] to pass to the wrapped callback
     */
    override fun invoke(device: YubiKeyDevice) {
        log { "-- invoke --" }
        if(isInvoked) return
        isInvoked = true
        return callback(device)
    }

}