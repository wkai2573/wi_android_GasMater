package com.wavein.gasmater.ui.test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmater.databinding.FragmentTestBinding
import java.util.UUID
import kotlin.experimental.xor

@SuppressLint("MissingPermission")
class TestFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentTestBinding? = null
	private val binding get() = _binding!!
	private val testVM by activityViewModels<TestViewModel>()

	override fun onDestroyView() {
		super.onDestroyView()
		// 防止內存洩漏
		_binding = null
		kotlin.runCatching {
			requireContext().unregisterReceiver(receiver)
		}
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentTestBinding.inflate(inflater, container, false)
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

		binding.button.setOnClickListener { 檢查藍牙並開啟 { 掃描設備() } }
		binding.button2.setOnClickListener { 藍牙配對() }
		binding.button3.setOnClickListener { 顯示已配對的藍牙裝置() }
		binding.button4.setOnClickListener { 獲取已配對Azbil母機() }
		binding.button5.setOnClickListener {
			獲取已配對Azbil母機()
			if (btDevice != null) {
				連接母機()
			} else {
				檢查藍牙並開啟 { 掃描設備 { 連接母機() } }
			}
		}
		binding.button6.setOnClickListener { 傳送並接收訊息() }
		binding.button7.setOnClickListener { binding.sendEt.setText("5") }                       // ZA00000000000000D70
		binding.button8.setOnClickListener { binding.sendEt.setText("A") }
		binding.button9.setOnClickListener { binding.sendEt.setText("ZA00000000000101R85125") }  // G200000000000101D05000000101@@@BA@@3G13AB@I (S-18)
		binding.button10.setOnClickListener { binding.sendEt.setText("ZA00000000000101R16") }    // ZA00000000000000D16@@9  アラーム情報(S-14頁)
		binding.button11.setOnClickListener { binding.sendEt.setText("ZA00000000000000R84121000000000000101????00000000000102????00000000000103????00000000000104????00000000000105????00000000000106????00000000000107????00000000000108????00000000000109????00000000000110????") }

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

	//endregion

	//region __________藍牙搜尋/配對__________

	// 藍牙設備
	private var btDevice:BluetoothDevice? = null

	// 藍牙adapter
	private val bluetoothManager:BluetoothManager by lazy { requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
	private val bluetoothAdapter:BluetoothAdapter by lazy { bluetoothManager.adapter }

	// 藍牙配對處理(接收廣播)
	private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context:Context, intent:Intent) {
			when (intent.action) {
				// 選擇藍牙設備(這是直接呼叫手機預設的選藍牙來選device, 不建議)
				"android.bluetooth.devicepicker.action.DEVICE_SELECTED" -> {
					btDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
					顯示藍牙資訊()
				}
				// 當藍牙配對改變
				BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
					Log.i("@@@", "ACTION_BOND_STATE_CHANGED")
					獲取已配對Azbil母機()
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
					顯示藍牙資訊(scannedDevice)
					if (scannedDevice?.address == DEVICE_ADDR) {
						bluetoothAdapter.cancelDiscovery()
						btDevice = scannedDevice
						當掃描到設備()
						當掃描到設備 = {}
					}
				}
			}
		}
	}

	// 檢查藍牙並開啟
	private fun 檢查藍牙並開啟(cb:() -> Unit) {
		if (bluetoothManager == null || bluetoothManager.adapter == null) {
			Toast.makeText(requireContext(), "設備不支援藍牙", Toast.LENGTH_SHORT).show()
			return
		}
		if (!bluetoothAdapter.isEnabled) {
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
	private var 當掃描到設備:() -> Unit = {}

	// 掃描設備 scanForDevices
	private fun 掃描設備(cb:() -> Unit = {}) {
		if (!bluetoothAdapter.isEnabled) return

		if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
		當掃描到設備 = cb
		val scaning = bluetoothAdapter.startDiscovery()
		if (!scaning) binding.devicesTv.text = "無法掃描"
	}

	private fun 藍牙配對() {
		if (btDevice == null) return
		btDevice?.createBond()
	}

	private fun 顯示已配對的藍牙裝置() {
		if (!bluetoothAdapter.isEnabled) return

		val devices:Set<BluetoothDevice> = bluetoothAdapter.bondedDevices!!
		binding.devicesTv.text = devices.joinToString("\n") { "${it.name} ${it.address}" }
	}

	private fun 獲取已配對Azbil母機() {
		val devices:Set<BluetoothDevice> = bluetoothAdapter.bondedDevices!!
		val foundDevice = devices.find { it.name == DEVICE_NAME }
		this.btDevice = foundDevice
		顯示藍牙資訊()
	}

	private fun 顯示藍牙資訊(_device:BluetoothDevice? = btDevice) {
		if (_device == null) {
			binding.devicesTv.text = "無配對"
			return
		}
		val bondString = when (_device.bondState) {
			BluetoothDevice.BOND_NONE -> "未配對"
			BluetoothDevice.BOND_BONDING -> "配對中"
			BluetoothDevice.BOND_BONDED -> "已配對"
			else -> "不明"
		}
		binding.devicesTv.text = "已選擇_裝置:${_device?.name} mac:${_device?.address} 配對狀態:${bondString}"
	}

	private fun 連接母機() {
		if (btDevice == null) return
		binding.stateTv.text = "連接母機"
		bluetoothGatt = btDevice!!.connectGatt(requireContext(), true, gattCallback)
	}

	private fun 傳送並接收訊息() {
//		if (sendReceive == null) return
		val text = binding.sendEt.editableText.toString()
		if (text.isEmpty()) return

		binding.msgTv.text = "🔽接收到的訊息🔽"
		val asciiCodeList = text.toCharArray().map { it.code.toByte() }
		var bytes = byteArrayOf(STX) + asciiCodeList.toByteArray() + byteArrayOf(ETX)
		bytes += getBcc(bytes)
		printByteArrayAsBinary(bytes)
//		bytes = convertbitComposition(bytes)
//		printByteArrayAsBinary(bytes)
//		sendReceive!!.write(bytes)

		val msg = "傳送: " + bytes.joinToString(" ") { /*"0x%02x"*/"%d".format(it) }
		Snackbar.make(requireContext(), binding.root, msg, Snackbar.LENGTH_SHORT).show()

		// 關閉軟鍵盤
		val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(binding.sendEt.windowToken, 0)
	}

	//endregion

	// ==藍牙連線參數==
	// 舊母機:MBH7BTZ43PANA  ADDR:E0:18:77:FC:F1:5C  PIN:5678
	// 新母機:RD64HGL        ADDR:E8:EB:1B:6E:49:47  PIN:5893
	private val DEVICE_NAME:String = "RD64HGL"
	private val DEVICE_ADDR:String = "E8:EB:1B:6E:49:47"

	//region __________(新母機,GATT) 藍牙連接 & 資料傳送/接收處理__________

	private lateinit var bluetoothGatt:BluetoothGatt

	// 設定您的服務和特性 UUID
	val serviceUuid = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
	val characteristicUuid = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")

	val gattCallback = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(gatt:BluetoothGatt, status:Int, newState:Int) {
			when (newState) {
				BluetoothProfile.STATE_CONNECTED -> {
					// 成功連接，開始發現服務
					binding.stateTv.text = "STATE_CONNECTED"
					gatt.discoverServices()
				}

				BluetoothProfile.STATE_CONNECTING -> {
					binding.stateTv.text = "STATE_CONNECTING"
				}

				BluetoothProfile.STATE_DISCONNECTING -> {
					binding.stateTv.text = "STATE_DISCONNECTING"
				}

				BluetoothProfile.STATE_DISCONNECTED -> {
					binding.stateTv.text = "STATE_DISCONNECTED"
				}
			}
		}

		override fun onServicesDiscovered(gatt:BluetoothGatt, status:Int) {
			// 發現 BLE 服務後，找到目標特性並進行相應操作
			val service = gatt.getService(serviceUuid)
			val characteristic = service?.getCharacteristic(characteristicUuid)

			if (characteristic != null) {
				// 在這裡進行您的特性操作，例如讀取或寫入數據
				// gatt.readCharacteristic(targetCharacteristic)
				// gatt.writeCharacteristic(targetCharacteristic)
			}
		}
	}

	//endregion

	// 電文參數
	private val STX = 0x02.toByte()
	private val ETX = 0x03.toByte()
	private fun getBcc(bytes:ByteArray):Byte { // 取得BCC(傳送bytes的最後驗證碼)
		var bcc:Byte = 0
		if (bytes.isNotEmpty()) {
			for (i in 1 until bytes.size) {
				val byte = bytes[i]
				bcc = bcc xor byte
			}
		}
		return bcc
	}

	// 除錯: 印出byteArray二進位
	private fun printByteArrayAsBinary(byteArray:ByteArray):List<String> {
		val binaryList:MutableList<String> = mutableListOf()
		for (byte in byteArray) {
			val binaryString = Integer.toBinaryString(byte.toInt() and 0xFF)
			val paddedBinaryString = binaryString.padStart(8, '0')
			binaryList.add(paddedBinaryString)
		}
		Log.e("@@@", binaryList.toString())
		return binaryList
	}

}