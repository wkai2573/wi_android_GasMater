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
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.FragmentSettingBinding
import com.wavein.gasmeter.tools.NetworkInfo
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import com.wavein.gasmeter.ui.ftp.AppState
import com.wavein.gasmeter.ui.ftp.FtpConnState
import com.wavein.gasmeter.ui.ftp.FtpSettingDialogFragment
import com.wavein.gasmeter.ui.ftp.FtpViewModel
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
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
		binding.btSelectBtn.setOnClickListener {
			checkBluetoothOn { BtDialogFragment.open(requireContext()) }
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
					} else {
						binding.selectedCsv.layout.visibility = View.VISIBLE
						binding.selectedCsv.infoTv.text = fileState.name
						binding.selectCsvFromLocalBtn.text = "重新選擇CSV"
						binding.uploadCsvBtn.isEnabled = true
					}
				}
			}
		}

		binding.selectCsvFromLocalBtn.setOnClickListener {
			csvVM.openFilePicker(filePickerLauncher)
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
				onSaveCallback = {
					ftpVM.saveFtpInfo(it)
				})
		}

		binding.uploadFtpSettingBtn.setOnClickListener {
			FtpSettingDialogFragment.open(
				context = requireContext(),
				ftpInfo = ftpVM.uploadFtpInfo,
				onSaveCallback = {
					ftpVM.saveFtpInfo(it)
				})
		}

		binding.systemFtpSettingBtn.setOnClickListener {
			FtpSettingDialogFragment.open(
				context = requireContext(),
				ftpInfo = ftpVM.systemFtpInfo,
				onSaveCallback = {
					ftpVM.saveFtpInfo(it)
				})
		}

		//todo 測試
		binding.test1Btn.setOnClickListener {
			lifecycleScope.launch {
				SharedEvent.eventFlow.emit(SharedEvent.ShowDialog("title", meterVM.meterRowsStateFlow.value.toString()))
			}
		}

		binding.test2Btn.setOnClickListener {
			//csvVM.writeFile(fileInfo.relativePath, "隨機數,欄位2,\n${Random.nextInt(1, 100)},內容2")

			val meterRows = meterVM.meterRowsStateFlow.value.toMutableList()
			// rows.add(
			// 	mapOf("header1" to "AAA", "header2" to "中文", "header3" to "♥\n🙄🙄🙄\n這樣也\"可以\"??")
			// )
			meterVM.meterRowsStateFlow.value = meterRows
			csvVM.saveCsv(meterVM)
		}


		// 產品註冊__________

		// ui
		val savedAppkey = Preference[Preference.APP_KEY, ""]!!
		binding.appkeyEt.setText(savedAppkey)
		binding.appActivateBtn.setOnClickListener {
			var uuid = settingVM.uuidStateFlow.value
			if (uuid.isEmpty()) settingVM.initUuid()
			uuid = settingVM.uuidStateFlow.value
			val appkey = binding.appkeyEt.text.toString()
			ftpVM.checkAppActivate(uuid, appkey)
			// 關閉軟鍵盤
			val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
			imm?.hideSoftInputFromWindow(binding.appkeyEt.windowToken, 0)
		}

		// 訂閱FTP連接狀態 (loading)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				ftpVM.ftpConnStateFlow.asStateFlow().collectLatest {
					when (it) {
						is FtpConnState.Idle -> SharedEvent.loadingFlow.value = ""
						is FtpConnState.Connecting -> SharedEvent.loadingFlow.value =
							if (ftpVM.appStateFlow.value == AppState.Checking) "序號驗證中" else "連線FTP中"

						is FtpConnState.Connected -> SharedEvent.loadingFlow.value = ""
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
							binding.appkeyLayout.visibility = View.VISIBLE
							binding.btArea.visibility = View.GONE
							binding.fileArea.visibility = View.GONE
							binding.appActivateBtn.callOnClick()
						}

						AppState.Checking -> {}

						AppState.Inactivated -> {
							binding.appActivatedTv.text = "產品未開通"
							binding.appActivatedTv.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_error))
							binding.appkeyLayout.visibility = View.VISIBLE
							binding.btArea.visibility = View.GONE
							binding.fileArea.visibility = View.GONE
						}

						AppState.Activated -> {
							val uuid = settingVM.uuidStateFlow.value
							val appkey = binding.appkeyEt.text.toString()
							val text = "產品已開通\n裝置UUID: $uuid\n產品序號: $appkey"
							binding.appActivatedTv.text = text
							binding.appActivatedTv.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_light_tertiary))
							binding.appkeyLayout.visibility = View.GONE
							binding.btArea.visibility = View.VISIBLE
							binding.fileArea.visibility = View.VISIBLE
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

	// 選擇檔案Launcher
	private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		csvVM.readCsvByPicker(requireContext(), result, meterVM)
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
			Manifest.permission.MANAGE_DOCUMENTS
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
