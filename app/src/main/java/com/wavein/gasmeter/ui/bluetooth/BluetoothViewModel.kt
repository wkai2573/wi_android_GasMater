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
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.rd64h.*
import com.wavein.gasmeter.tools.rd64h.info.*
import com.wavein.gasmeter.ui.loading.Tip
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
@OptIn(ExperimentalUnsignedTypes::class)
class BluetoothViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	private val bluetoothAdapter:BluetoothAdapter?,
) : ViewModel() {

	init {
		if (bluetoothAdapter == null) {
			viewModelScope.launch {
				SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("此裝置不支援藍牙", SharedEvent.Color.Error))
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
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("請開啟藍牙", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
			return true
		}
		return false
	}

	// 檢查藍牙並請求開啟
	fun checkBluetoothOn(bluetoothRequestLauncher:ActivityResultLauncher<Intent>) = viewModelScope.launch {
		if (bluetoothAdapter == null) {
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("此裝置不支援藍牙", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
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
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("請開啟藍牙", SharedEvent.Color.Error))
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
	fun setAutoConnectBluetoothDevice(device:BluetoothDevice) {
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
		commTextStateFlow.value = Tip("未連結設備")
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
				commTextStateFlow.value = Tip("設備連結中")
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
				commTextStateFlow.value = Tip("設備已連結")
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
	val commTextStateFlow = MutableStateFlow(Tip("未連結設備"))
	val commEndSharedEvent = MutableSharedFlow<CommEndEvent>()
	var commResult:MutableMap<String, BaseInfo> = mutableMapOf()

	var sendSteps = mutableListOf<BaseStep>()
	var receiveSteps = mutableListOf<BaseStep>()
	var totalReceiveCount = 0
	var receivedCount = 0

	// 溝通結束處理
	private fun onCommEnd() = viewModelScope.launch {
		if (commResult.containsKey("Error")) {
			commEndSharedEvent.emit(CommEndEvent.Error(commResult))
		} else {
			commEndSharedEvent.emit(CommEndEvent.Success(commResult))
		}
		commStateFlow.value = CommState.ReadyCommunicate
		commTextStateFlow.value = Tip("通信完畢")
	}

	// 發送自訂電文組合
	fun sendSingleTelegram(toSendText:String) = viewModelScope.launch {
		if (commStateFlow.value != CommState.ReadyCommunicate) return@launch
		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf()

		sendSteps = mutableListOf(
			RTestStep(toSendText),
			__AStep()
		)
		receiveSteps = mutableListOf(
			DTestStep()
		)
		totalReceiveCount = 1
		receivedCount = 0
		sendByStep()
	}

	// 發送R80電文組合
	fun sendR80Telegram(meterIds:List<String>) = viewModelScope.launch {
		if (commStateFlow.value != CommState.ReadyCommunicate) return@launch
		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf()

		sendSteps = mutableListOf(
			__5Step(),
			R80Step(meterIds),
			__AStep(),
		)
		receiveSteps = mutableListOf(
			D70Step(),
			D05Step(meterIds.size),
		)
		totalReceiveCount = 1 + meterIds.size
		receivedCount = 0
		sendByStep()
	}

	// 發送R87電文組合
	fun sendR87Telegram(meterId:String, r87Steps:List<R87Step>) = viewModelScope.launch {
		if (commStateFlow.value != CommState.ReadyCommunicate) return@launch
		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf()

		sendSteps = mutableListOf(
			__5Step(),
			R89Step(meterId),
			//如果需分割, 要在下方補n個 R70
			*r87Steps.flatMapIndexed { index, step ->
				when (step.op) {
					// 2分割
					"R23" -> listOf(step.copy(cc = "\u0021\u0040${index.toChar()}\u0000"), R70Step(step.adr))
					// 無分割
					else -> listOf(step.copy(cc = "\u0021\u0040${index.toChar()}\u0000"))
				}
			}.toTypedArray(),
			__AStep(),
		)
		receiveSteps = mutableListOf(
			D70Step(),
			D36Step(),
			*r87Steps.flatMapIndexed { index, step ->
				when (step.op) {
					"R01" -> listOf(D87D01Step())
					"R05" -> listOf(D87D05Step())
					"R23" -> listOf(D87D23Step(part = 1), D87D23Step(part = 2))
					else -> listOf()
				}
			}.toTypedArray()
		)
		totalReceiveCount = receiveSteps.size
		receivedCount = 0
		sendByStep()
	}

	// 依步驟發送電文 !!!電文處理中途
	private suspend fun sendByStep() {
		if (sendSteps.isEmpty()) {
			onCommEnd()
			return
		}
		when (val sendStep = sendSteps.removeAt(0)) {
			is RTestStep -> {
				val sendText = sendStep.text
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				commTextStateFlow.value = Tip("通信中: $sendText [${sendSP.toHexString()}]")
				wt134()
				transceiver?.write(sendSP)
			}

			is __5Step -> {
				commTextStateFlow.value = Tip("正在與母機建立連結", "5↔D70", progressText)
				val sendSP = RD64H.telegramConvert("5", "+s+p")
				wt134()
				transceiver?.write(sendSP)
			}

			is R80Step -> {
				commTextStateFlow.value = Tip("群組抄表中", "R80↔D05", progressText)
				val btParentId = (commResult["D70"] as D70Info).btParentId
				val sendText = RD64H.createR80Text(btParentId, sendStep.meterIds)
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				wt134()
				transceiver?.write(sendSP)
			}

			is __AStep -> {
				val sendSP = RD64H.telegramConvert("A", "+s+p")
				wt2()
				transceiver?.write(sendSP)
				delay(1000L)
				onCommEnd()
			}

			is R89Step -> {
				commTextStateFlow.value = Tip("正在要求設定", "R89↔D36", progressText)
				val sendText = "ZA${sendStep.meterId}R8966ZD${sendStep.meterId}R36"
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				wt134()
				transceiver?.write(sendSP)
			}

			is R87Step -> {
				when (sendStep.op) {
					"R01" -> commTextStateFlow.value = Tip("正在取得讀數", "R87R01", progressText)
					"R05" -> commTextStateFlow.value = Tip("正在取得讀數", "R87R05", progressText)
					"R23" -> commTextStateFlow.value = Tip("正在取得五回遮斷履歷", "R87R23", progressText)
				}
				val r87 = "ZD${sendStep.adr}R87"
				val aLine = RD64H.createR87Aline(cc = sendStep.cc, adr = sendStep.adr, op = sendStep.op, data = sendStep.data)
				val sendText = r87 + aLine.toByteArray().toText()
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				wt2()
				transceiver?.write(sendSP)
			}

			is R70Step -> {
				commTextStateFlow.value = commTextStateFlow.value.copy(subtitle = "R70", progress = progressText)
				val sendText = "ZD${sendStep.meterId}R70"
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				wt2()
				transceiver?.write(sendSP)
			}

			else -> {
				throw Exception("例外的OP: $sendStep")
			}
		}
	}

	private suspend inline fun wt134() {
		delay(1000L)
	}

	private suspend inline fun wt2() {
		delay(3500L)
	}

	// 依步驟接收電文 !!!電文處理中途
	private fun onReceiveByStep(readSP:ByteArray) = viewModelScope.launch {
		receivedCount++
		if (receiveSteps.isEmpty()) throw Exception("無receiveSteps")
		var continueSend = false
		val receiveStep = receiveSteps[0]
		val read = RD64H.telegramConvert(readSP, "-s-p")
		val respText = read.toText()

		try {
			when (receiveStep) {
				is DTestStep -> {
					commResult["single"] = BaseInfo(respText)
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is D70Step -> {
					val info = BaseInfo.get(respText, D70Info::class.java) as D70Info
					commResult["D70"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is D05Step -> {
					commTextStateFlow.value = commTextStateFlow.value.copy(progress = progressText)
					val info = BaseInfo.get(respText, D05Info::class.java) as D05Info
					commResult["D05"] = info
					if (!commResult.containsKey("D05m")) commResult["D05m"] = D05mInfo()
					val d05InfoList = (commResult["D05m"] as D05mInfo).list
					d05InfoList.add(info)
					if (d05InfoList.size == receiveStep.count) {
						receiveSteps.removeAt(0)
						continueSend = true
					}
				}

				is D36Step -> {
					val info = BaseInfo.get(respText, D36Info::class.java) as D36Info
					commResult["D36"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is D87D01Step -> {
					val info = BaseInfo.get(respText, D87D01Info::class.java) as D87D01Info
					commResult["D87D01"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is D87D05Step -> {
					val info = BaseInfo.get(respText, D87D05Info::class.java) as D87D05Info
					commResult["D87D05"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is D87D23Step -> {
					val info = (
							if (commResult.containsKey("D87D23")) commResult["D87D23"]
							else BaseInfo.get(respText, D87D23Info::class.java)
							) as D87D23Info
					info.writePart(respText)
					commResult["D87D23"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}
			}

			if (continueSend) sendByStep()
		} catch (error:Exception) {
			error.printStackTrace()
			// 錯誤處理: 檢查是不是D16
			kotlin.runCatching {
				val info = BaseInfo.get(respText, D16Info::class.java) as D16Info
				commResult["D16"] = info
			}
			commResult["Error"] = BaseInfo(respText)
			onCommEnd()
		}
	}

	// 進度文字
	private val progressText:String
		get() {
			val percentage = (receivedCount.toDouble() / totalReceiveCount * 100).toInt()
			return "$percentage%"
		}

	//endregion
}

// 掃描狀態
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