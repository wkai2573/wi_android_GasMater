package com.wavein.gasmeter.ui.meterwork.list

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.wavein.gasmeter.databinding.FragmentMeterListBinding


class MeterListFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterListBinding? = null
	private val binding get() = _binding!!
	private val meterListVM by activityViewModels<MeterListViewModel>()

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterListBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.doneAllToggleBtn.apply {
			isSingleSelection = true
			addOnButtonCheckedListener { group, checkedId, isChecked ->
				if (group.checkedButtonId == -1) group.check(checkedId)
			}
		}
		binding.doneBtn.isChecked = true


		val groups = listOf(
			MeterGroup(
				"Group1", listOf(
					Meter("0001", null),
					Meter("0002", null),
					Meter("0003", 3.5f),
					Meter("0004", null),
					Meter("0005", 128.456f),
				)
			),
			MeterGroup(
				"Group2", listOf(
					Meter("0001", null),
					Meter("0002", null),
				)
			),
			MeterGroup(
				"Group3", listOf(
					Meter("0001", 4.5f),
					Meter("0002", 77.7f),
				)
			),
		)
		val meterComboAdapter = MeterComboAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, groups)
		binding.groupsCombo.listTv.setAdapter(meterComboAdapter)
		binding.groupsCombo.listTv.setOnItemClickListener { parent, view, position, id ->
			val group = meterComboAdapter.getItem(position)
			group?.let {
				binding.groupsCombo.subTitleTv.text = it.tip
				binding.groupsCombo.subTitleTv.setTextColor(it.tipColor)
			}
		}
	}

}


// 群組ComboAdapter
class MeterComboAdapter(context:Context, resource:Int, groups:List<MeterGroup>) :
	ArrayAdapter<MeterGroup>(context, resource, groups) {

	// 提示未抄表數量 & 未完成顏色
	override fun getView(position:Int, convertView:View?, parent:ViewGroup):View {
		val view:TextView = super.getView(position, convertView, parent) as TextView
		val group:MeterGroup = getItem(position)!!
		view.text = group.spannable
		return view
	}
}

data class CsvRawRow(private val data:Map<String, String>)


data class Meter(
	private val id:String,
	private val degree:Float?,
) {
	val read:Boolean get() = degree != null
}


data class MeterGroup(
	val title:String,
	private val meterList:List<Meter>,
) {
	private val totalCount:Int get() = meterList.count()
	private val readCount:Int get() = meterList.count { it.read }
	val tip:String get() = "(${readCount}/${totalCount})"
	val tipColor:Int get() = if (readCount == totalCount) Color.parseColor("#ff4a973b") else Color.RED

	val spannable:SpannableString
		get() {
			val allText = "$title $tip"
			val spannable = SpannableString(allText)
			val color = ForegroundColorSpan(tipColor)
			spannable.setSpan(color, title.length + 1, allText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			return spannable
		}

	override fun toString():String {
		return this.title
	}
}
