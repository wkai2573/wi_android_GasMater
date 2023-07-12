package com.wavein.gasmater.ui.findbt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.activityViewModels
import com.wavein.gasmater.databinding.FragmentFindbtBinding

class FindbtFragment : Fragment() {
	// binding & viewModel
	private var _binding:FragmentFindbtBinding? = null
	private val binding get() = _binding!!
	private val vm by activityViewModels<FindbtViewModel>()

	// 權限
	private val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
	private val requestPermissionLauncher:ActivityResultLauncher<Array<String>> by lazy {
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
			if (permissionsMap.all { (permission, isGranted) -> isGranted }) {
				onPermissionsAllow()
			} else {
				onPermissionsNoAllow()
			}
		}
	}

	// 藍牙adapter
	private val bluetoothManager:BluetoothManager? by lazy { requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
	private val bluetoothAdapter:BluetoothAdapter? by lazy { bluetoothManager?.adapter }

	// 防止內存洩漏
	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentFindbtBinding.inflate(inflater, container, false)
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

		binding.button.setOnClickListener {
			檢查藍牙開啟 {
				藍牙搜尋()
			}
		}
	}


	// 檢查藍牙開啟
	private fun 檢查藍牙開啟(cb:() -> Unit) {
		if (bluetoothAdapter == null) {
			Toast.makeText(requireContext(), "設備不支援藍牙", Toast.LENGTH_SHORT).show()
			return
		}
		if (bluetoothAdapter?.isEnabled == false) {
			// 請求開啟藍牙
			請求開啟藍牙(cb)
		} else {
			cb()
		}
	}

	// 請求開啟藍牙
	private fun 請求開啟藍牙(cb:() -> Unit) {
		this.當藍牙開啟 = cb
		val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
		bluetoothRequestLauncher.launch(enableBtIntent)
	}

	// 藍牙開啟請求cb
	private var 當藍牙開啟:() -> Unit = {}
	private val bluetoothRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			當藍牙開啟()
			當藍牙開啟 = {}
		}
	}

	// 藍牙搜尋
	@SuppressLint("MissingPermission")
	private fun 藍牙搜尋() {
		if (bluetoothAdapter?.isEnabled == false) return

		Toast.makeText(requireContext(), "藍牙搜尋(指定mac)", Toast.LENGTH_SHORT).show()

		//todo
	}

	//region __________權限方法__________

	// 當權限不允許
	private fun onPermissionsNoAllow() {
		val revokedPermissions:List<String> = getPermissionsMap()
			.filterValues { isGranted -> !isGranted }
			.map { (permission, isGranted) -> permission }
		val revokedPermissionsText = """
		缺少權限: ${
			revokedPermissions
				.map { p -> p.replace(".+\\.".toRegex(), "") }
				.joinToString(", ")
		}
		請授予這些權限，以便應用程序正常運行。
		Please grant all of them for the app to function properly.
		""".trimIndent()
		binding.revokedPermissionTv.text = revokedPermissionsText
	}

	// 是否有全部權限
	private fun hasPermissions(
		context:Context = requireContext(),
		permissions:Array<String> = this.permissions,
	):Boolean = getPermissionsMap(context, permissions).all { (permission, isGranted) -> isGranted }

	// 取得權限狀態
	private fun getPermissionsMap(
		context:Context = requireContext(),
		permissions:Array<String> = this.permissions,
	):Map<String, Boolean> = permissions.associateWith {
		ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}

	//endregion
}