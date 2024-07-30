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


	private var _binding:FragmentTestBinding? = null
	private val binding get() = _binding!!

	override fun onDestroyView() {
		super.onDestroyView()

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

		if (hasPermissions()) {
			onPermissionsAllow()
		} else {
			binding.permission.requestBtn.setOnClickListener {
				requestPermissionLauncher.launch(permissions)
			}
			requestPermissionLauncher.launch(permissions)
		}
	}


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
		binding.button7.setOnClickListener { binding.sendInput.editText?.setText("5") }
		binding.button8.setOnClickListener { binding.sendInput.editText?.setText("A") }
		binding.button9.setOnClickListener {
			binding.sendInput.editText?.setText(
				"ZA00000000000101R85125"
			)
		}
		binding.button10.setOnClickListener {
			binding.sendInput.editText?.setText(
				"ZA00000000000101R16"
			)
		}
		binding.button11.setOnClickListener {
			binding.sendInput.editText?.setText(
				"ZA00000000000000R84121000000000000101????00000000000102????00000000000103????00000000000104????00000000000105????00000000000106????00000000000107????00000000000108????00000000000109????00000000000110????"
			)
		}


		val intentFilter = IntentFilter().apply {
			addAction("android.bluetooth.devicepicker.action.DEVICE_SELECTED")
			addAction(BluetoothDevice.ACTION_FOUND)
			addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
			addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
		}
		requireContext().registerReceiver(receiver, intentFilter)
	}



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


	private fun hasPermissions(context:Context = requireContext(), permissions:Array<String> = this.permissions):Boolean =
		getPermissionsMap(context, permissions).all { (permission, isGranted) -> isGranted }


	private fun getPermissionsMap(
		context:Context = requireContext(),
		permissions:Array<String> = this.permissions,
	):Map<String, Boolean> = permissions.associateWith {
		ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}






	private var btDevice:BluetoothDevice? = null


	private val bluetoothManager:BluetoothManager by lazy {
		requireContext().getSystemService(
			Context.BLUETOOTH_SERVICE
		) as BluetoothManager
	}
	private val bluetoothAdapter:BluetoothAdapter by lazy { bluetoothManager.adapter }


	private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context:Context, intent:Intent) {
			when (intent.action) {

				"android.bluetooth.devicepicker.action.DEVICE_SELECTED" -> {
					btDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
					顯示藍牙資訊()
				}

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


	private fun 檢查藍牙並開啟(cb:() -> Unit) {
		if (bluetoothManager == null || bluetoothManager.adapter == null) {
			Toast.makeText(requireContext(), "設備不支援藍牙", Toast.LENGTH_SHORT).show()
			return
		}
		if (!bluetoothAdapter.isEnabled) {

			請求開啟藍牙(cb)
		} else {
			cb()
		}
	}


	private fun 請求開啟藍牙(cb:() -> Unit) {
		this.當藍牙開啟 = cb
		val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
		bluetoothRequestLauncher.launch(enableBtIntent)
	}


	private var 當藍牙開啟:() -> Unit = {}
	private val bluetoothRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			當藍牙開啟()
			當藍牙開啟 = {}
		}
	}
	private var 當掃描到設備:() -> Unit = {}


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

		val sendText = binding.sendInput.editText?.editableText.toString()
		if (sendText.isEmpty()) return

		binding.msgTv.text = "🔽接收到的訊息🔽"
		val sendSP = RD64H.telegramConvert(sendText, "+s+p")
		sendReceive?.write(sendSP)


		val msg = "傳送: $sendText [${sendSP.toHex()}]"
		Snackbar.make(requireContext(), binding.root, msg, Snackbar.LENGTH_SHORT).show()



		val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(binding.sendInput.editText?.windowToken, 0)
	}












	private val DEVICE_NAME:String = "RD64HGL"
	private val DEVICE_ADDR:String = "E8:EB:1B:6E:49:47"



	private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
	var sendReceive:SendReceive? = null


	private val STX = 0x02.toByte()
	private val ETX = 0x03.toByte()
	private fun getBcc(bytes:ByteArray):Byte {
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




}