package com.wavein.gasmeter.ui.meterwork

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.wavein.gasmeter.databinding.FragmentMeterBaseBinding
import com.wavein.gasmeter.ui.meterwork.groups.MeterGroupsFragment
import com.wavein.gasmeter.ui.meterwork.list.MeterListFragment
import com.wavein.gasmeter.ui.meterwork.row.MeterRowFragment
import com.wavein.gasmeter.ui.setting.CsvViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MeterBaseFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterBaseBinding? = null
	private val binding get() = _binding!!
	private val meterVM by activityViewModels<MeterViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterBaseBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 未選csv提示
		binding.noCsvTipTv.visibility = if (csvVM.meterRowsStateFlow.value.isEmpty()) View.VISIBLE else View.GONE

		// pager
		val meterPageAdapter = MeterPageAdapter(this)
		binding.pager.adapter = meterPageAdapter
		binding.pager.isUserInputEnabled = false  // 禁用滑動切換tab

		// tab
		TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
			when (position) {
				0 -> tab.text = "群組列表"
				1 -> tab.text = "群組瓦斯表"
				2 -> tab.text = "瓦斯表"
				else -> tab.text = "UNKNOWN: $position"
			}
		}.attach()

		// 訂閱選擇的群組, 禁用tab
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterGroupStateFlow.collectLatest { meterGroup ->
					binding.tabLayout.getTabAt(1)?.let { tab ->
						setTabView(meterGroup, tab)
					}
				}
			}
		}

		// 訂閱選擇的表, 禁用tab
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterRowFlow.asStateFlow().collectLatest { meterRow ->
					binding.tabLayout.getTabAt(2)?.let { tab ->
						setTabView(meterRow, tab)
					}
				}
			}
		}
	}

	// tabView禁用與顯示
	private fun setTabView(it:Any?, tab:TabLayout.Tab) {
		if (it == null) {
			tab.view.isEnabled = false
			tab.customView = TextView(requireContext()).apply {
				gravity = Gravity.CENTER
				setTextColor(Color.LTGRAY)
				text = tab.text
			}
		} else {
			tab.view.isEnabled = true
			tab.customView = null
		}
	}

	// 切換tab (給子fragment呼叫)
	fun changeTab(index:Int, smoothScroll:Boolean = true) {
		binding.pager.setCurrentItem(index, smoothScroll)
	}
}


// 分頁管理器
class MeterPageAdapter(fragment:Fragment) : FragmentStateAdapter(fragment) {

	override fun getItemCount():Int = 3

	override fun createFragment(position:Int):Fragment {
		val fragment = when (position) {
			0 -> MeterGroupsFragment()
			1 -> MeterListFragment()
			2 -> MeterRowFragment()
			else -> MeterGroupsFragment()
		}
		return fragment
	}
}
