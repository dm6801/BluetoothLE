package com.dm6801.bluetoothle.utilities

fun <R> catch(silent: Boolean = false, action: () -> R): R? {
    return try {
        action()
    } catch (t: Throwable) {
        if (!silent) t.printStackTrace()
        null
    }
}