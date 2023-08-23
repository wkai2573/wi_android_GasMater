package com.wavein.gasmater.ui.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile
import android.os.Bundle
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

@SuppressLint("MissingPermission")
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

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// vm初始化
		bleVM.setDeviceOnClick { bleDevice ->
			Toast.makeText(requireContext(), "選擇了 ${bleDevice.name} \nTODO 配對該設備...", Toast.LENGTH_SHORT).show()
			bleVM.connectBleDevice(requireContext(), bleDevice)
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

		// 訂閱藍牙設備清單
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				bleVM.btDeviceListStateFlow.collectLatest {
					bleVM.leDeviceListAdapter.submitList(it)
				}
			}
		}

		// TODO 訂閱藍牙設備連接狀態
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				bleVM.bluetoothProfileStateFlow.collectLatest {
					when (it) {
						BluetoothProfile.STATE_CONNECTING -> {}
						BluetoothProfile.STATE_CONNECTED -> {}
						BluetoothProfile.STATE_DISCONNECTING -> {}
						BluetoothProfile.STATE_DISCONNECTED -> {}
					}
				}
			}
		}

	}
}