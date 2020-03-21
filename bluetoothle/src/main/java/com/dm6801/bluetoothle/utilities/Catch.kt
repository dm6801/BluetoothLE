package com.dm6801.bluetoothle.utilities

import kotlinx.coroutines.CoroutineScope

fun <R> catch(silent: Boolean = false, action: () -> R): R? {
    return try {
        action()
    } catch (t: Throwable) {
        if (!silent) t.printStackTrace()
        null
    }
}

fun <R> CoroutineScope.catch(silent: Boolean = false, action: CoroutineScope.() -> R): R? {
    return try {
        action(this)
    } catch (t: Throwable) {
        if (!silent) t.printStackTrace()
        null
    }
}