package com.dm6801.bluetoothle_example

import kotlinx.coroutines.*

fun main(block: suspend CoroutineScope.() -> Unit): Job =
    CoroutineScope(Dispatchers.Main + exceptionHandler).launch(block = block)

val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throwable.printStackTrace()
}

suspend fun <R> CoroutineScope.catch(silent: Boolean = false, action: suspend CoroutineScope.() -> R): R? {
    return try {
        action(this)
    } catch (t: Throwable) {
        if (!silent) t.printStackTrace()
        null
    }
}

fun <R> CoroutineScope.launchCatch(
    silent: Boolean = false,
    action: suspend CoroutineScope.() -> R
): Job {
    return launch { catch(silent, action) }
}