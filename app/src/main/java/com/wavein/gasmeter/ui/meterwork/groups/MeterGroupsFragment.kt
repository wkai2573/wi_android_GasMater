package com.wavein.gasmeter.ui.meterwork.groups

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.wavein.gasmeter.data.model.MeterGroup
import com.wavein.gasmeter.data.model.Selectable
import com.wavein.gasmeter.data.model.toMeterGroups
import com.wavein.gasmeter.databinding.FragmentMeterGroupsBinding
import com.wavein.gasmeter.ui.meterwork.Filter
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeterGroupsFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterGroupsBinding? = null
	private val binding get() = _binding!!
	private val meterVM by activityViewModels<MeterViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()

	// 實例
	private lateinit var meterGroupListAdapter:MeterGroupListAdapter

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterGroupsBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 未抄表完成 / 全部顯示
		binding.undoneBtn.setOnClickListener {
			binding.undoneBtn.isChecked = true
			meterVM.groupsFilterFlow.value = Filter.Undone
		}
		binding.allBtn.setOnClickListener {
			binding.allBtn.isChecked = true
			meterVM.groupsFilterFlow.value = Filter.All
		}

		// rv
		meterGroupListAdapter = MeterGroupListAdapter {
			meterVM.setSelectedMeterGroup(it)
			(parentFragment as MeterBaseFragment).changeTab(1)
		}
		binding.meterGroupsRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)) //分隔線
			itemAnimator = DefaultItemAnimator()
			adapter = meterGroupListAdapter
		}

		// 訂閱Csv檔案
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				csvVM.selectedFileStateFlow.asStateFlow().collectLatest { fileState ->
					val text = "檔名:${fileState.name}"
					binding.selectedCsv.infoTv.text = text
				}
			}
		}

		// 訂閱rows更新
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.meterRowsStateFlow.asStateFlow().collectLatest {
					submitList()
					ifAllDoneShowAll()
				}
			}
		}

		// 訂閱filter
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.groupsFilterFlow.asStateFlow().collectLatest {
					when (it) {
						Filter.All -> binding.allBtn.isChecked = true
						Filter.Undone -> binding.undoneBtn.isChecked = true
					}
					submitList()
				}
			}
		}

		// 訂閱選擇的group: 換底色
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterGroupStateFlow.collectLatest {
					submitList()
				}
			}
		}

		ifAllDoneShowAll()
	}

	// 如果全部抄表完成, 顯示全部
	private fun ifAllDoneShowAll() {
		val undoneSize = meterVM.meterRowsStateFlow.value.toMeterGroups().filter { !it.allRead }.size
		val allDone = undoneSize == 0
		if (allDone) meterVM.groupsFilterFlow.value = Filter.All
	}

	private fun submitList() {
		val meterRows = meterVM.meterRowsStateFlow.value
		val meterGroups = when (meterVM.groupsFilterFlow.value) {
			Filter.All -> meterRows.toMeterGroups()
			Filter.Undone -> meterRows.toMeterGroups().filter { !it.allRead }
		}
		val sMeterGroups = meterGroups.map {
			Selectable(selected = meterVM.selectedMeterGroupStateFlow.value == it, data = it)
		}
		meterGroupListAdapter.submitList(sMeterGroups)
		// 全完成提示
		binding.allDoneCongratsTip.visibility = if (meterGroups.isEmpty()) View.VISIBLE else View.GONE
	}

}