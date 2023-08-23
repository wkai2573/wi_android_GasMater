package com.wavein.gasmater.ui.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wavein.gasmater.databinding.ItemDeviceBinding

class LeDeviceListAdapter(
	private val onClick:(BluetoothDevice) -> Unit,
) : ListAdapter<BluetoothDevice, LeDeviceListAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent:ViewGroup, viewType:Int):LeDeviceListAdapter.ViewHolder {
		val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	@SuppressLint("MissingPermission")
	override fun onBindViewHolder(holder:LeDeviceListAdapter.ViewHolder, position:Int) {
		val btDevice = getItem(position)
		holder.bind(btDevice)
	}

	inner class ViewHolder(private val binding:ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
		init {
			itemView.setOnClickListener {
				onClick(getItem(adapterPosition))
			}
		}

		@SuppressLint("MissingPermission")
		fun bind(item:BluetoothDevice) {
			binding.btInfoTv.text = "${item.name}🏀${item.address}🏀${item.uuids}"
		}
	}

	class DiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
		override fun areItemsTheSame(oldItem:BluetoothDevice, newItem:BluetoothDevice):Boolean {
			return oldItem.address == newItem.address
		}

		override fun areContentsTheSame(oldItem:BluetoothDevice, newItem:BluetoothDevice):Boolean {
			return oldItem == newItem
		}
	}
}

