package com.wavein.gasmater.ui.bt

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.wavein.gasmater.databinding.FragmentBleBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BleDeviceFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentBleBinding? = null
	private val binding get() = _binding!!
	private val bleVM by activityViewModels<BleDeviceViewModel>()

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentBleBinding.inflate(inflater, container, false)
		return binding.root
	}

	@SuppressLint("MissingPermission")
	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		bleVM.init(requireContext()) {
			Toast.makeText(requireContext(), "選擇了 ${it.name} \nTODO 配對該設備...", Toast.LENGTH_SHORT).show()
		}

		// 訂閱scanning
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				bleVM.scanningStateFlow.collectLatest { scanning ->
					if (scanning) {
						binding.scanBtn.text = "STOP"
						binding.scanning.visibility = View.VISIBLE
					} else {
						binding.scanBtn.text = "SCAN"
						binding.scanning.visibility = View.GONE
					}
				}
			}
		}

		// 訂閱藍牙設備更新
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				bleVM.btDeviceListStateFlow.collectLatest {
					bleVM.leDeviceListAdapter.submitList(it)
				}
			}
		}

		// ui
		binding.scanBtn.setOnClickListener {
			bleVM.scanLeDevice()
		}
		binding.bleRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
			itemAnimator = DefaultItemAnimator()
			adapter = bleVM.leDeviceListAdapter
		}
	}

}