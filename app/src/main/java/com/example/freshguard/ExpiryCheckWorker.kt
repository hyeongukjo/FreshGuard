package com.example.freshguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.postgrest.postgrest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// CoroutineWorker를 상속받아 백그라운드에서 인터넷(DB) 통신이 가능하게 만듭니다.
class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    // 이 함수가 바로 일꾼이 정해진 시간마다 일어나서 할 '업무'입니다.
    override suspend fun doWork(): Result {
        val sharedPref = applicationContext.getSharedPreferences("freshguard_prefs", Context.MODE_PRIVATE)
        val isNotiEnabled = sharedPref.getBoolean("noti_enabled", true)

        // 알림이 꺼져있다면 뒤의 코드를 실행하지 않고 바로 종료합니다.
        if (!isNotiEnabled) return Result.success()
        return try {
            // 1. Supabase에서 현재 저장된 모든 식재료 가져오기
            val data = SupabaseManager.client.postgrest["ingredients"]
                .select()
                .decodeList<Ingredient>()

            // 2. 유통기한이 3일 이하로 남은(혹은 이미 지난) 식재료 개수 세기
            var imminentCount = 0
            val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
            val today = Date()

            for (item in data) {
                try {
                    val expiration = format.parse(item.expiryDate) ?: continue
                    val diff = expiration.time - today.time
                    val dDay = TimeUnit.MILLISECONDS.toDays(diff)

                    // D-Day가 3일 이하로 남았거나 이미 지났다면 카운트 증가!
                    if (dDay <= 3) {
                        imminentCount++
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            // 3. 임박한 식재료가 1개라도 있다면 스마트폰 알림 띄우기!
            if (imminentCount > 0) {
                showNotification(imminentCount)
            }

            Result.success() // 업무 성공적으로 완료!
        } catch (e: Exception) {
            Result.retry() // 인터넷이 끊겼거나 실패하면 나중에 다시 시도
        }
    }

    // ★ 스마트폰 상단바에 알림(푸시)을 만들어주는 함수
    private fun showNotification(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "freshguard_channel"

        // 안드로이드 8.0 이상에서는 '알림 채널'을 무조건 만들어야 알림이 보입니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "유통기한 임박 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 알림을 눌렀을 때 우리 앱(MainActivity)이 켜지도록 설정
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // 알림의 모양과 내용 꾸미기
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 기본 아이콘 사용
            .setContentTitle("🚨 유통기한 임박!")
            .setContentText("상하기 전에 먹어야 할 식재료가 ${count}개 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // 클릭 시 앱 켜기
            .setAutoCancel(true) // 누르면 알림 사라짐
            .build()

        notificationManager.notify(1, notification)
    }
}