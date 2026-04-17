package kr.ad960009.wherepark

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kr.ad960009.wherepark.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false

    // 어댑터 분리: 상단(스캔용)과 하단(등록 관리용)
    private lateinit var scanAdapter: DeviceAdapter
    private lateinit var registeredAdapter: RegisteredDeviceAdapter

    // 1. 권한 요청 핸들러 (안드로이드 최신 방식)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // 권한이 승인되었을 때, 굳이 바로 스캔을 시작하지 않고
            // 사용자가 다시 버튼을 누르도록 안내하는 것이 논리적으로 꼬이지 않습니다.
            Toast.makeText(this, "권한이 승인되었습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "필수 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 블루투스 어댑터 초기화
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // 뷰 셋업 및 저장된 데이터 불러오기
        setupRecyclerViews()
        loadRegisteredDevices()
        loadMyCarData()

        // 스캔 버튼 클릭 리스너 (XML의 버튼 ID에 맞춰 btnScan 또는 btnStartScan 사용)
        binding.btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        binding.btnRegisterCar.setOnClickListener {
            checkPermissionsAndSelectCar()
        }

        startBackgroundService()
    }

    // =========================================================================
    // 2. 권한 체크 및 블루투스 스캔 로직
    // =========================================================================

    private fun checkPermissionsAndScan() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val isAlreadyGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (isAlreadyGranted) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun checkPermissionsAndSelectCar() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val isAlreadyGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (isAlreadyGranted) {
            // 권한이 이미 있으면 바로 기기 목록 띄우기
            showPairedDevicesDialog()
        } else {
            // 권한이 없으면 요청 (기존에 만들어둔 Launcher 활용)
            // 요청 결과에서 자동으로 스캔이 시작되지 않도록 플래그를 두거나,
            // 그냥 다시 버튼을 누르게 유도하는 것이 깔끔합니다.
            requestPermissionLauncher.launch(requiredPermissions)
            Toast.makeText(this, "권한 허용 후 다시 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show()
        }
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

            // 10초 후 자동 중지 (배터리 보호)
            binding.root.postDelayed({
                stopBleScan()
            }, Constants.SCAN_DURATION_MS)
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
            // 기본값 설정
            var deviceName = "Unknown Device"
            val deviceAddress = result.device.address
            val rssi = result.rssi

            try {
                // 안드로이드 12 이상에서 권한이 없으면 여기서 SecurityException이 발생합니다.
                deviceName = result.device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                // 예외 발생 시 토스트를 띄우고 기본값을 유지합니다.
                // runOnUiThread를 써야 하는 이유는 스캔 콜백이 백그라운드 스레드에서 돌기 때문입니다.
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "기기 이름을 가져올 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            runOnUiThread {
                // 상단 스캔 리스트 업데이트
                scanAdapter.addDevice(BeaconDevice(deviceName, deviceAddress, rssi))

                // 하단 등록 리스트 RSSI 업데이트
                registeredAdapter.updateRssi(deviceAddress, rssi)
            }
        }
    }

    // =========================================================================
    // 3. UI 및 데이터 관리 (RecyclerView & Dialog)
    // =========================================================================

    private fun setupRecyclerViews() {
        // [상단] 스캔 리스트 설정
        scanAdapter = DeviceAdapter { device ->
            showRegisterDialog(device)
        }
        binding.rvDevices.apply {
            adapter = scanAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // [하단] 등록 리스트 설정
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
        // 💡 설정에 사용된 키워드들을 리스트로 정의
        val excludeKeys = listOf(
            Constants.KEY_MY_CAR_NAME,
            Constants.KEY_MY_CAR_ADDRESS,
            Constants.KEY_LAST_PARKING_LOCATION,
            Constants.KEY_LAST_PARKING_TIME
        )

        for ((key, value) in allEntries) {
            // 예약된 키워드가 아닐 때만 기기 목록으로 판단하고 추가
            if (key !in excludeKeys) {
                registeredList.add(BeaconDevice(value.toString(), key, 0))
            }
        }

        registeredAdapter.setItems(registeredList)
    }

    private fun saveDeviceData(address: String, alias: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(address, alias).apply()
    }

    // 신규 등록 다이얼로그
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
                    loadRegisteredDevices() // 등록 즉시 하단 리스트 새로고침
                    Toast.makeText(this, "[$alias] 등록되었습니다!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 이름 수정 다이얼로그
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

    // 삭제 확인 다이얼로그
    private fun showDeleteDialog(device: BeaconDevice) {
        AlertDialog.Builder(this)
            .setTitle("장치 삭제")
            .setMessage("[${device.name}]을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                prefs.edit().remove(device.address).apply()
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

        binding.tvLastParkingLocation.text = location

        if (timeMillis != 0L) {
            val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            binding.tvLastParkingTime.text = "확인 시간: ${sdf.format(java.util.Date(timeMillis))}"
        }
    }

    private fun displayLastParkingLocation() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val lastLoc = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, "정보 없음")
        val lastTime = prefs.getLong(Constants.KEY_LAST_PARKING_TIME, 0)

        if (lastTime != 0L) {
            //val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            //val timeStr = sdf.format(java.util.Date(lastTime))

            // 상단에 위치 표시용 텍스트뷰가 있다면 거기에 뿌려줍니다.
            // binding.tvLastStatus.text = "최종 주차 위치: $lastLoc ($timeStr)"
            Toast.makeText(this, "마지막 확인 위치: $lastLoc", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // 4. 내 차량(페어링된 기기) 등록 및 관리
    // =========================================================================

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog() {
        // 스마트폰에 이미 페어링된 기기 목록 가져오기
        val pairedDevices = bluetoothAdapter?.bondedDevices

        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "페어링된 블루투스 장치가 없습니다.\n설정에서 차를 먼저 연결해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        // 리스트와 화면에 띄울 이름 배열 만들기
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
        prefs.edit().apply {
            putString(Constants.KEY_MY_CAR_NAME, name)
            putString(Constants.KEY_MY_CAR_ADDRESS, address)
            apply()
        }
        updateMyCarUI(name)
        Toast.makeText(this, "내 차[$name]가 등록되었습니다.", Toast.LENGTH_SHORT).show()
        startBackgroundService()
    }

    private fun startBackgroundService() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val myCarAddress = prefs.getString(Constants.KEY_MY_CAR_ADDRESS, null)

        // 내 차가 등록되어 있을 때만 서비스 가동
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
        // 상단 UI 업데이트 (텍스트와 색상 변경)
        binding.tvMyCarStatus.text = "현재 등록된 차량: $carName"
        binding.tvMyCarStatus.setTextColor(android.graphics.Color.parseColor("#00FFFF")) // Cyan 색상으로 눈에 띄게
    }
}