package kr.ad960009.wherepark

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ParkingService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "PARKING_CHANNEL")
            .setContentTitle("주차 위치 탐색 중")
            .setContentText("주변 비컨 신호를 감시하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        // 포그라운드 서비스 시작 (안드로이드 16 기준 필수)
        startForeground(1, notification)

        // 여기에 블루투스 스캔 로직을 옮겨올 예정입니다.

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "PARKING_CHANNEL",
            "주차 서비스 채널",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}