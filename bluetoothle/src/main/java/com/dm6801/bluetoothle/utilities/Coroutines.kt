package com.dm6801.bluetoothle.utilities

import kotlinx.coroutines.*

fun main(block: suspend CoroutineScope.() -> Unit): Job =
    CoroutineScope(Dispatchers.Main + exceptionHandler).launch(block = block)

val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throwable.printStackTrace()
}