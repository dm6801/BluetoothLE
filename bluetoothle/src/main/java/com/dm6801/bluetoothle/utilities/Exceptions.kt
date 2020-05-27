package com.dm6801.bluetoothle.utilities

import com.dm6801.bluetoothle.BLE.getStateString

typealias BleException = Throwable

class NotOnMainThreadException :
    BleException("BLE operations must be executed on main thread (current thread: ${Thread.currentThread()}")

sealed class GattException(
    override val message: String? = null,
    override val cause: Throwable? = null
) : BleException(message, cause) {
    companion object {
        operator fun invoke(state: Int? = null): GattException =
            GattException::class.java.let { ctor ->
                (ctor.declaredConstructors.firstOrNull()
                    ?.newInstance(state?.let { "state: ${getStateString(it)}" }) as? GattException)
                    ?: ctor.newInstance()
            }
    }

    class Undefined : GattException("callback is undefined")
    class NotConnected(state: Int) : GattException("state: ${getStateString(state)}")
    class NotWritable(state: Int) : GattException("state: ${getStateString(state)}")
}