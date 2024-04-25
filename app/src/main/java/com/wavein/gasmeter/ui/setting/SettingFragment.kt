package com.wavein.gasmeter.ui.setting

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wavein.gasmeter.databinding.FragmentSettingBinding
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import com.wavein.gasmeter.ui.bluetooth.ConnectEvent
import com.wavein.gasmeter.ui.loading.Tip
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class SettingFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentSettingBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BluetoothViewModel>()
	private val settingVM by activityViewModels<SettingViewModel>()

	// cb
	private var onBluetoothOn:(() -> Unit)? = null

	// 靜態變數
	companion object {
		var firstSelectLastBtDevice = true // 首次選擇上次設備
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentSettingBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		checkPermissionAndInitUi()
	}

	// 當所有權限皆允許
	private fun onAllPermissionAllow() {
		binding.permission.layout.visibility = View.GONE

		// 藍牙設備__________

		// APP開啟時 首次選擇上次設備
		if (firstSelectLastBtDevice) {
			firstSelectLastBtDevice = false
			val lastBtDeviceMac = Preference[Preference.LAST_BT_DEVICE_MAC, ""]!!
			if (lastBtDeviceMac.isNotEmpty()) {
				checkBluetoothOn {
					val lastDevice = blVM.getBondedRD64HDevices().find { it.address == lastBtDeviceMac }
					if (lastDevice != null) {
						blVM.connectDevice(lastDevice)
					}
				}
			}
		}

		binding.btSelectBtn.setOnClickListener {
			checkBluetoothOn { BtDialogFragment.open(requireContext()) }
		}

		// 訂閱藍牙設備
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.autoConnectDeviceStateFlow.asStateFlow().collectLatest { device ->
					if (device == null) {
						binding.selectedDevice.layout.visibility = View.GONE
						binding.btSelectBtn.text = "選擇藍牙設備"
					} else {
						binding.selectedDevice.layout.visibility = View.VISIBLE
						val text = "${device.name}\n${device.address}"
						binding.selectedDevice.btInfoTv.text = text
						binding.btSelectBtn.text = "重新選擇設備"
					}
				}
			}
		}

		// 訂閱藍牙事件: 連線成功後立即中斷連線
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.connectEventFlow.asSharedFlow().collectLatest { event ->
					when (event) {
						ConnectEvent.Connecting -> {
							SharedEvent.loadingFlow.value = Tip("設備連結中")
						}

						ConnectEvent.Connected -> {
							SharedEvent.loadingFlow.value = Tip("")
							blVM.disconnectDevice()
						}

						ConnectEvent.ConnectionFailed -> {
							SharedEvent.loadingFlow.value = Tip("")
						}

						ConnectEvent.Listening -> {
							SharedEvent.loadingFlow.value = Tip("")
						}

						ConnectEvent.ConnectionLost -> {
							SharedEvent.loadingFlow.value = Tip("")
						}

						is ConnectEvent.BytesSent -> {}
						is ConnectEvent.BytesReceived -> {}
					}
				}
			}
		}

	}


	// 檢查藍牙是否開啟
	private fun checkBluetoothOn(onBluetoothOn:() -> Unit) {
		this.onBluetoothOn = onBluetoothOn
		if (!blVM.isBluetoothOn()) {
			blVM.checkBluetoothOn(bluetoothRequestLauncher)
		} else {
			onBluetoothOn.invoke()
		}
	}

	// 藍牙請求器
	private val bluetoothRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			this.onBluetoothOn?.invoke()
			this.onBluetoothOn = null
		}
		this.onBluetoothOn = null
	}

	//region __________權限方法__________

	// 檢查權限並重置UI
	private fun checkPermissionAndInitUi() {
		if (hasPermissions() && hasExternalStorageManagerPermission()) {
			onAllPermissionAllow()
			return
		}
		// 有權限不同意
		onAnyPermissionNoAllow()
	}

	// 通常權限
	private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		arrayOf(
			// 藍牙需要
			Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION,
		)
	} else {
		arrayOf(
			Manifest.permission.ACCESS_FINE_LOCATION,
		)
	}

	// 通常權限_通常權限請求器
	private val requestPermissionLauncher:ActivityResultLauncher<Array<String>> by lazy {
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
			if (!hasExternalStorageManagerPermission()) {
				requestExternalStorageManagerPermission()
			} else {
				checkPermissionAndInitUi()
			}
		}
	}

	// 通常權限_是否有通常權限
	private fun hasPermissions(context:Context = requireContext(), permissions:Array<String> = this.permissions):Boolean =
		getPermissionsMap(context, permissions).all { (permission, isGranted) -> isGranted }

	// 通常權限_取得通常權限狀態
	private fun getPermissionsMap(
		context:Context = requireContext(),
		permissions:Array<String> = this.permissions,
	):Map<String, Boolean> = permissions.associateWith {
		ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}

	// 外部存儲_請求外部存儲管理權限
	private fun requestExternalStorageManagerPermission() {
		if (!hasExternalStorageManagerPermission()) {
			val intent = Intent().apply {
				action = ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
				data = Uri.fromParts("package", requireContext().packageName, null)
			}
			requestExternalStorageManagerPermissionLauncher.launch(intent)
		}
	}

	// 外部存儲_外部存儲管理權限請求器
	private val requestExternalStorageManagerPermissionLauncher:ActivityResultLauncher<Intent> =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			checkPermissionAndInitUi()
		}

	// 外部存儲_是否有外部存儲管理權限
	private fun hasExternalStorageManagerPermission() =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.R
				|| Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

	// 首次要求權限
	private var firstRequestPermission = true

	// 當有權限不允許
	private fun onAnyPermissionNoAllow() {
		// 權限提示文字
		val revokedPermissions:List<String> = getPermissionsMap().filterValues { isGranted -> !isGranted }.map { (permission, isGranted) -> permission }
		val permission1Warning = if (revokedPermissions.isEmpty()) "" else
			"缺少權限: ${revokedPermissions.map { p -> p.replace(".+\\.".toRegex(), "") }.joinToString(", ")}"
		val permission2Warning = if (hasExternalStorageManagerPermission()) "" else "缺少外部存儲權限"
		val revokedPermissionsText = """
		$permission1Warning
		$permission2Warning
		請授予這些權限，以便應用程序正常運行。
		Please grant all of them for the app to function properly.
		""".trimIndent()
		binding.permission.revokedTv.text = revokedPermissionsText

		// 權限請求按鈕
		var requestPermissionHandle = {}
		if (!hasPermissions()) {
			requestPermissionHandle = { requestPermissionLauncher.launch(permissions) }
		} else if (!hasExternalStorageManagerPermission()) {
			requestPermissionHandle = { requestExternalStorageManagerPermission() }
		}
		binding.permission.requestBtn.setOnClickListener { requestPermissionHandle() }
		if (firstRequestPermission) {
			firstRequestPermission = false
			requestPermissionHandle()
		}
	}

	//endregion
}
