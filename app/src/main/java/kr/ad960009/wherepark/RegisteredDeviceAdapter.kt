package kr.ad960009.wherepark

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ad960009.wherepark.databinding.ItemDeviceBinding

class RegisteredDeviceAdapter(
    private val onEdit: (BeaconDevice) -> Unit,
    private val onDelete: (BeaconDevice) -> Unit
) : RecyclerView.Adapter<RegisteredDeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<BeaconDevice>()

    fun setItems(newDevices: List<BeaconDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    // 💡 추가된 기능: 스캔 중에 신호가 잡히면 RSSI 업데이트
    fun updateRssi(address: String, rssi: Int) {
        val index = devices.indexOfFirst { it.address == address }
        if (index != -1) {
            devices[index].rssi = rssi
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

        holder.itemView.setOnClickListener { onEdit(device) }
        holder.itemView.setOnLongClickListener {
            onDelete(device)
            true
        }
    }

    override fun getItemCount() = devices.size

    class ViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BeaconDevice) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceAddress.text = device.address

            // 💡 RSSI 값에 따라 UI 변경 (RSSI는 보통 0 미만의 음수입니다)
            if (device.rssi < 0) {
                binding.tvRssi.text = binding.root.context.getString(R.string.rssi_format, device.rssi)
                binding.tvRssi.setTextColor(Color.GREEN) // 근처에 있으면 초록색으로 표시
            } else {
                binding.tvRssi.text = binding.root.context.getString(R.string.rssi_recorded)
                binding.tvRssi.setTextColor(Color.GRAY)  // 신호가 없으면 회색으로 표시
            }
        }
    }
}