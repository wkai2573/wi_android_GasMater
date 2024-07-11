package com.wavein.gasmeter.ui.meterwork.row

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.databinding.FragmentMeterRowBinding
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeterRowFragment : Fragment() {


	private var _binding:FragmentMeterRowBinding? = null
	private val binding get() = _binding!!
	private val meterVM by activityViewModels<MeterViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()

	private var lastMeterId = ""

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterRowBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)


		binding.queueCombo.layout.hint = "抄表順路"


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterGroupStateFlow.collectLatest {
					initComboList()
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterRowFlow.asStateFlow().collectLatest {
					setCombo(it)
					binding.fieldGroup.setValue("${it?.group}")

					if (lastMeterId != it?.meterId) {
						binding.pager.setCurrentItem(0, false)
					}
					lastMeterId = it?.meterId ?: ""
				}
			}
		}


		val meterPageAdapter = MeterInfoSetPageAdapter(this)
		binding.pager.adapter = meterPageAdapter
		binding.pager.isUserInputEnabled = false


		TabLayoutMediator(binding.infoSetTabLayout, binding.pager) { tab, position ->
			when (position) {
				0 -> tab.text = "基本資料"
				1 -> tab.text = "表讀取 / 設定"
				else -> tab.text = "UNKNOWN: $position"
			}
		}.attach()
	}

	private fun initComboList() {
		val meterGroup = meterVM.selectedMeterGroupStateFlow.value
		val meterRowComboAdapter = MeterRowComboAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, meterGroup?.meterRows ?: listOf())
		binding.queueCombo.listTv.setAdapter(meterRowComboAdapter)
		binding.queueCombo.listTv.setOnItemClickListener { parent, view, position, id ->
			val meterRow = meterRowComboAdapter.getItem(position)
			meterVM.selectedMeterRowFlow.value = meterRow
		}
	}

	private fun setCombo(meterRow:MeterRow?) {
		meterRow?.let {
			val text = it.toString()
			binding.queueCombo.listTv.setText(text, false)
			binding.queueCombo.subTitleTv.text = it.readTip
			binding.queueCombo.subTitleTv.setTextColor(it.readTipColor)
		}
	}
}



class MeterRowComboAdapter(context:Context, resource:Int, meterRows:List<MeterRow>) :
	ArrayAdapter<MeterRow>(context, resource, meterRows) {


	override fun getView(position:Int, convertView:View?, parent:ViewGroup):View {
		val view:TextView = super.getView(position, convertView, parent) as TextView
		val meterRow:MeterRow = getItem(position)!!
		view.text = meterRow.queueAndIdWithTip
		return view
	}
}



class MeterInfoSetPageAdapter(fragment:Fragment) : FragmentStateAdapter(fragment) {
	override fun getItemCount():Int = 2
	override fun createFragment(position:Int):Fragment {
		val fragment = when (position) {
			0 -> MeterInfoFragment()
			1 -> MeterAdvFragment()
			else -> MeterInfoFragment()
		}
		return fragment
	}
}
