package com.dm6801.bluetoothle.utilities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun main(block: suspend CoroutineScope.() -> Unit): Job =
    CoroutineScope(Dispatchers.Main).launch(block = block)