package com.example.freshguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.work.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.TimeUnit





class MainActivity : AppCompatActivity() {

    // ★ 권한 요청 런처는 이렇게 클래스 최상단에 선언해야 안전합니다.

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "알림 권한을 허용해야 유통기한 알림을 받을 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 스플래시 설정 (제일 먼저!)
        val splashScreen = installSplashScreen()
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        super.onCreate(savedInstanceState)

        // 2. 레이아웃 설정 (딱 한 번만 호출하세요!)
        setContentView(R.layout.activity_main)

        // 스플래시 종료 예약 (2초 뒤)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            keepSplashScreen = false
        }, 2000)

        // 3. 기능 초기화
        checkNotificationPermission()
        startExpiryCheckWorker()

        // 4. 하단바 설정
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 처음 앱을 켰을 때 보여줄 화면 설정
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> replaceFragment(HomeFragment())
                R.id.navigation_scan -> replaceFragment(ScanFragment())
                R.id.navigation_recipe -> replaceFragment(RecipeFragment())
                R.id.navigation_settings -> replaceFragment(SettingsFragment())
            }
            true // 리턴값이 true여야 아이콘이 선택 상태로 바뀝니다.
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun startExpiryCheckWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(15, TimeUnit.SECONDS)
            //.setConstraints(
             //   Constraints.Builder()
                   // .setRequiredNetworkType(NetworkType.CONNECTED)
                  //  .build()
           // )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ExpiryCheckWork",
            ExistingPeriodicWorkPolicy.REPLACE, // KEEP -> REPLACE로 변경
            workRequest
        )
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}