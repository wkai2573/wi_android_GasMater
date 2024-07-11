package com.wavein.gasmeter.ui.bluetooth

import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BtDialogFragment(
	private val onDismissCallback:((dialog:DialogInterface) -> Unit)? = null,
) : DialogFragment() {


	private var binding:DialogBtBinding? = null
	private val blVM by activityViewModels<BluetoothViewModel>()


	private lateinit var bondedDeviceListAdapter:DeviceListAdapter
	private lateinit var scannedDeviceListAdapter:DeviceListAdapter

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)

		blVM.stopDiscovery()
		blVM.scanStateFlow.value = ScanState.Idle

		kotlin.runCatching {
			requireContext().unregisterReceiver(receiver)
		}

		onDismissCallback?.invoke(dialog)
		binding = null
	}

	override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
		return activity?.let {
			binding = DialogBtBinding.inflate(it.layoutInflater)
			init(it)
			val dialog = ComponentDialog(it, theme).apply {
				setContentView(binding!!.root)
			}
			dialog
		} ?: throw IllegalStateException("Activity cannot be null")
	}

	private fun init(activity:FragmentActivity) {

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


	private fun initBondedDevice() {
		bondedDeviceListAdapter = DeviceListAdapter {
			connectDevice(it)
		}

		binding!!.bondedDeviceRv.apply {
			layoutManager = LinearLayoutManager(requireContext())

			itemAnimator = DefaultItemAnimator()
			adapter = bondedDeviceListAdapter
		}
		bondedDeviceListAdapter.submitList(blVM.getBondedRD64HDevices())
	}


	private fun initScanDevice(activity:FragmentActivity) {

		lifecycleScope.launch {
			activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.scanStateFlow.collectLatest { scanState -> onScanStateChange(scanState) }
			}
		}


		lifecycleScope.launch {
			activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.scanStateFlow.collectLatest { scanState -> onScanStateChange(scanState) }
			}
		}


		binding!!.scanBtn.setOnClickListener { blVM.toggleDiscovery() }
		scannedDeviceListAdapter = DeviceListAdapter {
			connectDevice(it)
		}
		binding!!.scannedDeviceRv.apply {
			layoutManager = LinearLayoutManager(requireContext())

			itemAnimator = DefaultItemAnimator()
			adapter = scannedDeviceListAdapter
		}
	}



	private fun connectDevice(device:BluetoothDevice) {
		when (device.bondState) {

			BluetoothDevice.BOND_BONDED -> {
				blVM.connectDevice(device)
				dialog?.dismiss()
			}

			BluetoothDevice.BOND_NONE -> device.createBond()
		}
	}


	private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context:Context, intent:Intent) {
			when (intent.action) {

				BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
					val bondDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
					when (bondDevice.bondState) {
						BluetoothDevice.BOND_BONDED -> connectDevice(bondDevice)
					}
				}

				BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
					blVM.scannedDeviceListStateFlow.value = emptyList()
					blVM.scanStateFlow.value = ScanState.Scanning
				}

				BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
					blVM.scanStateFlow.value = ScanState.Idle
				}

				BluetoothDevice.ACTION_FOUND -> {
					val scannedDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
					if (scannedDevice.name !in blVM.pairableDeviceNames) return
					if (scannedDevice in blVM.scannedDeviceListStateFlow.value) return
					blVM.scannedDeviceListStateFlow.value += scannedDevice
					scannedDeviceListAdapter.submitList(blVM.scannedDeviceListStateFlow.value)
				}
			}
		}
	}


	private fun onScanStateChange(scanState:ScanState) {
		if (binding == null) return
		when (scanState) {
			ScanState.Idle -> {
				binding!!.scanProgressBar.visibility = View.GONE
				binding!!.scanBtn.text = "開始掃描"
			}

			ScanState.Scanning -> {
				binding!!.scanProgressBar.visibility = View.VISIBLE
				binding!!.scanBtn.text = "停止掃描"
			}

			ScanState.Error -> {
				blVM.scanStateFlow.value = ScanState.Idle
				Toast.makeText(requireActivity(), "掃描失敗，請從設定→藍牙→手動配對設備", Toast.LENGTH_SHORT).show()
			}
		}
	}

	companion object {

		fun open(context:Context) {
			val supportFragmentManager = (context as FragmentActivity).supportFragmentManager
			BtDialogFragment().show(supportFragmentManager, "BtDialogFragment")
		}
	}

}