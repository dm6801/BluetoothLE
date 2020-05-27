package com.dm6801.bluetoothle.utilities

import android.os.Looper

val isMainThread: Boolean get() = Thread.currentThread() == Looper.getMainLooper().thread