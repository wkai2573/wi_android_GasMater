package com.wavein.gasmeter.ui.meterwork.list

import android.R
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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.data.model.MeterGroup
import com.wavein.gasmeter.data.model.toMeterGroups
import com.wavein.gasmeter.databinding.FragmentMeterListBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.meterwork.Filter
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MeterListFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterListBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BluetoothViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()

	// 實例
	private val meterBaseFragment:MeterBaseFragment get() = parentFragment as MeterBaseFragment
	private lateinit var meterListAdapter:MeterListAdapter

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

		// 未抄表 / 全部顯示
		binding.undoneBtn.setOnClickListener {
			binding.undoneBtn.isChecked = true
			meterVM.metersFilterFlow.value = Filter.Undone
		}
		binding.allBtn.setOnClickListener {
			binding.allBtn.isChecked = true
			meterVM.metersFilterFlow.value = Filter.All
		}

		// combo
		binding.groupsCombo.layout.hint = "群組"

		// rv
		meterListAdapter = MeterListAdapter {
			meterVM.selectedMeterRowFlow.value = it
			(parentFragment as MeterBaseFragment).changeTab(2)
		}
		binding.meterRowsRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)) //分隔線
			itemAnimator = DefaultItemAnimator()
			adapter = meterListAdapter
		}

		// 訂閱選擇的group
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterGroupStateFlow.collectLatest {
					initComboList()
					setCombo(it)
					submitList()
					ifAllDoneShowAll()
				}
			}
		}

		// 訂閱filter
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.metersFilterFlow.asStateFlow().collectLatest {
					when (it) {
						Filter.All -> binding.allBtn.isChecked = true
						Filter.Undone -> binding.undoneBtn.isChecked = true
					}
					submitList()
				}
			}
		}

		// 群組抄表按鈕
		binding.groupReadBtn.setOnClickListener {
			val meterGroup = meterVM.selectedMeterGroupStateFlow.value ?: return@setOnClickListener
			if (meterGroup.allRead) {
				lifecycleScope.launch {
					SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("該群組已抄表完畢", SharedEvent.Color.Info))
				}
				return@setOnClickListener
			}
			val notReadMeterIds = meterGroup.meterRows.filter { !it.degreeRead }.map { it.meterId }
			if (notReadMeterIds.size > 45) {
				lifecycleScope.launch {
					SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("一次最多對45台進行抄表", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
				}
				return@setOnClickListener
			}
			val estimatedTime = (notReadMeterIds.size - 1) * 3 + when {
				notReadMeterIds.size <= 15 -> 37
				else -> 50
			}

			MaterialAlertDialogBuilder(requireContext()).apply {
				setTitle("群組抄表")
				setMessage("準備進行群組抄表\n共${notReadMeterIds.size}台，耗時約${estimatedTime}秒")
				setNeutralButton("取消") { dialog, which -> dialog.dismiss() }
				setPositiveButton("確定") { dialog, which ->
					dialog.dismiss()
					readGroupMeters(notReadMeterIds)
				}
				show()
			}
		}

		ifAllDoneShowAll()
	}

	// 群組抄表
	private fun readGroupMeters(meterIds:List<String>) {
		meterBaseFragment.checkBluetoothOn { blVM.sendR80Telegram(meterIds) }
	}

	// 如果全部抄表完成, 顯示全部
	private fun ifAllDoneShowAll() {
		val undoneSize = meterVM.selectedMeterGroupStateFlow.value?.meterRows?.filter { !it.degreeRead }?.size ?: 0
		val allDone = undoneSize == 0
		if (allDone) meterVM.metersFilterFlow.value = Filter.All
	}

	private fun initComboList() {
		val meterGroups = meterVM.meterRowsStateFlow.value.toMeterGroups()
		val meterGroupComboAdapter = MeterGroupComboAdapter(requireContext(), R.layout.simple_dropdown_item_1line, meterGroups)
		binding.groupsCombo.listTv.setAdapter(meterGroupComboAdapter)
		binding.groupsCombo.listTv.setOnItemClickListener { parent, view, position, id ->
			val meterGroup = meterGroupComboAdapter.getItem(position)
			meterVM.setSelectedMeterGroup(meterGroup)
		}
	}

	private fun setCombo(meterGroup:MeterGroup?) {
		meterGroup?.let {
			binding.groupsCombo.listTv.setText(it.group, false)
			binding.groupsCombo.subTitleTv.text = it.readTip
			binding.groupsCombo.subTitleTv.setTextColor(it.readTipColor)
		}
	}

	private fun submitList() {
		val meterGroup = meterVM.selectedMeterGroupStateFlow.value
		if (meterGroup == null) {
			meterListAdapter.submitList(listOf())
			return
		}
		val meterRows = when (meterVM.metersFilterFlow.value) {
			Filter.All -> meterGroup.meterRows
			Filter.Undone -> meterGroup.meterRows.filter { !it.degreeRead }
		}.sortedBy { it.queue }
		meterListAdapter.submitList(meterRows)
		// 全完成提示
		binding.allDoneCongratsTip.visibility = if (meterRows.isEmpty()) View.VISIBLE else View.GONE
	}

}


// 群組ComboAdapter
class MeterGroupComboAdapter(context:Context, resource:Int, groups:List<MeterGroup>) :
	ArrayAdapter<MeterGroup>(context, resource, groups) {

	// 提示未抄表數量 & 未完成顏色
	override fun getView(position:Int, convertView:View?, parent:ViewGroup):View {
		val view:TextView = super.getView(position, convertView, parent) as TextView
		val group:MeterGroup = getItem(position)!!
		view.text = group.groupWithTip
		return view
	}
}
