package kr.ad960009.wherepark

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kr.ad960009.wherepark.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false

    private lateinit var deviceAdapter: DeviceAdapter

    // 1. 권한 요청 핸들러 (안드로이드 최신 방식)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBleScan() // 권한 허용 시 스캔 시작
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

        // 스캔 버튼 클릭 리스너
        binding.btnStartScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        deviceAdapter = DeviceAdapter { device ->
            // 장치를 클릭했을 때 실행될 코드 (나중에 다이얼로그 띄울 곳)
            Toast.makeText(this, "${device.name}을 선택했습니다.", Toast.LENGTH_SHORT).show()
            showRegisterDialog(device)
        }

        binding.rvDevices.apply {
            adapter = deviceAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
        }
    }

    // 2. 권한 체크 로직
    private fun checkPermissionsAndScan() {
        // 필요한 권한들 리스트 (안드로이드 12 이상 기준)
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // 모든 권한이 이미 허용되었는지 확인
        val isAlreadyGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (isAlreadyGranted) {
            // 이미 권한이 있다면 바로 스캔 시작
            startBleScan()
        } else {
            // 권한이 없다면 사용자에게 요청 팝업 띄우기
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    // 3. 실제 BLE 스캔 로직
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "블루투스를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isScanning) {
            isScanning = true
            binding.btnStartScan.text = "스캔 중지..."

            // 스캔 결과 콜백
            scanner.startScan(scanCallback)

            // 배터리 보호를 위해 10초 후 자동 중지
            binding.root.postDelayed({
                stopBleScan()
            }, 10000)
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
            binding.btnStartScan.text = "주변 비컨 스캔 시작"
        }
    }

    // 4. 스캔 결과 처리
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: "이름 없음"
            val deviceAddress = result.device.address
            var rssi = result.rssi

            // 로그로 먼저 확인해보기
            println("찾은 장치: $deviceName [$deviceAddress]")

            // TODO: 여기서 리스트(RecyclerView)에 데이터를 추가하는 코드가 들어갑니다.
            runOnUiThread {
                deviceAdapter.addDevice(BeaconDevice(deviceName, deviceAddress, rssi))
            }
        }
    }

    private fun showRegisterDialog(device: BeaconDevice) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("주차 장치 등록")
        builder.setMessage("${device.address} 장치에 이름을 붙여주세요.")

        // 입력창 추가
        val input = android.widget.EditText(this)
        input.hint = "예: 내 차 옆 기둥 / 지하 2층"
        builder.setView(input)

        builder.setPositiveButton("등록") { _, _ ->
            val alias = input.text.toString()
            if (alias.isNotEmpty()) {
                // SharedPreferences에 저장 (Key: 맥주소, Value: 별명)
                saveDeviceData(device.address, alias)
                Toast.makeText(this, "[$alias] 등록되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }

    private fun saveDeviceData(address: String, alias: String) {
        val prefs = getSharedPreferences("WhereParkPrefs", MODE_PRIVATE)
        prefs.edit().putString(address, alias).apply()
    }
}