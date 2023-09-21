package com.wavein.gasmeter.ui.meterwork

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.wavein.gasmeter.databinding.FragmentMeterBaseBinding
import com.wavein.gasmeter.ui.meterwork.groups.MeterGroupsFragment
import com.wavein.gasmeter.ui.meterwork.list.MeterListFragment


class MeterBaseFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterBaseBinding? = null
	private val binding get() = _binding!!
	private val meterBaseVM by activityViewModels<MeterBaseViewModel>()

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

		//TODO pager
		val meterPageAdapter = MeterPageAdapter(this)
		binding.pager.adapter = meterPageAdapter

		//TODO Tab
		TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
			when(position) {
				0 -> {
					tab.text = "群組列表"
				}
				1 -> {
					tab.text = "群組瓦斯表"
					//todo tab.view.isEnabled = false
				}
				2 -> {
					tab.text = "瓦斯表"
					//todo tab.view.isEnabled = false
				}
				else -> {
					tab.text = "UNKNOWN: $position"
					tab.view.isEnabled = false
				}
			}
		}.attach()
	}

}


// 分頁管理器
class MeterPageAdapter(fragment:Fragment) : FragmentStateAdapter(fragment) {

	override fun getItemCount():Int = 3

	override fun createFragment(position:Int):Fragment {
		val fragment = when (position) {
			0 -> MeterGroupsFragment()
			1 -> MeterListFragment()
			2 -> MeterGroupsFragment()
			else -> MeterGroupsFragment()
		}
		return fragment
	}
}
