@file:Suppress("FunctionName")

package com.dm6801.bluetoothle.utilities

import android.os.Build

private const val MAX_LOG_LENGTH = 4068

val isUnitTest: Boolean
    get() {
        return Build.BRAND == null && Build.DEVICE == null && Build.PRODUCT == null
    }

fun <E : Any> E.Log(arg: Any?, level: Int = android.util.Log.DEBUG) {
    log(javaClass.name.substringAfterLast(".").substringBefore("$"), arg, level)
}

fun <E : Any> E.Log(
    title: Any,
    arg: Any?,
    level: Int = android.util.Log.DEBUG
) {
    val isUnitTest = isUnitTest
    log(
        tag =
        if (isUnitTest) title.toString()
        else javaClass.name.substringAfterLast(".").substringBefore("$"),
        arg = if (isUnitTest) arg else "$title: $arg",
        level = level
    )
}

fun log(
    tag: String,
    arg: Any?,
    level: Int = android.util.Log.DEBUG
) {
    val string = arg.toString()
    if (string.length <= MAX_LOG_LENGTH)
        _log(tag, string, level)
    else
        string.chunkedSequence(MAX_LOG_LENGTH)
            .forEach { _log(tag, it, level) }
}

private fun _log(
    tag: String,
    arg: Any?,
    level: Int = android.util.Log.DEBUG
) {
    if (isUnitTest) {
        println("$tag: $arg")
    } else {
        android.util.Log.println(
            level,
            tag,
            arg.toString()
        )
    }
}

fun Any?.justify(
    prefix: String? = null,
    suffix: String? = null,
    size: Int? = null
): String {
    return if (this != null) {
        val _string = (prefix ?: "") + toString() + (suffix ?: "")
        val _length = size ?: (kotlin.math.floor(_string.length / 10f) + 1).toInt() * 10
        String.format("%-${_length}s", _string)
    } else ""
}

fun Any?.hashCode(radix: Int) = hashCode().toString(radix)