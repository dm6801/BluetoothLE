package com.dm6801.bluetoothle_example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.commit
import com.dm6801.bluetoothle.BLE

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 35431
        private val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val fragmentContainer = R.id.fragment_container
        private val landing = MainFragment::class.java
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BLE.init(this, log = true)
        getPermissions()
        setLanding()
    }

    private fun setLanding() {
        supportFragmentManager.commit {
            add(fragmentContainer, MainFragment(), MainFragment::class.simpleName)
        }
    }

    private fun getPermissions() {
        if (permissions.any {
                PermissionChecker.checkSelfPermission(this, it) !=
                        PermissionChecker.PERMISSION_GRANTED
            }) ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_CODE)
    }

}