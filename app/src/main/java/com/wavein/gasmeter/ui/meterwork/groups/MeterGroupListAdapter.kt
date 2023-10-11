package com.wavein.gasmeter.ui.meterwork.groups

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wavein.gasmeter.data.model.MeterGroup
import com.wavein.gasmeter.databinding.ItemMeterGroupBinding

class MeterGroupListAdapter(private val onClick:(MeterGroup) -> Unit) :
	ListAdapter<MeterGroup, MeterGroupListAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent:ViewGroup, viewType:Int):ViewHolder {
		val binding = ItemMeterGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	@SuppressLint("MissingPermission")
	override fun onBindViewHolder(holder:ViewHolder, position:Int) {
		val meterGroup = getItem(position)
		holder.bind(meterGroup)
	}

	inner class ViewHolder(private val binding:ItemMeterGroupBinding) : RecyclerView.ViewHolder(binding.root) {
		init {
			itemView.setOnClickListener {
				onClick(getItem(adapterPosition))
			}
		}

		@SuppressLint("MissingPermission")
		fun bind(item:MeterGroup) {
			binding.fieldGroup.setValue(item.group)
			binding.fieldReadCount.setValue(item.readTip, item.readTipColor)
		}
	}

	class DiffCallback : DiffUtil.ItemCallback<MeterGroup>() {
		override fun areItemsTheSame(oldItem:MeterGroup, newItem:MeterGroup):Boolean {
			return oldItem.group == newItem.group
		}

		override fun areContentsTheSame(oldItem:MeterGroup, newItem:MeterGroup):Boolean {
			return oldItem == newItem
		}
	}
}

