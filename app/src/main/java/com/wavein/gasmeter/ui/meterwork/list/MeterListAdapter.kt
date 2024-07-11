package com.wavein.gasmeter.ui.meterwork.list

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.data.model.Selectable
import com.wavein.gasmeter.databinding.ItemMeterRowBinding

enum class MeterRowRender { Simple, Detail }

class MeterListAdapter(
	private val render:MeterRowRender = MeterRowRender.Simple,
	private val onClick:(MeterRow) -> Unit
) :
	ListAdapter<Selectable<MeterRow>, MeterListAdapter.ViewHolder>(DiffCallback()) {

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
				onClick(getItem(adapterPosition).data)
			}
		}

		@SuppressLint("MissingPermission")
		fun bind(sMeterRow:Selectable<MeterRow>) {
			if (sMeterRow.selected) {
				binding.layout.setBackgroundColor(Color.parseColor("#E5E1E5"))
			} else {

				binding.layout.apply {
					background = with(TypedValue()) {
						context.theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackground, this, true)
						ContextCompat.getDrawable(context, resourceId)
					}
				}
			}
			val meterRow = sMeterRow.data
			binding.fieldGroup.setValue(meterRow.group)
			binding.fieldMeterId.setValue(meterRow.meterId)
			binding.fieldQueue.setValue(meterRow.queue)
			binding.fieldCustId.setValue(meterRow.custId)
			val degreeColor = if (meterRow.degreeRead) Color.BLACK else Color.RED
			binding.fieldMeterDegree.setValue("${meterRow.meterDegree ?: "未抄表"}", degreeColor)
			binding.errTv.setText(meterRow.error)
		}
	}

	class DiffCallback : DiffUtil.ItemCallback<Selectable<MeterRow>>() {
		override fun areItemsTheSame(oldItem:Selectable<MeterRow>, newItem:Selectable<MeterRow>):Boolean {
			return oldItem.data.meterId == newItem.data.meterId && oldItem.selected == newItem.selected
		}

		override fun areContentsTheSame(oldItem:Selectable<MeterRow>, newItem:Selectable<MeterRow>):Boolean {
			return oldItem.data == newItem.data && oldItem.selected == newItem.selected
		}
	}
}

