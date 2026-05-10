package kr.ad960009.wherepark

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ParkingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val myCarAddress = prefs.getString(Constants.KEY_MY_CAR_ADDRESS, null)

        // 내 차 주소와 일치하는지 확인
        if (device?.address == myCarAddress && myCarAddress != null) {
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // 연결 또는 해제 시 서비스를 실행하여 포그라운드로 전환
                    val serviceIntent = Intent(context, ParkingService::class.java).apply {
                        this.action = action
                        putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}
