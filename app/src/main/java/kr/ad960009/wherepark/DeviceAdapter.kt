package kr.ad960009.wherepark

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ad960009.wherepark.databinding.ItemDeviceBinding

// 리스트의 각 아이템 데이터를 담는 클래스
data class BeaconDevice(val name: String, val address: String, var rssi: Int)

class DeviceAdapter(private val onClick: (BeaconDevice) -> Unit) :
    RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<BeaconDevice>()

    // 새로운 장치가 발견되면 리스트에 추가 (중복 체크 포함)
    fun addDevice(device: BeaconDevice) {
        val index = devices.indexOfFirst { it.address == device.address }
        if (index == -1) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        } else {
            // 이미 있는 장치라면 신호 세기만 업데이트
            devices[index].rssi = device.rssi
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = devices.size

    class ViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BeaconDevice) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceAddress.text = device.address
            binding.tvRssi.text = binding.root.context.getString(R.string.rssi_format, device.rssi)
        }
    }
}