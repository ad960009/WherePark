package kr.ad960009.wherepark

import android.annotation.SuppressLint
import android.app.*
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.edit

class ParkingService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private var bestRssi = -100
    private var bestLocation: String? = null

    // 5분 후 스캔 자동 종료 및 서비스 종료를 위한 타이머
    private val stopServiceRunnable = Runnable {
        stopBleScan()

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        var finalLoc = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_NOT_FOUND)

        if (finalLoc == Constants.MSG_SCANNING) {
            finalLoc = Constants.MSG_NOT_FOUND
            prefs.edit { putString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_NOT_FOUND) }
        }

        // 최종 알림을 띄우고 서비스 종료
        updateNotification("주차 기록 완료: $finalLoc", finalPersistent = false)
        updateWidgetWithStatus(finalLoc ?: Constants.MSG_NOT_FOUND)

        // 모든 작업 완료 후 서비스 종료 (상태바 알림 사라짐)
        stopSelf()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val rssi = result.rssi
            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            val alias = prefs.getString(address, null)

            if (alias != null && rssi > Constants.MIN_RSSI_THRESHOLD) {
                saveParkingLocation(alias, rssi)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // 초기 포그라운드 설정 (안드로이드 정책상 필수)
        val initialNotification = createNotification("서비스 활성화", "차량 연결 상태를 확인 중입니다.")
        startForeground(Constants.NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // 주행 시작 시: 기존 스캔 중단하고 주행 중 상태 유지
                stopBleScan()
                handler.removeCallbacks(stopServiceRunnable)
                updateNotification("차량 연결됨: 주행 중...", finalPersistent = true)
                updateWidgetWithStatus(Constants.MSG_DRIVING)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // 차량 연결 해제 시: 주차 위치 스캔 시작
                startParkingScan()
            }
            else -> {
                // 액션 없이 실행된 경우 (예: 앱에서 직접 실행)
                // 현재 연결 상태를 확인하여 분기 처리 가능하지만, 기본적으로 대기
            }
        }

        return START_NOT_STICKY // 작업 완료 후 종료되도록 설정
    }

    @SuppressLint("MissingPermission")
    private fun startParkingScan() {
        if (isScanning) return
        isScanning = true
        bestRssi = -100
        bestLocation = null

        updateNotification("차량 연결 해제: 주차 위치 탐색 중...", finalPersistent = true)
        updateWidgetWithStatus(Constants.MSG_SCANNING)

        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)

        // 5분 후 서비스 완전 종료 예약
        handler.removeCallbacks(stopServiceRunnable)
        handler.postDelayed(stopServiceRunnable, Constants.PARKING_SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }

    private fun saveParkingLocation(alias: String, rssi: Int) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        if (rssi > bestRssi) {
            bestRssi = rssi
            bestLocation = alias

            prefs.edit().apply {
                putString(Constants.KEY_LAST_PARKING_LOCATION, alias)
                putLong(Constants.KEY_LAST_PARKING_TIME, System.currentTimeMillis())
                apply()
            }
            updateNotification("주차 위치 탐색 중... 현재 최적: $alias", finalPersistent = true)
            updateWidget()
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String, finalPersistent: Boolean) {
        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle("어디파킹")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(finalPersistent) // false이면 사용자가 밀어서 지울 수 있음
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.CHANNEL_ID,
            Constants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun updateWidget() {
        val intent = Intent(this, ParkingWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, ParkingWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun updateWidgetWithStatus(status: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putString(Constants.KEY_LAST_PARKING_LOCATION, status) }
        updateWidget()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        handler.removeCallbacks(stopServiceRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
