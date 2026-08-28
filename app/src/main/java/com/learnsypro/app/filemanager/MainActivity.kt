package com.learnsypro.app.filemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.learnsypro.app.R
import com.learnsypro.app.filemanager.fragments.CloudFragment
import com.learnsypro.app.filemanager.fragments.ClientFragment
import com.learnsypro.app.filemanager.fragments.ServerFragment
import com.learnsypro.app.filemanager.fragments.SettingsFragment
import com.learnsypro.app.filemanager.util.ActivityTransitions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : LearnsyFileManagerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(R.string.app_name)
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finishWithAnimation() }
        }
        // Activity vẽ edge-to-edge -> spacer riêng (status_bar_spacer) chừa đúng chiều cao status
        // bar phía trên Toolbar tại runtime, Toolbar giữ nguyên actionBarSize cố định (không bị
        // phình to như khi cộng padding-top thẳng vào Toolbar).
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyTopInsetHeight(findViewById(R.id.status_bar_spacer))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })

        requestRuntimePermissions()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_server -> ServerFragment()
                R.id.nav_client -> ClientFragment()
                R.id.nav_cloud -> CloudFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> ServerFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_server
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun finishWithAnimation() {
        finish()
        ActivityTransitions.backward(this)
    }

    companion object {
        private const val REQ_PERMISSIONS = 100
    }
}
