package com.wavein.gasmeter.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wavein.gasmeter.databinding.ItemDeviceBinding

class DeviceListAdapter(private val onClick:(BluetoothDevice) -> Unit) :
	ListAdapter<BluetoothDevice, DeviceListAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent:ViewGroup, viewType:Int):ViewHolder {
		val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	@SuppressLint("MissingPermission")
	override fun onBindViewHolder(holder:ViewHolder, position:Int) {
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
			val showText = "${item.name}\n${item.address}"
			binding.btInfoTv.text = showText
//			binding.btInfoTv.text = "${item.name}🏀${item.address}🏀${item.uuids}"
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

