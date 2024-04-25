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
import android.util.Log
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.wavein.gasmeter.databinding.FragmentSettingBinding
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.rd64h.info.D87D24Info
import com.wavein.gasmeter.tools.rd64h.info.GwD34Info
import com.wavein.gasmeter.tools.rd64h.info.MetaInfo
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import com.wavein.gasmeter.ui.bluetooth.CommEndEvent
import com.wavein.gasmeter.ui.bluetooth.CommState
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
						// blVM.connectDevice(lastDevice)
						blVM.setAutoConnectBluetoothDevice(lastDevice)
					}
				}
			}
		}

		binding.btSelectBtn.setOnClickListener {
			BtDialogFragment.open(requireContext())
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

		// 連線相關的訂閱
		initConnSubscription()

		// GW頻道切換__________

		binding.homeIdField.editText?.setText(Preference[Preference.NCC_METER_ID, ""])

		(binding.channelField.editText as? MaterialAutoCompleteTextView)?.apply {
			setSimpleItems(arrayOf("1", "2", "3", "4", "5", "6"))
			setText("3", false)
		}

		binding.gwChannelSetBtn.setOnClickListener {
			val meterId = binding.homeIdField.editText?.text?.toString() ?: ""
			if (meterId.length != 14) {
				binding.homeIdField.error = "長度必須為14位"
				return@setOnClickListener
			}
			binding.homeIdField.error = ""
			Preference[Preference.NCC_METER_ID] = meterId
			// 傳送電文
			checkBluetoothOn {
				blVM.sendTxGwSetChannel(meterId, binding.channelField.editText?.text.toString())
			}
		}

		binding.gwChannelReadBtn.setOnClickListener {
			val meterId = binding.homeIdField.editText?.text?.toString() ?: ""
			if (meterId.length != 14) {
				binding.homeIdField.error = "長度必須為14位"
				return@setOnClickListener
			}
			binding.homeIdField.error = ""
			Preference[Preference.NCC_METER_ID] = meterId
			// 傳送電文
			checkBluetoothOn {
				blVM.sendTxGwReadChannel(meterId)
			}
		}
	}


	//region__________連線方法__________

	// 連線相關的訂閱
	private fun initConnSubscription() {

		// 訂閱通信中 進度文字
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commTextStateFlow.asStateFlow().collectLatest {
					Log.i("@@@通信狀態", it.title)
					SharedEvent.loadingFlow.value = when (it.title) {
						"未連結設備", "通信完畢" -> Tip()
						"設備已連結" -> Tip("設備已連結")
						else -> it.copy()
					}
				}
			}
		}

		// 訂閱藍牙事件
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.connectEventFlow.asSharedFlow().collectLatest { event ->
					when (event) {
						ConnectEvent.Connecting -> {}
						ConnectEvent.Connected -> {}
						ConnectEvent.ConnectionFailed -> {
							SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("設備連結失敗", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
						}

						ConnectEvent.Listening -> {}
						ConnectEvent.ConnectionLost -> {}

						is ConnectEvent.BytesSent -> {}
						is ConnectEvent.BytesReceived -> {}
						else -> {}
					}
				}
			}
		}

		// 訂閱溝通狀態
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commStateFlow.asStateFlow().collectLatest { state ->
					when (state) {
						CommState.NotConnected -> {}
						CommState.Communicating, CommState.Connecting -> {}
						CommState.ReadyCommunicate -> {
							onConnected?.invoke()
							onConnected = null
						}

						else -> {}
					}
				}
			}
		}

		// 訂閱溝通結束事件
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commEndSharedEvent.asSharedFlow().collectLatest { event ->
					SharedEvent.catching {
						when (event) {
							is CommEndEvent.Success -> {
								val message = if (event.commResult.containsKey("success")) {
									event.commResult["success"].toString()
								} else {
									"通信成功"
								}
								SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(message, SharedEvent.Color.Success, Snackbar.LENGTH_INDEFINITE))
								SharedEvent.eventFlow.emit(SharedEvent.PlayEffect())
								afterComm(event.commResult) // 依據結果更新csvRows
							}

							is CommEndEvent.Error -> {
								val message = if (event.commResult.containsKey("error")) {
									event.commResult["error"].toString()
								} else {
									event.commResult.toString()
								}
								SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(message, SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
								SharedEvent.eventFlow.emit(SharedEvent.PlayEffect())
								afterComm(event.commResult) // 依據結果更新csvRows
							}

							else -> {}
						}
					}
				}
			}
		}
	}

	// cb
	private var onBluetoothOn:(() -> Unit)? = null
	private var onConnected:(() -> Unit)? = null
	private var onConnectionFailed:(() -> Unit)? = null

	// 藍牙請求器
	private val bluetoothRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			this.onBluetoothOn?.invoke()
			this.onBluetoothOn = null
		}
		this.onBluetoothOn = null
	}

	// 檢查藍牙是否開啟
	private fun checkBluetoothOn(onConnected:() -> Unit) {
		this.onBluetoothOn = { checkReadyCommunicate(onConnected) }
		if (!blVM.isBluetoothOn()) {
			blVM.checkBluetoothOn(bluetoothRequestLauncher)
		} else {
			onBluetoothOn?.invoke()
			onBluetoothOn = null
		}
	}

	// 檢查能不能進行通信
	private fun checkReadyCommunicate(onConnected:() -> Unit) {
		when (blVM.commStateFlow.value) {
			CommState.NotConnected -> autoConnectDevice(onConnected)
			CommState.ReadyCommunicate -> onConnected.invoke()
			CommState.Connecting -> {}
			CommState.Communicating -> {}
			else -> {}
		}
	}

	// 如果有連線過的設備,直接嘗試連線, 沒有若連線失敗則開藍牙窗
	private fun autoConnectDevice(onConnected:() -> Unit) {
		if (blVM.autoConnectDeviceStateFlow.value != null) {
			this.onConnectionFailed = { BtDialogFragment.open(requireContext()) }
			this.onConnected = onConnected
			blVM.connectDevice()
		} else {
			this.onConnected = onConnected
			BtDialogFragment.open(requireContext())
		}
	}

	// 處理通信結果
	private suspend fun afterComm(commResult:Map<String, Any>) {
		SharedEvent.catching {
			Log.i("@@@通信結果 ", commResult.toString())
			val metaInfo = commResult["meta"] as MetaInfo

			when (metaInfo.op) {
				"R89_GW" -> {
					if (commResult.containsKey("GwD34")) {
						val info = commResult["GwD34"] as GwD34Info
						binding.gwReadTv.text = info.data
						SharedEvent.eventFlow.emit(
							SharedEvent.ShowSnackbar("通信成功，目前的頻道為 ${info.data[4]}", SharedEvent.Color.Success, Snackbar.LENGTH_INDEFINITE)
						)
					}
				}
			}
		}
	}

	//endregion

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
