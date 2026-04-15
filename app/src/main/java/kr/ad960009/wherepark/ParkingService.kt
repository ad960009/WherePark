package kr.ad960009.wherepark

import android.annotation.SuppressLint
import android.app.*
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ParkingService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private var bestRssi = -100 // 가장 좋은 신호 세기 저장용
    private var bestLocation: String? = null

    // 5분 후 스캔 자동 종료를 위한 타이머
    private val stopScanRunnable = Runnable {
        stopBleScan()

        val finalLoc = getSharedPreferences("WhereParkPrefs", MODE_PRIVATE)
            .getString("LAST_PARKING_LOCATION", "알 수 없음")

        // 스캔이 종료될 때 최종 위치를 확정 알림으로 띄움
        updateNotification("주차 기록 완료: $finalLoc")

        // 초기화
        bestRssi = -100
        bestLocation = null
    }

    // 1. 블루투스 연결 해제 감지 리시버
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val prefs = getSharedPreferences("WhereParkPrefs", MODE_PRIVATE)
                val myCarAddress = prefs.getString("MY_CAR_ADDRESS", null)

                // 등록된 내 차와 연결이 끊긴 경우에만 스캔 시작
                if (device?.address == myCarAddress && myCarAddress != null) {
                    startParkingScan()
                }
            }
        }
    }

    // 2. 서비스 전용 스캔 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val rssi = result.rssi

            val prefs = getSharedPreferences("WhereParkPrefs", MODE_PRIVATE)
            val alias = prefs.getString(address, null)

            // 등록된 비컨이 기준치(-85dBm)보다 강하게 감지되면 위치 저장
            if (alias != null && rssi > -85) {
                saveParkingLocation(alias, address, rssi)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // 리시버 등록
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // 포그라운드 알림 생성
        val notification = NotificationCompat.Builder(this, "PARKING_CHANNEL")
            .setContentTitle("어디파킹 활성화")
            .setContentText("연결 해제 시 주차 위치를 기록합니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startParkingScan() {
        if (isScanning) return

        isScanning = true
        updateNotification("차량 연결 해제: 5분간 주차 층수 탐색 중...")

        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)

        // 5분 후 스캔 중단 예약
        handler.removeCallbacks(stopScanRunnable)
        handler.postDelayed(stopScanRunnable, 5 * 60 * 1000)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }

    private fun saveParkingLocation(alias: String, address: String, rssi: Int) {
        val prefs = getSharedPreferences("WhereParkPrefs", MODE_PRIVATE)

        // 💡 단순히 마지막 것이 아니라, 5분 스캔 동안 "가장 가까웠던(강했던)" 장치를 기록
        if (rssi > bestRssi) {
            bestRssi = rssi
            bestLocation = alias

            prefs.edit().apply {
                putString("LAST_PARKING_LOCATION", alias)
                putLong("LAST_PARKING_TIME", System.currentTimeMillis())
                apply()
            }

            // 알림창에도 가장 확실한 현재 위치를 갱신
            updateNotification("주차 위치 탐색 중... 현재 최적: $alias")
        }

        updateWidget()

        println("비컨 감지: $alias ($rssi dBm)")
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

    private fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, "PARKING_CHANNEL")
            .setContentTitle("어디파킹 알림")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOnlyAlertOnce(false)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "PARKING_CHANNEL",
            "주차 서비스 기록",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        handler.removeCallbacks(stopScanRunnable)
        stopBleScan()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}