package com.dm6801.bluetoothle_example

import androidx.fragment.app.Fragment

abstract class BaseFragment : Fragment() {

    open fun onForeground() {}

    open fun onBackground() {}

}