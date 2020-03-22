package com.dm6801.bluetoothle_example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.dm6801.bluetoothle.BLE
import com.dm6801.bluetoothle.utilities.catch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 35431
        private val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val fragmentContainer = R.id.fragment_container
    }

    val logHandler = BleLogHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BLE.init(this, logHandler)
        getPermissions()
        initFragmentListener()
        setLanding()
    }

    private fun initFragmentListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 1)
            val foreground = supportFragmentManager.findFragmentById(fragmentContainer)
            supportFragmentManager.fragments.forEach { fragment ->
                (fragment as? BaseFragment)?.let {
                    if (it == foreground) it.onForeground()
                    else it.onBackground()
                }
            }
        }
    }

    fun addFragment(fragment: Fragment) = catch {
        supportFragmentManager.commit {
            addToBackStack(fragment.javaClass.simpleName)
            setCustomAnimations(
                androidx.fragment.R.anim.fragment_open_enter,
                androidx.fragment.R.anim.fragment_open_exit,
                androidx.fragment.R.anim.fragment_close_enter,
                androidx.fragment.R.anim.fragment_close_exit
            )
            add(fragmentContainer, fragment, fragment.javaClass.simpleName)
        }
    }

    private fun setLanding() {
        addFragment(MainFragment())
    }

    private fun getPermissions() {
        if (permissions.any {
                PermissionChecker.checkSelfPermission(this, it) !=
                        PermissionChecker.PERMISSION_GRANTED
            }) ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_CODE)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navigateBack() ?: super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (navigateBack() != true)
            finish()
    }

    fun navigateBack(): Boolean? {
        return if (supportFragmentManager.backStackEntryCount > 1)
            catch { supportFragmentManager.popBackStackImmediate() }
        else false
    }

}