package com.wavein.gasmater.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmater.databinding.FragmentMainBinding
import com.wavein.gasmater.tools.MESSAGE_READ
import com.wavein.gasmater.tools.MESSAGE_TOAST
import com.wavein.gasmater.tools.MESSAGE_WRITE
import com.wavein.gasmater.tools.MyBluetoothService
import java.util.UUID
import kotlin.experimental.xor

@SuppressLint("MissingPermission")
class MainFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMainBinding? = null
	private val binding get() = _binding!!
	private val mainVM by activityViewModels<MainViewModel>()

	// 權限
	private val permissions = arrayOf(
		Manifest.permission.BLUETOOTH_CONNECT,
		Manifest.permission.BLUETOOTH_SCAN,
		Manifest.permission.ACCESS_FINE_LOCATION
	)
	private val requestPermissionLauncher:ActivityResultLauncher<Array<String>> by lazy {
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
			if (permissionsMap.all { (permission, isGranted) -> isGranted }) {
				onPermissionsAllow()
			} else {
				onPermissionsNoAllow()
			}
		}
	}

	// 藍芽設備 & 連線
	private var device:BluetoothDevice? = null
	private var bluetoothService:MyBluetoothService? = null
	private var socketThread:MyBluetoothService.ConnectedThread? = null

	// 藍牙adapter
	private val bluetoothManager:BluetoothManager by lazy { requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
	private val bluetoothAdapter:BluetoothAdapter by lazy { bluetoothManager.adapter }

	// 電文參數
	private val STX = 0x02.toByte()
	private val ETX = 0x03.toByte()
	private fun getBcc(bytes:ByteArray):Byte { // 取得BCC(傳送bytes的最後驗證碼)
		var bcc:Byte = 0
		for (byte in bytes) bcc = bcc xor byte
		return bcc
	}

	// 防止內存洩漏
	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
		kotlin.runCatching {
			requireContext().unregisterReceiver(receiver)
		}
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMainBinding.inflate(inflater, container, false)
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

		binding.button.setOnClickListener { 檢查藍牙開啟 { 藍牙搜尋() } }
		binding.button2.setOnClickListener { 藍牙配對() }
		binding.button3.setOnClickListener { 顯示已配對的藍牙裝置() }
		binding.button4.setOnClickListener { 檢查配對Azbil母機() }
		binding.button5.setOnClickListener {
			檢查配對Azbil母機()
			連接母機()
		}
		binding.button6.setOnClickListener {
			傳送並接收訊息()
		}

		// 註冊廣播:偵測藍牙配對
		val intentFilter = IntentFilter().apply {
			addAction("android.bluetooth.devicepicker.action.DEVICE_SELECTED")
//			addAction(BluetoothCommands.STATE_CONNECTING)
//			addAction(BluetoothCommands.STATE_CONNECTED)
//			addAction(BluetoothCommands.STATE_DISCONNECTED)
			addAction(BluetoothDevice.ACTION_FOUND)
			addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
		}
		requireContext().registerReceiver(receiver, intentFilter)
	}

	// 藍牙廣播接收
	private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context:Context, intent:Intent) {
			when (intent.action) {
				// 選擇藍牙設備
				"android.bluetooth.devicepicker.action.DEVICE_SELECTED" -> {
					device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
					顯示藍牙資訊()
				}
				// 當藍牙配對改變
				BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
					Log.i("@@@", "ACTION_BOND_STATE_CHANGED")
					檢查配對Azbil母機()
				}

				BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
					Log.i("@@@", "ACTION_DISCOVERY_STARTED")
					binding.scanStateTv.text = "掃描中..."
				}

				BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
					Log.i("@@@", "ACTION_DISCOVERY_FINISHED")
					binding.scanStateTv.text = "掃描結束"
				}

				BluetoothDevice.ACTION_FOUND -> {
					Log.i("@@@", "ACTION_FOUND")
					val scannedDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
					顯示藍牙資訊(scannedDevice)
					if (scannedDevice?.name == "MBH7BTZ43PANA") {
						bluetoothAdapter.cancelDiscovery()
						device = scannedDevice
						顯示藍牙資訊()
					}
				}
			}
		}
	}

	// 檢查藍牙開啟
	private fun 檢查藍牙開啟(cb:() -> Unit) {
		if (bluetoothAdapter == null) {
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

	// 藍牙搜尋
	private fun 藍牙搜尋() {
		if (!bluetoothAdapter.isEnabled) return

		if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
		val scaning = bluetoothAdapter.startDiscovery()
		if (!scaning) binding.devicesTv.text = "無法掃描"
	}

	private fun 藍牙配對() {
		if (device == null) return
		device?.createBond()
	}

	private fun 顯示已配對的藍牙裝置() {
		if (bluetoothAdapter?.isEnabled == false) return

		val devices:Set<BluetoothDevice> = bluetoothAdapter?.bondedDevices!!
		binding.devicesTv.text = devices.joinToString("\n") { "${it.name} ${it.address}" }
	}

	private fun 檢查配對Azbil母機() {
		val devices:Set<BluetoothDevice> = bluetoothAdapter?.bondedDevices!!
		val foundDevice = devices.find { it.name == "MBH7BTZ43PANA" }
		this.device = foundDevice
		顯示藍牙資訊()
	}

	private fun 顯示藍牙資訊(_device:BluetoothDevice? = device) {
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
		if (socketThread != null) {
			socketThread?.cancel()
			socketThread = null
			return
		}

		Snackbar.make(binding.root, "嘗試連接母機", Snackbar.LENGTH_SHORT).show()
		val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
		Thread {
			val socket = device!!.createInsecureRfcommSocketToServiceRecord(uuid)
			val clazz = socket.remoteDevice.javaClass
			val paramTypes = arrayOf<Class<*>>(Integer.TYPE)
			val m = clazz.getMethod("createRfcommSocket", *paramTypes)
			val fallbackSocket = m.invoke(socket.remoteDevice, Integer.valueOf(1)) as BluetoothSocket
			try {
				fallbackSocket.connect()
				bluetoothService = MyBluetoothService(handler)
				socketThread = bluetoothService?.ConnectedThread(fallbackSocket)
				socketThread?.start()
				Snackbar.make(binding.root, "已與母機建立連接", Snackbar.LENGTH_SHORT).show()
				// 寫入
//				var outputStream = fallbackSocket.outputStream
//				outputStream.write(text.toByteArray(Charset.forName("UTF-8")))
//				Snackbar.make(binding.root, "成功連接母機並傳送資料?", Snackbar.LENGTH_SHORT).show()
//				// 讀取
//				val buffer = ByteArray(256)
//				val bytes = fallbackSocket.inputStream.read(buffer)
//				val readMessage:String = String(buffer, 0, bytes)
//				Snackbar.make(binding.root, "成功連接母機並取得資料? $readMessage", Snackbar.LENGTH_SHORT).show()
			} catch (e:Exception) {
				e.printStackTrace()
				Snackbar.make(binding.root, "連接母機失敗 An error occurred", Snackbar.LENGTH_SHORT).show()
			}
		}.start()
	}

	private fun 傳送並接收訊息() {
		if (socketThread == null) return

		val text = binding.sendEt.editableText.toString()
		val asciiCode = text.toCharArray().getOrElse(0) { '0' }.code.toByte()
		var byteArray = byteArrayOf(STX, asciiCode, ETX)
		byteArray += getBcc(byteArray)
		socketThread!!.write(byteArray)
	}

	// 藍牙傳送接收處理
//	var handler:Handler? = Handler { msg ->
//		when (msg.what) {
//			STATE_LISTENING -> status.setText("Listening")
//			STATE_CONNECTING -> status.setText("Connecting")
//			STATE_CONNECTED -> status.setText("Connected")
//			STATE_CONNECTION_FAILED -> status.setText("Connection Failed")
//			STATE_MESSAGE_RECEIVED -> {
//				val readBuff = msg.obj as ByteArray
//				val tempMsg = String(readBuff, 0, msg.arg1)
//				msg_box.setText(tempMsg)
//			}
//		}
//		true
//	}

	val handler:Handler = Handler(Looper.getMainLooper()) { msg ->
		when (msg.what) {
			MESSAGE_TOAST -> {
				val msg = "吐司: ${msg.data}"
				Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
			}

			MESSAGE_WRITE -> {
				val msg = "傳送: ${msg.arg1} ${(msg.obj as ByteArray).joinToString(",") { "0x%02x".format(it) }}"
				Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
			}

			MESSAGE_READ -> {
				val msg = "接收: ${msg.arg1} ${msg.obj} ${msg.data}"
				Log.i("@@@", msg)
			}
		}
		true
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