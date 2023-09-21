package com.wavein.gasmeter.ui.meterwork.groups

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.wavein.gasmeter.databinding.FragmentMeterGroupsBinding

class MeterGroupsFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterGroupsBinding? = null
	private val binding get() = _binding!!
	private val meterGroupsVM by activityViewModels<MeterGroupsViewModel>()

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

		binding.doneAllToggleBtn.apply {
			isSingleSelection = true
			addOnButtonCheckedListener { group, checkedId, isChecked ->
				if (group.checkedButtonId == -1) group.check(checkedId)
			}
		}
		binding.doneBtn.isChecked = true

		//...
	}

}