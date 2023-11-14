package com.wavein.gasmeter.ui.metersearch

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.wavein.gasmeter.R
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.data.model.Selectable
import com.wavein.gasmeter.data.model.toMeterGroups
import com.wavein.gasmeter.databinding.FragmentMeterSearchBinding
import com.wavein.gasmeter.tools.textChanges
import com.wavein.gasmeter.ui.NavViewModel
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.meterwork.list.MeterListAdapter
import com.wavein.gasmeter.ui.meterwork.list.MeterRowRender
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MeterSearchFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterSearchBinding? = null
	private val binding get() = _binding!!
	private val navVM by activityViewModels<NavViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()
	private val searchVM by viewModels<MeterSearchViewModel>()

	// 實例
	private lateinit var meterListAdapter:MeterListAdapter

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterSearchBinding.inflate(inflater, container, false)
		return binding.root
	}

	@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// 未選csv提示
		binding.noCsvTipTv.visibility = if (meterVM.meterRowsStateFlow.value.isEmpty()) View.VISIBLE else View.GONE

		// search ui
		binding.searchInput.editText?.let {
			it.textChanges()
				.debounce(300)
				.onEach {
					searchVM.searchStateFlow.value = searchVM.searchStateFlow.value.copy(text = it.toString())
				}
				.launchIn(lifecycleScope)
		}
		binding.lowBatteryCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
			searchVM.searchStateFlow.value = searchVM.searchStateFlow.value.copy(lowBattery = isChecked)
		}
		binding.innerPipeLeakageCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
			searchVM.searchStateFlow.value = searchVM.searchStateFlow.value.copy(innerPipeLeakage = isChecked)
		}
		binding.shutoffCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
			searchVM.searchStateFlow.value = searchVM.searchStateFlow.value.copy(shutoff = isChecked)
		}

		// rv
		meterListAdapter = MeterListAdapter(MeterRowRender.Detail) {
			// 點擊項目: 選擇該meter, 並跳轉到meterRow頁面
			meterVM.setSelectedMeterGroup(meterVM.meterRowsStateFlow.value.toMeterGroups().find { meterGroup -> meterGroup.group == it.group })
			meterVM.selectedMeterRowFlow.value = it
			navVM.meterBaseChangeTabStateFlow.value = 2
			navVM.meterRowPageBackDestinationIsSearch = true
			navVM.navigate(R.id.nav_meterBaseFragment)
		}
		binding.meterRowsRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)) //分隔線
			itemAnimator = DefaultItemAnimator()
			adapter = meterListAdapter
		}

		// 訂閱查詢變化
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				searchVM.searchStateFlow.asStateFlow().collectLatest {
					submitList()
				}
			}
		}
	}

	private fun submitList() {
		val searchState = searchVM.searchStateFlow.value
		if (searchState.notSearch) {
			meterListAdapter.submitList(listOf())
			return
		}

		var meterRows = meterVM.meterRowsStateFlow.value
		if (searchState.text.isNotEmpty()) {
			meterRows = meterRows.filter { meterRow ->
				arrayOf(meterRow.group, meterRow.meterId, meterRow.custId, meterRow.custName, meterRow.custAddr).any {
					it.contains(searchState.text, true)
				}
			}
		}
		if (searchState.lowBattery || searchState.innerPipeLeakage || searchState.shutoff) {
			meterRows = meterRows.filter {
				val cond1 = if (searchState.lowBattery) it.batteryVoltageDropAlarm == true else false
				val cond2 = if (searchState.innerPipeLeakage) it.innerPipeLeakageAlarm == true else false
				val cond3 = if (searchState.shutoff) it.shutoff == true else false
				cond1 || cond2 || cond3
			}
		}
		meterRows = meterRows.sortedWith(compareBy<MeterRow> { it.group }.thenBy { it.queue })
		val sMeterRows = meterRows.map { Selectable(data = it) }

		meterListAdapter.submitList(sMeterRows)
	}
}