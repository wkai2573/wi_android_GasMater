package com.wavein.gasmeter.ui.meterwork.list

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.databinding.ItemMeterRowBinding

enum class MeterRowRender { Simple, Detail }

class MeterListAdapter(
	private val render:MeterRowRender = MeterRowRender.Simple,
	private val onClick:(MeterRow) -> Unit
) :
	ListAdapter<MeterRow, MeterListAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent:ViewGroup, viewType:Int):ViewHolder {
		val binding = ItemMeterRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	@SuppressLint("MissingPermission")
	override fun onBindViewHolder(holder:ViewHolder, position:Int) {
		val meterRow = getItem(position)
		holder.bind(meterRow)
	}

	inner class ViewHolder(private val binding:ItemMeterRowBinding) : RecyclerView.ViewHolder(binding.root) {
		init {
			binding.groupMeterIdLayout.visibility = if (render == MeterRowRender.Simple) View.GONE else View.VISIBLE
			itemView.setOnClickListener {
				onClick(getItem(adapterPosition))
			}
		}

		@SuppressLint("MissingPermission")
		fun bind(meterRow:MeterRow) {
			binding.fieldGroup.setValue(meterRow.group)
			binding.fieldMeterId.setValue(meterRow.meterId)
			binding.fieldQueue.setValue("${meterRow.queue}")
			binding.fieldCustName.setValue(meterRow.custName)
			binding.fieldMeterDegree.setValue("${meterRow.meterDegree ?: "未抄表"}", if (meterRow.meterDegree == null) Color.RED else Color.BLACK)
		}
	}

	class DiffCallback : DiffUtil.ItemCallback<MeterRow>() {
		override fun areItemsTheSame(oldItem:MeterRow, newItem:MeterRow):Boolean {
			return oldItem.meterId == newItem.meterId
		}

		override fun areContentsTheSame(oldItem:MeterRow, newItem:MeterRow):Boolean {
			return oldItem == newItem
		}
	}
}

