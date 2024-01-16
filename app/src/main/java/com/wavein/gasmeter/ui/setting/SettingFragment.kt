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
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.FragmentSettingBinding
import com.wavein.gasmeter.tools.FileUtils
import com.wavein.gasmeter.tools.NetworkInfo
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.rd64h.RD64H
import com.wavein.gasmeter.tools.rd64h.hexToUBytes
import com.wavein.gasmeter.tools.rd64h.toHex
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import com.wavein.gasmeter.ui.bluetooth.ConnectEvent
import com.wavein.gasmeter.ui.ftp.AppState
import com.wavein.gasmeter.ui.ftp.FtpConnState
import com.wavein.gasmeter.ui.ftp.FtpSettingDialogFragment
import com.wavein.gasmeter.ui.ftp.FtpViewModel
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
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
	private val csvVM by activityViewModels<CsvViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()
	private val ftpVM by activityViewModels<FtpViewModel>()
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
	@OptIn(ExperimentalUnsignedTypes::class)
	private fun onAllPermissionAllow() {
		binding.permission.layout.visibility = View.GONE

		// todo 測試用
		binding.testBtn.setOnClickListener {

			lifecycleScope.launch {
				SharedEvent.catching {
					// val ss = Preference[Preference.SESSION_KEY_FILE, ""]!!
					// val (cryptKey, macKey) = RD64H.Auth.decryptKeyFile(ss)
					val 電文前面87位 = "7140080041504c41444452494e495456414c303030303030303030303030303040454e31523136202020202020202020202020202020202020202020202020202020202020202020202020202020202020240104104518".hexToUBytes()
					val mac = RD64H.Auth.calcMac(電文前面87位.toByteArray())
					Log.i("@@@", mac.toHex())
				}
			}
			// val bytes1 = byteArrayOf(0x61.toByte(), 0x62.toByte())
			// val ubytes1 = ubyteArrayOf(0x61.toUByte(), 0x62.toUByte())
			// val bytes2 = byteArrayOf(0x80.toByte(), 0x81.toByte())
			// val ubytes2 = ubyteArrayOf(0x80.toUByte(), 0x81.toUByte())
			// Log.i("@@@", bytes1.toHex().hexToString())
			// Log.i("@@@", ubytes1.toHex().hexToString())
			// Log.i("@@@", bytes2.toHex().hexToString())
			// Log.i("@@@", ubytes2.toHex().hexToString())

			// settingVM.createLogFile("表IDD", "C41", "舊值啦", "新值啦")
			// ftpVM.uploadLog()
		}

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

		// 檔案管理__________

		// 訂閱Csv檔案
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				csvVM.selectedFileStateFlow.asStateFlow().collectLatest { fileState ->
					if (!fileState.isOpened) {
						binding.selectedCsv.layout.visibility = View.GONE
						binding.selectCsvFromLocalBtn.text = "選擇CSV"
						binding.uploadCsvBtn.isEnabled = false
						binding.resetDegreeBtn.isEnabled = false
					} else {
						binding.selectedCsv.layout.visibility = View.VISIBLE
						binding.selectedCsv.infoTv.text = fileState.name
						binding.selectCsvFromLocalBtn.text = "重新選擇CSV"
						binding.uploadCsvBtn.isEnabled = true
						binding.resetDegreeBtn.isEnabled = true
					}
				}
			}
		}

		binding.selectCsvFromLocalBtn.setOnClickListener {
			val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
				addCategory(Intent.CATEGORY_OPENABLE)
				type = "text/*" // 檔案類型 ("text/csv"會無效)
			}
			csvFilePickerLauncher.launch(intent)
		}

		binding.resetDegreeBtn.setOnClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("重置抄表值")
				.setMessage("確定要重置抄表值？")
				.setNegativeButton("取消") { dialog, which -> }
				.setPositiveButton("確定") { dialog, which ->
					val newCsvRows = meterVM.meterRowsStateFlow.value.map { meterRow ->
						if (meterRow.degreeRead) {
							meterRow.copy(
								isManualMeterDegree = false,
								meterDegree = null,
								lastMeterDegree = meterRow.meterDegree,
								meterReadTime = null,
								lastMeterReadTime = meterRow.meterReadTime,
								batteryVoltageDropAlarm = null,
								innerPipeLeakageAlarm = null,
								shutoff = null,
								alarmInfo1 = null,
							)
						} else {
							meterRow
						}
					}
					csvVM.updateSaveCsv(newCsvRows, meterVM)
					lifecycleScope.launch {
						SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("csv抄表值已重置", SharedEvent.Color.Success))
					}
				}
				.show()
		}

		binding.downloadCsvBtn.setOnClickListener {
			if (ftpVM.downloadFtpInfo.host.isNotEmpty()) {
				ftpVM.downloadFileOpenFolder(requireContext(), csvVM, meterVM)
			} else {
				FtpSettingDialogFragment.open(
					context = requireContext(),
					ftpInfo = ftpVM.downloadFtpInfo,
					saveBtnText = "下載",
					saveBtnIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_download_24),
					onSaveCallback = {
						ftpVM.downloadFileOpenFolder(requireContext(), csvVM, meterVM)
					})
			}
		}

		binding.uploadCsvBtn.setOnClickListener {
			val fileState = csvVM.selectedFileStateFlow.value
			if (ftpVM.uploadFtpInfo.host.isNotEmpty()) {
				ftpVM.uploadFile(requireContext(), fileState)
			} else {
				FtpSettingDialogFragment.open(
					context = requireContext(),
					ftpInfo = ftpVM.uploadFtpInfo,
					saveBtnText = "上傳",
					saveBtnIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_upload_24),
					onSaveCallback = {
						ftpVM.uploadFile(requireContext(), fileState)
					})
			}
		}

		binding.downloadFtpSettingBtn.setOnClickListener {
			FtpSettingDialogFragment.open(
				context = requireContext(),
				ftpInfo = ftpVM.downloadFtpInfo,
				onSaveCallback = { ftpVM.saveFtpInfo(it) })
		}

		binding.uploadFtpSettingBtn.setOnClickListener {
			FtpSettingDialogFragment.open(
				context = requireContext(),
				ftpInfo = ftpVM.uploadFtpInfo,
				onSaveCallback = { ftpVM.saveFtpInfo(it) })
		}

		binding.systemFtpSettingBtn.setOnClickListener {
			FtpSettingDialogFragment.open(
				context = requireContext(),
				ftpInfo = ftpVM.systemFtpInfo,
				onSaveCallback = { ftpVM.saveFtpInfo(it) })
		}

		// 通信設定__________

		// 註冊UUID
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				settingVM.sessionKeyFlow.asStateFlow().collectLatest { sessionKey ->
					if (sessionKey.isEmpty()) {
						binding.sessionKeyInput.editText?.setText("未設定通信金鑰 (點擊設定)")
						binding.sessionKeyInput.editText?.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_error))
					} else {
						binding.sessionKeyInput.editText?.setText("已設定通信金鑰 (點擊重新設定)")
						binding.sessionKeyInput.editText?.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_tertiary))
					}
				}
			}
		}

		binding.sessionKeyInput.editText?.setOnClickListener {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
				addCategory(Intent.CATEGORY_OPENABLE)
				type = "*/*"
			}
			sessionKeyFilePickerLauncher.launch(intent)
		}

		// 產品註冊__________

		// ui
		val savedAppkey = Preference[Preference.APP_KEY, ""]!!
		val savedCompany = Preference[Preference.USER_COMPANY, ""]!!
		val savedDep = Preference[Preference.USER_DEP, ""]!!
		val savedUsername = Preference[Preference.USER_NAME, ""]!!
		binding.appkeyInput.editText?.setText(savedAppkey)
		binding.companyInput.editText?.setText(savedCompany)
		binding.depInput.editText?.setText(savedDep)
		binding.usernameInput.editText?.setText(savedUsername)
		binding.appActivateBtn.setOnClickListener {
			var uuid = settingVM.uuidStateFlow.value
			if (uuid.isEmpty()) settingVM.initUuid()
			uuid = settingVM.uuidStateFlow.value
			val appkey = binding.appkeyInput.editText?.text.toString()
			val company = binding.companyInput.editText?.text.toString()
			val dep = binding.depInput.editText?.text.toString()
			val username = binding.usernameInput.editText?.text.toString()
			binding.appkeyInput.error = if (appkey.isEmpty()) "請輸入序號" else ""
			binding.companyInput.error = if (company.isEmpty()) "請輸入公司名稱" else ""
			binding.depInput.error = if (dep.isEmpty()) "請輸入部門" else ""
			binding.usernameInput.error = if (username.isEmpty()) "請輸入姓名" else ""
			if (appkey.isEmpty() || company.isEmpty() || dep.isEmpty() || username.isEmpty()) {
				return@setOnClickListener
			}
			ftpVM.checkAppActivate(uuid, appkey, company, dep, username)
			// 關閉軟鍵盤
			val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
			imm?.hideSoftInputFromWindow(binding.appkeyInput.editText?.windowToken, 0)
		}

		// 訂閱FTP連接狀態 (loading)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				ftpVM.ftpConnStateFlow.asStateFlow().collectLatest {
					when (it) {
						is FtpConnState.Idle -> SharedEvent.loadingFlow.value = Tip()
						is FtpConnState.Connecting -> SharedEvent.loadingFlow.value =
							Tip(if (ftpVM.appStateFlow.value == AppState.Checking) "序號驗證中" else "連線FTP中")

						is FtpConnState.Connected -> SharedEvent.loadingFlow.value = Tip()
					}
				}
			}
		}

		// 訂閱開通狀態
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				ftpVM.appStateFlow.asStateFlow().collectLatest {
					when (it) {
						AppState.NotChecked -> {
							binding.appActivatedTv.text = "產品未開通"
							binding.appActivatedTv.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_error))
							binding.appkeyInput.visibility = View.VISIBLE
							binding.companyInput.visibility = View.VISIBLE
							binding.depInput.visibility = View.VISIBLE
							binding.usernameInput.visibility = View.VISIBLE
							binding.appActivateBtn.visibility = View.VISIBLE
							binding.btArea.visibility = View.GONE
							binding.fileArea.visibility = View.GONE
							binding.commArea.visibility = View.GONE
							if (Preference[Preference.APP_ACTIVATED, false]!!) {
								binding.appActivateBtn.callOnClick()
							}
						}

						AppState.Checking -> {}

						AppState.Inactivated -> {
							binding.appActivatedTv.text = "產品未開通"
							binding.appActivatedTv.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_error))
							binding.appkeyInput.visibility = View.VISIBLE
							binding.companyInput.visibility = View.VISIBLE
							binding.depInput.visibility = View.VISIBLE
							binding.usernameInput.visibility = View.VISIBLE
							binding.appActivateBtn.visibility = View.VISIBLE
							binding.btArea.visibility = View.GONE
							binding.fileArea.visibility = View.GONE
							binding.commArea.visibility = View.GONE
						}

						AppState.Activated -> {
							val uuid = settingVM.uuidStateFlow.value
							val appkey = binding.appkeyInput.editText?.text.toString()
							val text = "產品已開通\n裝置UUID: $uuid\n產品序號: $appkey"
							binding.appActivatedTv.text = text
							binding.appActivatedTv.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_tertiary))
							binding.appkeyInput.visibility = View.GONE
							binding.companyInput.visibility = View.GONE
							binding.depInput.visibility = View.GONE
							binding.usernameInput.visibility = View.GONE
							binding.appActivateBtn.visibility = View.GONE
							binding.btArea.visibility = View.VISIBLE
							binding.fileArea.visibility = View.VISIBLE
							binding.commArea.visibility = View.VISIBLE
						}
					}
				}
			}
		}

		/**
		 * 初始化APP開通狀態
		 * 開啟APP, 狀態=未檢查 ->
		 * 	有開通+有網路 -> 檢查序號，然後 { 狀態=開通|未開通 }
		 * 	有開通+無網路 { 狀態=開通 }
		 * 	無開通 { 狀態=未開通 }
		 */
		if (ftpVM.appStateFlow.value == AppState.NotChecked) {
			when (Preference[Preference.APP_ACTIVATED, false]!!) {
				true -> {
					when (NetworkInfo.networkStateFlow.value) {
						NetworkInfo.NetworkState.Available -> binding.appActivateBtn.callOnClick()
						else -> ftpVM.appStateFlow.value = AppState.Activated
					}
				}

				false -> {
					ftpVM.appStateFlow.value = AppState.Inactivated
				}
			}
		}

		// 系統設定__________

		// 訂閱系統區塊顯示
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				ftpVM.systemAreaOpenedStateFlow.asStateFlow().collectLatest {
					when (it) {
						true -> binding.systemArea.visibility = View.VISIBLE
						false -> binding.systemArea.visibility = View.GONE
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

	// csv檔案PickerLauncher
	private val csvFilePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		csvVM.readCsvByPicker(requireContext(), result, meterVM)
	}

	// 通信keyPickerLauncher
	private val sessionKeyFilePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			runCatching {
				val uri = result.data?.data!!
				val sessionKey = FileUtils.readFileContent(requireContext(), uri, 1024, listOf(".key", ".txt"))
				Preference[Preference.SESSION_KEY_FILE] = sessionKey
				settingVM.initSessionKey()
				if (RD64H.Auth.macKey.isEmpty()) {
					throw Exception("金鑰異常，無法分析出MacKey")
				} else {
					lifecycleScope.launch {
						SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("通信金鑰設定成功", SharedEvent.Color.Success))
					}
				}
			}.onFailure {
				it.printStackTrace()
				lifecycleScope.launch {
					SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(it.message ?: "檔案讀取失敗", SharedEvent.Color.Error))
				}
			}
		}
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
