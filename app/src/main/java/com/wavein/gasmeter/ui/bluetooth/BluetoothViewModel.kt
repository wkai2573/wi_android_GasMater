package com.wavein.gasmeter.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavein.gasmeter.tools.RD64H
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.toHexString
import com.wavein.gasmeter.tools.toText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
class BluetoothViewModel @Inject constructor(
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

	//region 開啟藍牙相關__________________________________________________

	// is藍牙開啟
	fun isBluetoothOn():Boolean {
		return bluetoothAdapter?.isEnabled ?: false
	}

	// 藍牙未開啟提示
	private suspend fun bluetoothOffTip():Boolean {
		if (!isBluetoothOn()) {
			SharedEvent._eventFlow.emit(SharedEvent.ShowSnackbar("請開啟藍牙", SharedEvent.SnackbarColor.Error))
			return true
		}
		return false
	}

	// 檢查藍牙並請求開啟
	fun checkBluetoothOn(bluetoothRequestLauncher:ActivityResultLauncher<Intent>) = viewModelScope.launch {
		if (bluetoothAdapter == null) {
			SharedEvent._eventFlow.emit(SharedEvent.ShowSnackbar("此裝置不支援藍牙", SharedEvent.SnackbarColor.Error))
			return@launch
		}
		if (!bluetoothAdapter.isEnabled) {
			requestBluetooth(bluetoothRequestLauncher)
		}
	}

	// 請求開啟藍牙
	private fun requestBluetooth(bluetoothRequestLauncher:ActivityResultLauncher<Intent>) {
		val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
		bluetoothRequestLauncher.launch(enableBtIntent)
	}

	//endregion

	//region 藍牙相關(經典藍牙)__________________________________________________

	// 常數
	val deviceName = "RD64HGL"

	// 實例
	private var parentDeviceClient:ParentDeviceClient? = null

	// 可觀察變數
	val scanStateFlow = MutableStateFlow(ScanState.Idle)
	val scannedDeviceListStateFlow = MutableStateFlow(emptyList<BluetoothDevice>())
	var autoConnectDeviceStateFlow = MutableStateFlow<BluetoothDevice?>(null)

	// 取得已配對的RD-64H藍牙設備
	fun getBondedRD64HDevices():List<BluetoothDevice> {
		val devices:Set<BluetoothDevice> = bluetoothAdapter?.bondedDevices ?: emptySet()
		return devices.filter { it.name == deviceName }
	}

	// 開始掃描
	fun toggleDiscovery() = viewModelScope.launch {
		if (!isBluetoothOn()) {
			SharedEvent._eventFlow.emit(SharedEvent.ShowSnackbar("請開啟藍牙", SharedEvent.SnackbarColor.Error))
			return@launch
		}
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return@launch
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

	// 選定為自動連結的設備
	private fun setAutoConnectBluetoothDevice(device:BluetoothDevice) {
		autoConnectDeviceStateFlow.value = device
	}

	// 連接藍牙設備
	fun connectDevice(device:BluetoothDevice? = autoConnectDeviceStateFlow.value) = viewModelScope.launch {
		if (bluetoothOffTip()) return@launch
		if (device == null) return@launch
		setAutoConnectBluetoothDevice(device)
		parentDeviceClient = ParentDeviceClient(device)
		parentDeviceClient?.start()
	}

	// 中斷連線
	fun disconnectDevice() {
		parentDeviceClient?.stopSocket()
		parentDeviceClient = null
		transceiver = null
		commStateFlow.value = CommState.NotConnected
		commTextStateFlow.value = "未連結設備"
	}

	//endregion

	//region 藍牙資料收發__________________________________________________

	// 常數
	private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

	// 實例
	var transceiver:ParentDeviceTransceiver? = null

	// 可觀察事件
	val connectEventFlow = MutableSharedFlow<ConnectEvent>()

	// 藍牙連線事件
	fun onConnectEvent(event:ConnectEvent) = viewModelScope.launch {
		when (event) {
			ConnectEvent.Connecting -> {
				connectEventFlow.emit(ConnectEvent.Connecting)
				commStateFlow.value = CommState.Connecting
				commTextStateFlow.value = "設備連結中"
			}

			ConnectEvent.ConnectionFailed -> {
				connectEventFlow.emit(ConnectEvent.ConnectionFailed)
				disconnectDevice()
			}

			ConnectEvent.ConnectionLost -> {
				connectEventFlow.emit(ConnectEvent.ConnectionLost)
				disconnectDevice()
			}

			ConnectEvent.Connected -> {
				connectEventFlow.emit(ConnectEvent.Connected)
				commStateFlow.value = CommState.ReadyCommunicate
				commTextStateFlow.value = "設備已連結"
			}

			ConnectEvent.Listening -> {
				connectEventFlow.emit(ConnectEvent.Listening)
			}

			is ConnectEvent.BytesSent -> connectEventFlow.emit(event)
			is ConnectEvent.BytesReceived -> {
				connectEventFlow.emit(event)
				val readSP = event.byteArray
				onReceiveByStep(readSP)
			}
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

		fun stopSocket() {
			socket?.close()
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
				onConnectEvent(ConnectEvent.BytesSent(bytes))
			} catch (e:IOException) {
				e.printStackTrace()
			}
		}
	}
	//endregion

	//region 傳送接收訊息邏輯&UI層處理__________________________________________________

	// 發送Text給母機
	fun sendTextToDevice(toSendText:String) {
		if (toSendText.isEmpty()) return
		if (transceiver == null) return
		val sendSP = RD64H.telegramConvert(toSendText, "+s+p")
		transceiver?.write(sendSP)
	}

	// 相關屬性
	val commStateFlow = MutableStateFlow<CommState>(CommState.NotConnected)
	val commTextStateFlow = MutableStateFlow("未連結設備")
	val commEndSharedEvent = MutableSharedFlow<CommEndEvent>()
	var commResult:MutableMap<String, RD64H.BaseInfo> = mutableMapOf()

	var sendSteps = mutableListOf<RD64H.BaseStep>()
	var receiveSteps = mutableListOf<RD64H.BaseStep>()
	var totalStep = 0

	// 溝通結束處理
	private fun onCommEnd() = viewModelScope.launch {
		if (commResult.containsKey("Error")) {
			commEndSharedEvent.emit(CommEndEvent.Error(commResult))
		} else {
			commEndSharedEvent.emit(CommEndEvent.Success(commResult))
		}
		commStateFlow.value = CommState.ReadyCommunicate
		commTextStateFlow.value = "通信完畢"
	}

	// 發送自訂電文組合
	fun sendSingleTelegram(toSendText:String) = viewModelScope.launch {
		if (commStateFlow.value != CommState.ReadyCommunicate) return@launch
		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf()

		sendSteps = mutableListOf(
			RD64H.SingleSendStep(toSendText),
			RD64H.__AStep()
		)
		receiveSteps = mutableListOf(
			RD64H.SingleRespStep()
		)
		totalStep = receiveSteps.size
		sendByStep()
	}

	// 發送R80電文組合
	fun sendR80Telegram(meterIds:List<String>) = viewModelScope.launch {
		if (commStateFlow.value != CommState.ReadyCommunicate) return@launch
		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf()

		sendSteps = mutableListOf(
			RD64H.__5Step(),
			RD64H.R80Step(meterIds),
			RD64H.__AStep(),
		)
		receiveSteps = mutableListOf(
			RD64H.D70Step(),
			RD64H.D05Step(meterIds.size),
		)
		totalStep = receiveSteps.size
		sendByStep()
	}

	// 依步驟發送電文
	private suspend fun sendByStep() {
		if (sendSteps.isEmpty()) {
			onCommEnd()
			return
		}
		when (val sendStep = sendSteps.removeAt(0)) {
			is RD64H.SingleSendStep -> {
				val sendText = sendStep.text
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				commTextStateFlow.value = "通信中: $sendText [${sendSP.toHexString()}]"
				transceiver?.write(sendSP)
			}

			is RD64H.__5Step -> {
				commTextStateFlow.value = "通信中:5↔D70 (${receiveSteps.size} / $totalStep)"
				val sendSP = RD64H.telegramConvert("5", "+s+p")
				transceiver?.write(sendSP)
			}

			is RD64H.R80Step -> {
				commTextStateFlow.value = "通信中:R80↔D05 (${receiveSteps.size} / $totalStep)"
				val btParentId = (commResult["D70"] as RD64H.D70Info).btParentId
				val sendText = RD64H.createR80Text(btParentId, sendStep.meterIds)
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				transceiver?.write(sendSP)
			}

			is RD64H.__AStep -> {
				val sendSP = RD64H.telegramConvert("A", "+s+p")
				transceiver?.write(sendSP)
				delay(1000)
				onCommEnd()
			}

			else -> {
				throw Error("奇怪的OP: $sendStep")
			}
		}
	}

	// 依步驟接收電文
	private fun onReceiveByStep(readSP:ByteArray) = viewModelScope.launch {
		try {
			if (receiveSteps.isEmpty()) throw Exception("無receiveSteps")
			var continueSend = false
			val receiveStep = receiveSteps[0]
			val read = RD64H.telegramConvert(readSP, "-s-p")
			val respText = read.toText()

			when (receiveStep) {
				is RD64H.SingleRespStep -> {
					commResult["single"] = RD64H.BaseInfo(respText)
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is RD64H.D70Step -> {
					when (val info = RD64H.getInfo(respText, RD64H.D70Info::class.java)) {
						is RD64H.D70Info -> {
							commResult["D70"] = info
							receiveSteps.removeAt(0)
							continueSend = true
						}

						else -> exceptionInfoHandle(info, respText)
					}
				}

				is RD64H.D05Step -> {
					when (val info = RD64H.getInfo(respText, RD64H.D05Info::class.java)) {
						is RD64H.D05Info -> {
							commResult["D05"] = info
							if (!commResult.containsKey("D05m")) commResult["D05m"] = RD64H.D05mInfo()
							val d05InfoList = (commResult["D05m"] as RD64H.D05mInfo).list
							d05InfoList.add(info)
							if (d05InfoList.size == receiveStep.count) {
								receiveSteps.removeAt(0)
								continueSend = true
							}
						}

						else -> exceptionInfoHandle(info, respText)
					}
				}
			}

			if (continueSend) sendByStep()
		} catch (error:Exception) {
			error.printStackTrace()
			errHandle(error.message ?: "")
		}
	}

	// 異常回傳結果(Info)處理
	private fun exceptionInfoHandle(info:RD64H.BaseInfo, respText:String) {
		if (info is RD64H.D16Info) {
			commResult["D16"] = info
			throw Error("D16")
		}
		throw Error("異常接收訊息錯誤: $respText")
	}

	// 錯誤處理
	private fun errHandle(respText:String) {
		commResult["Error"] = RD64H.BaseInfo(respText)
		onCommEnd()
	}

	//endregion
}

// 掃瞄狀態
enum class ScanState { Idle, Scanning, Error }

// 連接事件
sealed class ConnectEvent {
	object Listening : ConnectEvent()
	object Connecting : ConnectEvent()
	object ConnectionFailed : ConnectEvent()
	object Connected : ConnectEvent()
	object ConnectionLost : ConnectEvent()
	data class BytesSent(val byteArray:ByteArray) : ConnectEvent()
	data class BytesReceived(val byteArray:ByteArray) : ConnectEvent()
}

// 溝通狀態
sealed class CommState {
	object NotConnected : CommState()
	object Connecting : CommState()
	object ReadyCommunicate : CommState()
	object Communicating : CommState()
}

// 溝通結束事件
sealed class CommEndEvent {
	data class Success(val commResult:Map<String, Any>) : CommEndEvent()
	data class Error(val commResult:Map<String, Any>) : CommEndEvent()
}