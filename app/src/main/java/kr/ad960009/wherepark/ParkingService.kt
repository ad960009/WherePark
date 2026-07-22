package kr.ad960009.wherepark

import android.annotation.SuppressLint
import android.app.*
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.android.gms.location.*

class ParkingService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private var bestRssi = -100
    private var bestLocation: String? = null

    private var bestAccuracy = Float.MAX_VALUE
    private var bestLatitude = 0.0
    private var bestLongitude = 0.0
    private var wakeLock: PowerManager.WakeLock? = null

    private val stopServiceRunnable = Runnable {
        stopBleScan()
        stopLocationUpdates()

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        var finalLoc = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_NOT_FOUND)

        if (finalLoc == Constants.MSG_SCANNING) {
            if (bestAccuracy != Float.MAX_VALUE) {
                finalLoc = Constants.MSG_OUTDOOR
                prefs.edit {
                    putString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_OUTDOOR)
                    putFloat(Constants.KEY_LAST_LATITUDE, bestLatitude.toFloat())
                    putFloat(Constants.KEY_LAST_LONGITUDE, bestLongitude.toFloat())
                    putFloat(Constants.KEY_LAST_ACCURACY, bestAccuracy)
                    putLong(Constants.KEY_LAST_PARKING_TIME, System.currentTimeMillis())
                }
            } else {
                finalLoc = Constants.MSG_NOT_FOUND
                prefs.edit { putString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_NOT_FOUND) }
            }
        }

        updateNotification("주차 기록 완료: $finalLoc", finalPersistent = false)
        updateWidgetWithStatus(finalLoc ?: Constants.MSG_NOT_FOUND)

        // 탐색이 완료되면 반드시 WakeLock을 해제하여 배터리 소모를 막습니다.
        releaseWakeLock()
        stopSelf()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                if (location.accuracy <= Constants.MAX_LOCATION_ACCURACY_METERS &&
                    location.accuracy < bestAccuracy) {

                    bestAccuracy = location.accuracy
                    bestLatitude = location.latitude
                    bestLongitude = location.longitude

                    println("최적 위치 갱신: ${location.latitude}, ${location.longitude} (오차: ${location.accuracy}m)")
                }
            }
        }
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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // WakeLock 초기화
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WherePark:ParkingScanWakeLock"
        )

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val notification = createNotification("어디파킹", "차량에 연결되었습니다. 주행 중...")
                startForeground(Constants.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

                stopBleScan()
                stopLocationUpdates()
                handler.removeCallbacks(stopServiceRunnable)
                releaseWakeLock()

                updateWidgetWithStatus(Constants.MSG_DRIVING)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val notification = createNotification("어디파킹", "차량 연결 해제: 주차 위치 탐색 중...")
                startForeground(Constants.NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

                startParkingScan()
            }
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startParkingScan() {
        if (isScanning) return
        isScanning = true
        bestRssi = -100
        bestLocation = null
        bestAccuracy = Float.MAX_VALUE

        // 화면이 꺼져도 CPU가 멈추지 않도록 지정된 시간만큼 WakeLock을 유지합니다.
        wakeLock?.acquire(Constants.PARKING_SCAN_DURATION_MS)

        updateNotification("차량 연결 해제: 주차 위치 탐색 중...", finalPersistent = true)
        updateWidgetWithStatus(Constants.MSG_SCANNING)

        // 화면이 꺼져있을 때도 적극적으로 스캔하도록 ScanSettings 추가
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        startLocationUpdates()

        handler.removeCallbacks(stopServiceRunnable)
        handler.postDelayed(stopServiceRunnable, Constants.PARKING_SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
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
            .setOngoing(finalPersistent)
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
        releaseWakeLock()
        handler.removeCallbacks(stopServiceRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}