package com.wavein.gasmater.ui.test

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.activityViewModels
import com.wavein.gasmater.databinding.FragmentNccBinding

class NccFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentNccBinding? = null
	private val binding get() = _binding!!
	private val nccVM by activityViewModels<NccViewModel>()

	private lateinit var viewModel:NccViewModel

	override fun onDestroyView() {
		super.onDestroyView()
		// 防止內存洩漏
		_binding = null
		kotlin.runCatching {
			requireContext().unregisterReceiver(receiver)
		}
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentNccBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// 檢查權限，沒權限則請求權限
		if (hasPermissions()) {
			onPermissionsAllow()
		} else {
			binding.permission.requestBtn.setOnClickListener {
				requestPermissionLauncher.launch(permissions)
			}
			requestPermissionLauncher.launch(permissions)
		}
	}

	// 當權限皆允許
	private fun onPermissionsAllow() {
		binding.permission.layout.visibility = View.GONE

		// 註冊廣播:偵測藍牙掃描結果
		val intentFilter = IntentFilter().apply {
			addAction("android.bluetooth.devicepicker.action.DEVICE_SELECTED")
			addAction(BluetoothDevice.ACTION_FOUND)
			addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
		}
		requireContext().registerReceiver(receiver, intentFilter)
	}

	// 藍牙配對處理(接收廣播)
	private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context:Context, intent:Intent) {
			when (intent.action) {
				// 當藍牙配對改變
				BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
					Log.i("@@@", "ACTION_BOND_STATE_CHANGED")
				}

				BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
					Log.i("@@@", "ACTION_DISCOVERY_STARTED")
					binding.stateTv.text = "掃描中..."
				}

				BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
					Log.i("@@@", "ACTION_DISCOVERY_FINISHED")
					binding.stateTv.text = "掃描結束"
				}

				BluetoothDevice.ACTION_FOUND -> {
					Log.i("@@@", "ACTION_FOUND")
					val scannedDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

				}
			}
		}
	}

	//region __________權限方法__________

	private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		arrayOf(
			Manifest.permission.BLUETOOTH_CONNECT,
			Manifest.permission.BLUETOOTH_SCAN,
			Manifest.permission.ACCESS_FINE_LOCATION
		)
	} else {
		arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
	}

	private val requestPermissionLauncher:ActivityResultLauncher<Array<String>> by lazy {
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
			if (permissionsMap.all { (permission, isGranted) -> isGranted }) {
				onPermissionsAllow()
			} else {
				onPermissionsNoAllow()
			}
		}
	}

	// 當權限不允許
	private fun onPermissionsNoAllow() {
		val revokedPermissions:List<String> =
			getPermissionsMap().filterValues { isGranted -> !isGranted }.map { (permission, isGranted) -> permission }
		val revokedPermissionsText = """
		缺少權限: ${
			revokedPermissions.map { p -> p.replace(".+\\.".toRegex(), "") }.joinToString(", ")
		}
		請授予這些權限，以便應用程序正常運行。
		Please grant all of them for the app to function properly.
		""".trimIndent()
		binding.permission.revokedTv.text = revokedPermissionsText
	}

	// 是否有全部權限
	private fun hasPermissions(context:Context = requireContext(), permissions:Array<String> = this.permissions):Boolean =
		getPermissionsMap(context, permissions).all { (permission, isGranted) -> isGranted }

	// 取得權限狀態
	private fun getPermissionsMap(
		context:Context = requireContext(),
		permissions:Array<String> = this.permissions,
	):Map<String, Boolean> = permissions.associateWith {
		ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}

	//endregion

}