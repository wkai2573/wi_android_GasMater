package com.wavein.gasmater.ui.meterwork.list

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.FragmentMeterListBinding

@Suppress("UNNECESSARY_SAFE_CALL")
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


		val items = arrayOf("Item 1", "Item 2", "Item 3", "Item 4")
//		val adapter = ArrayAdapter(requireContext(), R.layout.combo_groups, items)
//		binding.groupsListTv.setAdapter(adapter)

		val customAdapter = CustomArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
		binding.groupsListTv.setAdapter(customAdapter)


		//...
	}

}




// todo 待修改
class CustomArrayAdapter(context:Context, resource: Int, objects: Array<String>) :
	ArrayAdapter<String>(context, resource, objects) {

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val view:TextView = super.getView(position, convertView, parent) as TextView
		val text = getItem(position)

		// 这里假设数字始终在文本的最后，所以找到最后一个空格并将其后面的文本设置为红色
		val lastSpaceIndex = text?.lastIndexOf(" ")
		if (lastSpaceIndex != null && lastSpaceIndex >= 0) {
			val spannable = SpannableString(text)
			val redColor = ForegroundColorSpan(Color.RED)
			spannable.setSpan(redColor, lastSpaceIndex + 1, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			view.text = spannable
		}

		return view
	}
}