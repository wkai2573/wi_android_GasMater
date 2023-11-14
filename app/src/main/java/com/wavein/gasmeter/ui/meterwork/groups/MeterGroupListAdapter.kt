package com.wavein.gasmeter.ui.meterwork.groups

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wavein.gasmeter.data.model.MeterGroup
import com.wavein.gasmeter.data.model.Selectable
import com.wavein.gasmeter.databinding.ItemMeterGroupBinding


class MeterGroupListAdapter(private val onClick:(MeterGroup) -> Unit) :
	ListAdapter<Selectable<MeterGroup>, MeterGroupListAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent:ViewGroup, viewType:Int):ViewHolder {
		val binding = ItemMeterGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	@SuppressLint("MissingPermission")
	override fun onBindViewHolder(holder:ViewHolder, position:Int) {
		val sMeterGroup = getItem(position)
		holder.bind(sMeterGroup)
	}

	inner class ViewHolder(private val binding:ItemMeterGroupBinding) : RecyclerView.ViewHolder(binding.root) {
		init {
			itemView.setOnClickListener {
				onClick(getItem(adapterPosition).data)
			}
		}

		@SuppressLint("MissingPermission")
		fun bind(sMeterGroup:Selectable<MeterGroup>) {
			if (sMeterGroup.selected) {
				binding.layout.setBackgroundColor(Color.parseColor("#E5E1E5"))
			} else {
				// 設定成?android:attr/selectableItemBackground, 參考 https://stackoverflow.com/a/68083456/12729834
				binding.layout.apply {
					background = with(TypedValue()) {
						context.theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackground, this, true)
						ContextCompat.getDrawable(context, resourceId)
					}
				}
			}
			val meterGroup = sMeterGroup.data
			binding.fieldGroup.setValue(meterGroup.group)
			binding.fieldReadCount.setValue(meterGroup.readTip, meterGroup.readTipColor)
			binding.errTv.setText(meterGroup.error)
		}
	}

	class DiffCallback : DiffUtil.ItemCallback<Selectable<MeterGroup>>() {
		override fun areItemsTheSame(oldItem:Selectable<MeterGroup>, newItem:Selectable<MeterGroup>):Boolean {
			return oldItem.data.group == newItem.data.group && oldItem.selected == newItem.selected
		}

		override fun areContentsTheSame(oldItem:Selectable<MeterGroup>, newItem:Selectable<MeterGroup>):Boolean {
			return oldItem.data == newItem.data && oldItem.selected == newItem.selected
		}
	}
}

