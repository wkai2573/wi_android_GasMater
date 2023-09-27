package com.wavein.gasmeter.ui.bluetooth

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.core.content.IntentCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.wavein.gasmeter.databinding.DialogBtBinding
import com.wavein.gasmeter.databinding.DialogLoadingBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BtDialogFragment(
	private val onDismissCallback:((dialog:DialogInterface) -> Unit)? = null,
) : DialogFragment() {

	// binding & viewModel
	private var _binding:DialogBtBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BluetoothViewModel>()

	// adapter
	private lateinit var bondedDeviceListAdapter:DeviceListAdapter
	private lateinit var scannedDeviceListAdapter:DeviceListAdapter

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		// 停止掃描
		blVM.stopDiscovery()
		blVM.scanStateFlow.value = ScanState.Idle
		// 註銷廣播
		kotlin.runCatching {
			requireContext().unregisterReceiver(receiver)
		}
		// cb
		onDismissCallback?.invoke(dialog)
		_binding = null
	}

	override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
		return activity?.let {
			_binding = DialogBtBinding.inflate(it.layoutInflater)
			init(it)
			val dialog = ComponentDialog(it, theme).apply {
				setContentView(binding.root)
			}
			dialog
		} ?: throw IllegalStateException("Activity cannot be null")
	}

	private fun init(activity:FragmentActivity) {
		// 註冊廣播:偵測藍牙掃描結果
		val intentFilter = IntentFilter().apply {
			addAction(BluetoothDevice.ACTION_FOUND)
			addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
		}
		requireContext().registerReceiver(receiver, intentFilter)
		initBondedDevice()
		initScanDevice(activity)
	}

	// 初始化已配對設備
	private fun initBondedDevice() {
		bondedDeviceListAdapter = DeviceListAdapter { connectDevice(it) }
		// 已配對的設備
		binding.bondedDeviceRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			// addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)) //分隔線
			itemAnimator = DefaultItemAnimator()
			adapter = bondedDeviceListAdapter
		}
		bondedDeviceListAdapter.submitList(blVM.getBondedRD64HDevices())
	}

	// 初始化掃描設備
	private fun initScanDevice(activity:FragmentActivity) {
		// 訂閱scanState
		lifecycleScope.launch {
			activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.scanStateFlow.collectLatest { scanState -> onScanStateChange(scanState) }
			}
		}

		// 訂閱scanState
		lifecycleScope.launch {
			activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.scanStateFlow.collectLatest { scanState -> onScanStateChange(scanState) }
			}
		}

		// 搜尋藍牙設備
		binding.scanBtn.setOnClickListener { blVM.toggleDiscovery() }
		scannedDeviceListAdapter = DeviceListAdapter { connectDevice(it) }
		binding.scannedDeviceRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			// addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)) //分隔線
			itemAnimator = DefaultItemAnimator()
			adapter = scannedDeviceListAdapter
		}
	}

	// 配對並連線設備
	private fun connectDevice(device:BluetoothDevice) {
		when (device.bondState) {
			// 已配對, 連接設備
			BluetoothDevice.BOND_BONDED -> {
				blVM.connectDevice(device)
				dialog?.dismiss()
			}
			// 未配對, 配對設備
			BluetoothDevice.BOND_NONE -> device.createBond()
		}
	}

	// 藍牙配對處理(接收廣播)
	private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context:Context, intent:Intent) {
			when (intent.action) {
				// 配對後, 連接此設備
				BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
					val bondDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
					when (bondDevice.bondState) {
						BluetoothDevice.BOND_BONDED -> connectDevice(bondDevice)
					}
				}
				// 開始掃描
				BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
					blVM.scannedDeviceListStateFlow.value = emptyList()
					blVM.scanStateFlow.value = ScanState.Scanning
				}
				// 掃描結束
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
					blVM.scanStateFlow.value = ScanState.Idle
				}
				// 掃描中_當發現設備
				BluetoothDevice.ACTION_FOUND -> {
					val scannedDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
					if (scannedDevice.name != blVM.deviceName) return
					if (scannedDevice in blVM.scannedDeviceListStateFlow.value) return
					blVM.scannedDeviceListStateFlow.value = blVM.scannedDeviceListStateFlow.value + scannedDevice
					scannedDeviceListAdapter.submitList(blVM.scannedDeviceListStateFlow.value)
				}
			}
		}
	}

	// 藍牙掃描處理
	private fun onScanStateChange(scanState:ScanState) {
		when (scanState) {
			ScanState.Idle -> {
				binding.scanProgressBar.visibility = View.GONE
				binding.scanBtn.text = "開始掃描"
			}

			ScanState.Scanning -> {
				binding.scanProgressBar.visibility = View.VISIBLE
				binding.scanBtn.text = "停止掃描"
			}

			ScanState.Error -> {
				blVM.scanStateFlow.value = ScanState.Idle
				Toast.makeText(requireActivity(), "掃描失敗", Toast.LENGTH_SHORT).show()
			}
		}
	}

	companion object {
		// 開啟選擇bt視窗
		fun open(context:Context) {
			val supportFragmentManager = (context as FragmentActivity).supportFragmentManager
			BtDialogFragment().show(supportFragmentManager, "BtDialogFragment")
		}
	}

}