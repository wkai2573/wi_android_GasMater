package com.wavein.gasmater.ui.setting

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmater.tools.RD64H
import com.wavein.gasmater.tools.SharedEvent
import com.wavein.gasmater.tools.toHexString
import com.wavein.gasmater.tools.toText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@SuppressLint("MissingPermission")
class BlueToothViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	private val bluetoothAdapter:BluetoothAdapter?,
) : ViewModel() {

	init {
		if (bluetoothAdapter == null) {
			viewModelScope.launch {
				SharedEvent._eventFlow.emit(SharedEvent.ShowSnackbar("設備不支援藍牙", SharedEvent.SnackbarColor.Error))
			}
		}
	}

	// 藍牙相關(經典藍牙)__________________________________________________

	// 常數
	val deviceName = "RD64HGL"

	// 可觀察變數
	val scanStateFlow = MutableStateFlow(ScanState.Idle)
	val scannedDeviceListStateFlow = MutableStateFlow(emptyList<BluetoothDevice>())

	// 取得已配對的RD-64H藍牙設備
	fun getBondedRD64HDevices():List<BluetoothDevice> {
		val devices:Set<BluetoothDevice> = bluetoothAdapter?.bondedDevices ?: emptySet()
		return devices.filter { it.name == deviceName }
	}

	// 開始掃描
	fun toggleDiscovery() {
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
		if (bluetoothAdapter.isDiscovering) {
			stopDiscovery()
		} else {
			scanStateFlow.value = if (bluetoothAdapter.startDiscovery()) ScanState.Scanning else ScanState.Error
		}
	}

	// 停止掃描
	fun stopDiscovery() {
		bluetoothAdapter?.cancelDiscovery()
	}

	// 連接藍牙設備
	fun connectDevice(device:BluetoothDevice) {
		val clientClass = ParentDeviceClient(device)
		clientClass.start()
	}

	// 傳送Text給母機, 回傳實際傳送HEX字串
	fun sendTextToDevice(text:String):String? {
		if (text.isEmpty()) return null
		if (transceiver == null) return null
		val sendSP = RD64H.telegramConvert(text, "+s+p")
		transceiver?.write(sendSP)
		return sendSP.toHexString()
	}

	// 藍牙資料收發__________________________________________________

	// 常數 & 實例
	private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
	var transceiver:ParentDeviceTransceiver? = null

	// 可觀察事件
	val connectEventFlow = MutableSharedFlow<ConnectEvent>()

	// 藍牙連線事件
	fun onConnectEvent(event:ConnectEvent) {
		when (event) {
			ConnectEvent.Connecting -> viewModelScope.launch { connectEventFlow.emit(ConnectEvent.Connecting) }
			ConnectEvent.ConnectionFailed -> viewModelScope.launch { connectEventFlow.emit(ConnectEvent.ConnectionFailed) }
			ConnectEvent.Connected -> viewModelScope.launch { connectEventFlow.emit(ConnectEvent.Connected) }
			ConnectEvent.ConnectionLost -> viewModelScope.launch { connectEventFlow.emit(ConnectEvent.ConnectionLost) }
			ConnectEvent.Listening -> viewModelScope.launch { connectEventFlow.emit(ConnectEvent.Listening) }
			is ConnectEvent.BytesReceived -> {
				val readSP = event.byteArray
				val read = RD64H.telegramConvert(readSP, "-s-p")
				val readText = read.toText()
				val readSPHex = readSP.toHexString()
				val showText = "$readText [$readSPHex]"
				viewModelScope.launch { connectEventFlow.emit(ConnectEvent.TextReceived(showText)) }
			}

			else -> {}
		}
	}

	// 母機連接
	private inner class ParentDeviceClient(private val device:BluetoothDevice) : Thread() {
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
				onConnectEvent(ConnectEvent.Connecting)
				socket!!.connect()
				onConnectEvent(ConnectEvent.Connected)
				transceiver = ParentDeviceTransceiver(socket)
				transceiver!!.start()
			} catch (e:IOException) {
				onConnectEvent(ConnectEvent.ConnectionFailed)
				e.printStackTrace()
			}
		}
	}

	// 母機收發器
	inner class ParentDeviceTransceiver(private val bluetoothSocket:BluetoothSocket?) : Thread() {
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
				onConnectEvent(ConnectEvent.Listening)
				val singleBuffer = ByteArray(320)
				try {
					val length = inputStream!!.read(singleBuffer)
					if (length != -1) {
						val bytes = singleBuffer.copyOfRange(0, length)
						onConnectEvent(ConnectEvent.BytesReceived(bytes))
					}
				} catch (e:IOException) {
					onConnectEvent(ConnectEvent.ConnectionLost)
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


enum class ScanState { Idle, Scanning, Error }

sealed class ConnectEvent {
	object Listening : ConnectEvent()
	object Connecting : ConnectEvent()
	object ConnectionFailed : ConnectEvent()
	object Connected : ConnectEvent()
	object ConnectionLost : ConnectEvent()
	data class BytesReceived(val byteArray:ByteArray) : ConnectEvent()
	data class TextReceived(val text:String) : ConnectEvent()
}

