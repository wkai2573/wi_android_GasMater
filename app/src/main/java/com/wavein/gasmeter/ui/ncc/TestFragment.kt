package com.wavein.gasmeter.ui.ncc

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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.databinding.FragmentTestBinding
import com.wavein.gasmeter.tools.rd64h.RD64H
import com.wavein.gasmeter.tools.rd64h.toHex
import com.wavein.gasmeter.tools.rd64h.toText
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.experimental.xor

@SuppressLint("MissingPermission")
class TestFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentTestBinding? = null
	private val binding get() = _binding!!

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
			binding.permission.requestBtn.setOnClickListener {
				requestPermissionLauncher.launch(permissions)
			}
			requestPermissionLauncher.launch(permissions)
		}
	}

	// 當權限皆允許
	private fun onPermissionsAllow() {
		binding.permission.layout.visibility = View.GONE

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
		binding.button7.setOnClickListener { binding.sendInput.editText?.setText("5") }                       // ZA00000000000000D70
		binding.button8.setOnClickListener { binding.sendInput.editText?.setText("A") }
		binding.button9.setOnClickListener {
			binding.sendInput.editText?.setText(
				"ZA00000000000101R85125"
			)
		}  // G200000000000101D05000000101@@@BA@@3G13AB@I (S-18)
		binding.button10.setOnClickListener {
			binding.sendInput.editText?.setText(
				"ZA00000000000101R16"
			)
		}    // ZA00000000000000D16@@9  アラーム情報(S-14頁)
		binding.button11.setOnClickListener {
			binding.sendInput.editText?.setText(
				"ZA00000000000000R84121000000000000101????00000000000102????00000000000103????00000000000104????00000000000105????00000000000106????00000000000107????00000000000108????00000000000109????00000000000110????"
			)
		}

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

	//region __________藍牙搜尋/配對__________

	// 藍牙設備
	private var btDevice:BluetoothDevice? = null

	// 藍牙adapter
	private val bluetoothManager:BluetoothManager by lazy {
		requireContext().getSystemService(
			Context.BLUETOOTH_SERVICE
		) as BluetoothManager
	}
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
		binding.stateTv.text = "連接母機中"
		val clientClass = ClientClass(btDevice!!)
		clientClass.start()
	}

	private fun 傳送並接收訊息() {
//		if (sendReceive == null) return
		val sendText = binding.sendInput.editText?.editableText.toString()
		if (sendText.isEmpty()) return

		binding.msgTv.text = "🔽接收到的訊息🔽"
		val sendSP = RD64H.telegramConvert(sendText, "+s+p")
		sendReceive?.write(sendSP)


		val msg = "傳送: $sendText [${sendSP.toHex()}]"
		Snackbar.make(requireContext(), binding.root, msg, Snackbar.LENGTH_SHORT).show()


		// 關閉軟鍵盤
		val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(binding.sendInput.editText?.windowToken, 0)
	}

	//endregion


	//region __________藍牙連接 & 資料傳送/接收處理__________
	// 藍牙連接參考 https://www.youtube.com/watch?v=5M4o5dGigbY&ab_channel=SarthiTechnology

	// ==藍牙連線參數==
	// 舊母機:MBH7BTZ43PANA  ADDR:E0:18:77:FC:F1:5C  PIN:5678
//	private val DEVICE_NAME:String = "MBH7BTZ43PANA"
//	private val DEVICE_ADDR:String = "E0:18:77:FC:F1:5C"
	// 新母機:RD64HGL        ADDR:E8:EB:1B:6E:49:47  PIN:5893
	private val DEVICE_NAME:String = "RD64HGL"
	private val DEVICE_ADDR:String = "E8:EB:1B:6E:49:47"

	// 藍牙連線
	// private val MY_UUID = UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66")
	private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
	var sendReceive:SendReceive? = null

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

	val STATE_LISTENING = 1
	val STATE_CONNECTING = 2
	val STATE_CONNECTED = 3
	val STATE_CONNECTION_FAILED = 4
	val STATE_MESSAGE_RECEIVED = 5

	// 藍牙連線與資料處理
	var bluetoothHandler:Handler = Handler(Looper.getMainLooper()) { msg ->
		when (msg.what) {
			STATE_LISTENING -> binding.stateTv.text = "Listening"
			STATE_CONNECTING -> binding.stateTv.text = "Connecting"
			STATE_CONNECTED -> binding.stateTv.text = "Connected"
			STATE_CONNECTION_FAILED -> binding.stateTv.text = "Connection Failed"
			STATE_MESSAGE_RECEIVED -> {
				val readSP = (msg.obj as ByteArray).copyOfRange(0, msg.arg1)
				val read = RD64H.telegramConvert(readSP, "-s-p")
				val readText = read.toText()
				val readSPHex = readSP.toHex()
				binding.msgTv.text = "${binding.msgTv.text}\n$readText [$readSPHex]"
			}
		}
		true
	}

	// 連接device用
	private inner class ClientClass(private val device:BluetoothDevice) : Thread() {
		private var socket:BluetoothSocket? = null

		init {
			try {
				socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
			} catch (e:IOException) {
				e.printStackTrace()
			}
		}

		override fun run() {
			try {
				socket!!.connect()
				val message:Message = Message.obtain()
				message.what = STATE_CONNECTED
				bluetoothHandler.sendMessage(message)
				sendReceive = SendReceive(socket)
				sendReceive!!.start()
			} catch (e:IOException) {
				e.printStackTrace()
				val message:Message = Message.obtain()
				message.what = STATE_CONNECTION_FAILED
				bluetoothHandler.sendMessage(message)
			}
		}
	}

	// 傳送與接收資料用
	inner class SendReceive(private val bluetoothSocket:BluetoothSocket?) : Thread() {
		private val inputStream:InputStream?
		private val outputStream:OutputStream?

		init {
			var tempIn:InputStream? = null
			var tempOut:OutputStream? = null
			try {
				tempIn = bluetoothSocket!!.inputStream
				tempOut = bluetoothSocket.outputStream
			} catch (e:IOException) {
				e.printStackTrace()
			}
			inputStream = tempIn
			outputStream = tempOut
		}

		override fun run() {
			while (true) {
				val singleBuffer = ByteArray(320)
				try {
					val length = inputStream!!.read(singleBuffer)
					if (length != -1) {
						bluetoothHandler.obtainMessage(STATE_MESSAGE_RECEIVED, length, -1, singleBuffer).sendToTarget()
					}
				} catch (e:IOException) {
					Snackbar.make(requireContext(), binding.root, "接收資料時發生錯誤: ${e.message}", Snackbar.LENGTH_SHORT).show()
					e.printStackTrace()
					break
				}
			}
		}

		fun write(bytes:ByteArray) {
			try {
				outputStream!!.write(bytes)
			} catch (e:IOException) {
				e.printStackTrace()
			}
		}
	}

	//endregion


}