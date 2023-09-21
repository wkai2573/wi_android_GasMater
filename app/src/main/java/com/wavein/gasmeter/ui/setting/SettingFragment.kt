package com.wavein.gasmeter.ui.setting

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wavein.gasmeter.databinding.FragmentSettingBinding
import com.wavein.gasmeter.ui.bluetooth.BlueToothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class SettingFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentSettingBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BlueToothViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()
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
			binding.permission.requestBtn.setOnClickListener {
				requestPermissionLauncher.launch(permissions)
			}
			requestPermissionLauncher.launch(permissions)
		}
	}

	// 當權限皆允許
	private fun onPermissionsAllow() {
		binding.permission.layout.visibility = View.GONE

		// 藍牙裝置__________

		// 註冊藍牙設備
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.autoConnectDeviceStateFlow.asStateFlow().collectLatest { device ->
					if (device == null) {
						binding.selectedDevice.layout.visibility = View.GONE
						binding.btSelectBtn.text = "選擇裝置"
					} else {
						binding.selectedDevice.layout.visibility = View.VISIBLE
						val text = "${device.name} [${device.address}]"
						binding.selectedDevice.btInfoTv.text = text
						binding.btSelectBtn.text = "重新選擇裝置"
					}
				}
			}
		}
		binding.btSelectBtn.setOnClickListener {
			BtDialogFragment.open(requireContext())
		}


		// 檔案管理__________

		// 註冊Csv檔案
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				csvVM.selectedFileStateFlow.asStateFlow().collectLatest { fileState ->
					if (!fileState.isOpened) {
						binding.selectedCsv.layout.visibility = View.GONE
						binding.selectCsvFromLocalBtn.text = "選擇CSV"
					} else {
						binding.selectedCsv.layout.visibility = View.VISIBLE
						binding.selectedCsv.infoTv.text = fileState.name
						binding.selectCsvFromLocalBtn.text = "重新選擇CSV"
					}
				}
			}
		}

		binding.selectCsvFromLocalBtn.setOnClickListener {
			csvVM.selectCsv(filePickerLauncher)
		}

	}

	// 選擇檔案Launcher
	private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		csvVM.readCsv(requireContext(), result)
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