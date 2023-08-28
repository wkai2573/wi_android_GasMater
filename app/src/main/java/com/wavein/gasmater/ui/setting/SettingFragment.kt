package com.wavein.gasmater.ui.setting

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.wavein.gasmater.databinding.FragmentSettingBinding
import com.wavein.gasmater.tools.SharedEvent
import com.wavein.gasmater.ui.csv.ReadFileState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.FileInputStream

@SuppressLint("MissingPermission")
class SettingFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentSettingBinding? = null
	private val binding get() = _binding!!
	private val settingVM by activityViewModels<SettingViewModel>()

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
		// 檢查權限，沒權限則請求權限
		if (hasPermissions()) {
			onPermissionsAllow()
		} else {
			binding.requestPermissionBtn.setOnClickListener {
				requestPermissionLauncher.launch(permissions)
			}
			requestPermissionLauncher.launch(permissions)
		}
	}

	// 當權限皆允許
	private fun onPermissionsAllow() {
		binding.revokedPermissionLayout.visibility = View.GONE

		// vm初始化
		settingVM.setDeviceOnClick { bleDevice ->
			lifecycleScope.launch {
				SharedEvent._eventFlow.emit(SharedEvent.ShowSnackbar("選擇了 ${bleDevice.name} \nTODO 配對該設備..."))
			}
			settingVM.connectBleDevice(requireContext(), bleDevice)
		}

		// UI:csv__________
		binding.selectCsvBtn.setOnClickListener {
			settingVM.selectReadCsv(filePickerLauncher)
		}

		// UI:藍牙__________
		binding.scanBtn.setOnClickListener {
			settingVM.scanLeDevice()
		}
		binding.deviceRv.apply {
			layoutManager = LinearLayoutManager(requireContext())
			addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
			itemAnimator = DefaultItemAnimator()
			adapter = settingVM.leDeviceListAdapter
		}

		// 訂閱scanning
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				settingVM.scanningStateFlow.collectLatest { scanning ->
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
				settingVM.btDeviceListStateFlow.collectLatest {
					settingVM.leDeviceListAdapter.submitList(it)
				}
			}
		}

		// TODO 訂閱藍牙設備連接狀態
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				settingVM.bluetoothProfileStateFlow.collectLatest {
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

	//region __________權限__________

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
		binding.revokedPermissionTv.text = revokedPermissionsText
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

	//region __________CSV檔案__________

	// 當選擇檔案
	private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		settingVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.Reading)
		if (result.resultCode == Activity.RESULT_OK) {
			kotlin.runCatching {
				val uri = result.data?.data ?: return@registerForActivityResult
				val fileDescriptor = requireContext().contentResolver.openFileDescriptor(uri, "r") ?: return@registerForActivityResult
				val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
				val rows:List<Map<String, String>> = csvReader().readAllWithHeader(inputStream)
				settingVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.Idle)
				settingVM.rowsStateFlow.value = rows
			}.onFailure {
				settingVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, it.message)
			}
		} else {
			settingVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, result.resultCode.toString())
		}
	}
}