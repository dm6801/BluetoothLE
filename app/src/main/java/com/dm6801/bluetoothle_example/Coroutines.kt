package com.dm6801.bluetoothle_example

import kotlinx.coroutines.*

fun main(block: suspend CoroutineScope.() -> Unit): Job =
    CoroutineScope(Dispatchers.Main + exceptionHandler).launch(block = block)

val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throwable.printStackTrace()
}

fun <R> CoroutineScope.catch(silent: Boolean = false, action: CoroutineScope.() -> R): R? {
    return try {
        action(this)
    } catch (t: Throwable) {
        if (!silent) t.printStackTrace()
        null
    }
}