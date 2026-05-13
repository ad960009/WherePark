package kr.ad960009.wherepark

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kr.ad960009.wherepark.databinding.ItemDeviceBinding

class RegisteredDeviceAdapter(
    private val onEdit: (BeaconDevice) -> Unit,
    private val onDelete: (BeaconDevice) -> Unit
) : RecyclerView.Adapter<RegisteredDeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<BeaconDevice>()

    fun setItems(newDevices: List<BeaconDevice>) {
        val diffCallback = DeviceDiffCallback(devices, newDevices)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        devices.clear()
        devices.addAll(newDevices)
        diffResult.dispatchUpdatesTo(this)
    }

    class DeviceDiffCallback(
        private val oldList: List<BeaconDevice>,
        private val newList: List<BeaconDevice>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].address == newList[newItemPosition].address
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

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

            if (device.rssi < 0) {
                binding.tvRssi.text = binding.root.context.getString(R.string.rssi_format, device.rssi)
                binding.tvRssi.setTextColor(Color.GREEN)
            } else {
                binding.tvRssi.text = binding.root.context.getString(R.string.rssi_recorded)
                binding.tvRssi.setTextColor(Color.GRAY)
            }
        }
    }
}
