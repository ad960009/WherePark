package kr.ad960009.wherepark

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kr.ad960009.wherepark.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false

    private lateinit var scanAdapter: DeviceAdapter
    private lateinit var registeredAdapter: RegisteredDeviceAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val bluetoothGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false

        if (fineLocationGranted && bluetoothGranted) {
            checkBackgroundLocationPermission()
        } else {
            Toast.makeText(this, "필수 권한(위치, 블루투스)이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "모든 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()
            startBackgroundService()
        } else {
            Toast.makeText(this, "백그라운드 위치 권한이 거부되어 야외 주차 기록이 제한될 수 있습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        setupRecyclerViews()
        loadRegisteredDevices()
        loadMyCarData()

        binding.btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        binding.btnRegisterCar.setOnClickListener {
            checkPermissionsAndSelectCar()
        }

        binding.btnOpenMap.setOnClickListener {
            openOutdoorMap()
        }

        startBackgroundService()
    }

    private fun checkPermissionsAndScan() {
        if (hasBasicPermissions()) {
            if (hasBackgroundLocationPermission()) {
                startBleScan()
            } else {
                showBackgroundLocationRationale()
            }
        } else {
            requestBasicPermissions()
        }
    }

    private fun checkPermissionsAndSelectCar() {
        if (hasBasicPermissions()) {
            showPairedDevicesDialog()
            if (!hasBackgroundLocationPermission()) {
                showBackgroundLocationRationale()
            }
        } else {
            requestBasicPermissions()
        }
    }

    private fun hasBasicPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBasicPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        requestPermissionLauncher.launch(requiredPermissions)
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (!hasBackgroundLocationPermission()) {
            showBackgroundLocationRationale()
        }
    }

    private fun showBackgroundLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle("백그라운드 위치 권한 필요")
            .setMessage("차량 연결 해제 시 야외 주차 위치를 자동으로 기록하기 위해 위치 권한을 '항상 허용'으로 설정해야 합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "블루투스를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isScanning) {
            isScanning = true
            binding.btnScan.text = "스캔 중지..."
            scanner.startScan(scanCallback)
            binding.root.postDelayed({ stopBleScan() }, Constants.SCAN_DURATION_MS)
        } else {
            stopBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (isScanning) {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            scanner?.stopScan(scanCallback)
            isScanning = false
            binding.btnScan.text = "스캔 시작"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            var deviceName = "Unknown Device"
            val deviceAddress = result.device.address
            val rssi = result.rssi

            try {
                deviceName = result.device.name ?: "Unknown Device"
            } catch (_: SecurityException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "기기 이름을 가져올 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            runOnUiThread {
                scanAdapter.addDevice(BeaconDevice(deviceName, deviceAddress, rssi))
                registeredAdapter.updateRssi(deviceAddress, rssi)
            }
        }
    }

    private fun setupRecyclerViews() {
        scanAdapter = DeviceAdapter { device -> showRegisterDialog(device) }
        binding.rvDevices.apply {
            adapter = scanAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        registeredAdapter = RegisteredDeviceAdapter(
            onEdit = { device -> showEditDialog(device) },
            onDelete = { device -> showDeleteDialog(device) }
        )
        binding.rvRegisteredDevices.apply {
            adapter = registeredAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun loadRegisteredDevices() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val allEntries = prefs.all
        val registeredList = mutableListOf<BeaconDevice>()
        val excludeKeys = listOf(
            Constants.KEY_MY_CAR_NAME,
            Constants.KEY_MY_CAR_ADDRESS,
            Constants.KEY_LAST_PARKING_LOCATION,
            Constants.KEY_LAST_PARKING_TIME,
            Constants.KEY_LAST_LATITUDE,
            Constants.KEY_LAST_LONGITUDE,
            Constants.KEY_LAST_ACCURACY
        )

        for ((key, value) in allEntries) {
            if (key !in excludeKeys) {
                registeredList.add(BeaconDevice(value.toString(), key, 0))
            }
        }
        registeredAdapter.setItems(registeredList)
    }

    private fun saveDeviceData(address: String, alias: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putString(address, alias) }
    }

    private fun showRegisterDialog(device: BeaconDevice) {
        val input = EditText(this).apply { hint = "예: 내 차 옆 기둥 / 지하 2층" }
        AlertDialog.Builder(this)
            .setTitle("주차 장치 등록")
            .setMessage("${device.address} 장치에 이름을 붙여주세요.")
            .setView(input)
            .setPositiveButton("등록") { _, _ ->
                val alias = input.text.toString()
                if (alias.isNotEmpty()) {
                    saveDeviceData(device.address, alias)
                    loadRegisteredDevices()
                    Toast.makeText(this, "[$alias] 등록되었습니다!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditDialog(device: BeaconDevice) {
        val input = EditText(this).apply { setText(device.name) }
        AlertDialog.Builder(this)
            .setTitle("이름 수정")
            .setView(input)
            .setPositiveButton("수정") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotEmpty()) {
                    saveDeviceData(device.address, newName)
                    loadRegisteredDevices()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteDialog(device: BeaconDevice) {
        AlertDialog.Builder(this)
            .setTitle("장치 삭제")
            .setMessage("[${device.name}]을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                prefs.edit { remove(device.address) }
                loadRegisteredDevices()
                Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateParkingInfoUI()
    }

    private fun updateParkingInfoUI() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val location = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_READY)
        val timeMillis = prefs.getLong(Constants.KEY_LAST_PARKING_TIME, 0L)
        val lat = prefs.getFloat(Constants.KEY_LAST_LATITUDE, 0f)
        val lng = prefs.getFloat(Constants.KEY_LAST_LONGITUDE, 0f)

        binding.tvLastParkingLocation.text = location

        if (location == Constants.MSG_OUTDOOR && lat != 0f && lng != 0f) {
            binding.btnOpenMap.visibility = android.view.View.VISIBLE
        } else {
            binding.btnOpenMap.visibility = android.view.View.GONE
        }

        if (timeMillis != 0L) {
            val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            binding.tvLastParkingTime.text = getString(R.string.last_parking_time_format, sdf.format(java.util.Date(timeMillis)))
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog() {
        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "페어링된 블루투스 장치가 없습니다.\n설정에서 차를 먼저 연결해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        val deviceList = pairedDevices.toList()
        val deviceNames = deviceList.map { "${it.name}\n(${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("내 차의 블루투스 선택")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = deviceList[which]
                saveMyCarDevice(selectedDevice.name ?: "Unknown Car", selectedDevice.address)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveMyCarDevice(name: String, address: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            putString(Constants.KEY_MY_CAR_NAME, name)
            putString(Constants.KEY_MY_CAR_ADDRESS, address)
        }
        updateMyCarUI(name)
        Toast.makeText(this, "내 차[$name]가 등록되었습니다.", Toast.LENGTH_SHORT).show()
        startBackgroundService()
    }

    private fun startBackgroundService() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val myCarAddress = prefs.getString(Constants.KEY_MY_CAR_ADDRESS, null)

        if (myCarAddress != null) {
            val intent = Intent(this, ParkingService::class.java)
            startForegroundService(intent)
        }
    }

    private fun loadMyCarData() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val carName = prefs.getString(Constants.KEY_MY_CAR_NAME, null)
        if (carName != null) {
            updateMyCarUI(carName)
        }
    }

    private fun updateMyCarUI(carName: String) {
        binding.tvMyCarStatus.text = getString(R.string.my_car_status_format, carName)
        binding.tvMyCarStatus.setTextColor("#00FFFF".toColorInt())
    }

    private fun openOutdoorMap() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val lat = prefs.getFloat(Constants.KEY_LAST_LATITUDE, 0f)
        val lng = prefs.getFloat(Constants.KEY_LAST_LONGITUDE, 0f)

        if (lat != 0f && lng != 0f) {
            val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(내 차 위치)")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "설치된 지도 앱이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
