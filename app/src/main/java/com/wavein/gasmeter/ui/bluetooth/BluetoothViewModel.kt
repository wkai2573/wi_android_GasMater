package com.wavein.gasmeter.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.tools.Preference
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
	private fun setAutoConnectBluetoothDevice(device:BluetoothDevice) {
		autoConnectDeviceStateFlow.value = device
	}

	// 連接藍牙設備
	fun connectDevice(device:BluetoothDevice? = autoConnectDeviceStateFlow.value) = viewModelScope.launch {
		if (bluetoothOffTip()) return@launch
		if (device == null) return@launch
		Preference[Preference.LAST_BT_DEVICE_MAC] = device.address
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
				// 如果是通信中途發生中斷, 要處理結果
				if (commStateFlow.value == CommState.Communicating) onCommEnd()
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

	private var startTime:Long = 0L // 計算耗時用

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

	// 溝通中處理變數
	var sendSteps = mutableListOf<BaseStep>()
	var receiveSteps = mutableListOf<BaseStep>()
	var totalReceiveCount = 0
	var receivedCount = 0

	// 溝通結束處理
	private fun onCommEnd() = viewModelScope.launch {
		Log.i("@@@耗時", "${elapsedTime()} 秒 (總耗時)")
		// 依組合決定通信結束後小吃文字
		val metaInfo = commResult["meta"] as MetaInfo
		when (metaInfo.op) {
			// R80 異常訊息
			"R80" -> {
				val d05mList = if (commResult.containsKey("D05m")) {
					(commResult["D05m"] as D05mInfo).list
				} else {
					emptyList()
				}
				val notReadNumber = metaInfo.meterIds.size - d05mList.size
				if (notReadNumber > 0) {
					commResult["error"] = BaseInfo("${d05mList.size}台抄表成功，${notReadNumber}台無回應\n請檢查未抄表瓦斯表")
				} else {
					commResult["success"] = BaseInfo("${d05mList.size}台抄表成功")
				}
			}

			// R87 異常訊息
			// 檢查開始時R87傳了什麼steps，如果結果沒有對應的結果op，顯示對應錯誤
			"R87" -> {
				val errList = mutableListOf<String>()
				metaInfo.r87Steps!!.forEach { step ->
					when (step.op) {
						"R19" -> if (!commResult.containsKey("D87D19")) errList.add("時刻要求")
						"R23" -> if (!commResult.containsKey("D87D23")) errList.add("五回遮斷履歷")
						"R24" -> if (!commResult.containsKey("D87D24")) errList.add("告警情報or母火流量")
						"R16" -> if (!commResult.containsKey("D87D16")) errList.add("表狀態")
						"S16" -> if (!commResult.containsKey("D87D16")) errList.add("表狀態設定")
						"R57" -> if (!commResult.containsKey("D87D57")) errList.add("時間使用量")
						"R58" -> if (!commResult.containsKey("D87D58")) errList.add("最大使用量")
						"R59" -> if (!commResult.containsKey("D87D59")) errList.add("1日最大使用量")
						"S31" -> if (!commResult.containsKey("D87D24")) errList.add("母火流量設定")
						"R50" -> if (!commResult.containsKey("D87D50")) errList.add("壓力遮斷判定值")
						"S50" -> if (!commResult.containsKey("D87D50")) errList.add("壓力遮斷判定值設定")
						"R51" -> if (!commResult.containsKey("D87D51")) errList.add("現在壓力值")
					}
				}
				if (errList.isNotEmpty()) {
					val errorType = if (commResult.containsKey("error_D16")) {
						"HHD用GW終端無回應(D16)"
					} else if (commResult.containsKey("error_D36")) {
						"對表U-bus通信異常(D36)"
					} else {
						"通信異常"
					}
					commResult["error"] = BaseInfo("$errorType，以下通信失敗：\n${errList.joinToString("\n")}")
				} else {
					commResult["success"] = BaseInfo("讀取/設定成功")
				}
			}
		}
		// 結果處理
		if (commResult.containsKey("error")) {
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
		startTime = System.currentTimeMillis()
		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf("meta" to MetaInfo("", "R80", meterIds))

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
		startTime = System.currentTimeMillis()

		commStateFlow.value = CommState.Communicating
		commResult = mutableMapOf("meta" to MetaInfo("", "R87", listOf(meterId), r87Steps))

		sendSteps = mutableListOf(
			__5Step(),
			R89Step(meterId),
			//如果需分割, 要在下方補n個 R70
			*r87Steps.flatMapIndexed { index, step ->
				when (step.op) {
					// 2part
					"R23" -> listOf(step.copy(cc = "\u0021\u0040${index.toChar()}\u0000"), R70Step(step.adr))
					// 1part
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
					"R19" -> listOf(D87D19Step())
					"R23" -> listOf(D87D23Step(), D87D23Step()) //R23有2part
					"R24" -> listOf(D87D24Step())
					"R16" -> listOf(D87D16Step())
					// todo 其他R87項目...
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
		Log.i("@@@耗時", "${elapsedTime()} 秒")
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
				commTextStateFlow.value = Tip("抄表中", "R80↔D05", progressText)
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
				commTextStateFlow.value = Tip("正在要求通信許可", "R89↔D36", progressText)
				val sendText = "ZA${sendStep.meterId}R8966ZD${sendStep.meterId}R36"
				val sendSP = RD64H.telegramConvert(sendText, "+s+p")
				wt134()
				transceiver?.write(sendSP)
			}

			is R87Step -> {
				when (sendStep.op) {
					"R01" -> commTextStateFlow.value = Tip("正在讀取讀數", "R87R01", progressText)
					"R05" -> commTextStateFlow.value = Tip("正在讀取讀數", "R87R05", progressText)
					"R19" -> commTextStateFlow.value = Tip("正在讀取時刻", "R87R19", progressText)
					"R23" -> commTextStateFlow.value = Tip("正在讀取五回遮斷履歷", "R87R23", progressText)
					"R24" -> commTextStateFlow.value = Tip("正在讀取表內部資料", "R87R24", progressText)
					"R16" -> commTextStateFlow.value = Tip("正在讀取表狀態", "R87R16", progressText)
					"S16" -> commTextStateFlow.value = Tip("正在設定表狀態", "R87S16", progressText)
					"R57" -> commTextStateFlow.value = Tip("正在讀取時間使用量", "R87R57", progressText)
					"R58" -> commTextStateFlow.value = Tip("正在讀取最大使用量", "R87R58", progressText)
					"R59" -> commTextStateFlow.value = Tip("正在讀取1日最大使用量", "R87R59", progressText)
					"S31" -> commTextStateFlow.value = Tip("正在設定登錄母火流量", "R87S31", progressText)
					"R50" -> commTextStateFlow.value = Tip("正在讀取壓力遮斷判定值", "R87R50", progressText)
					"S50" -> commTextStateFlow.value = Tip("正在設定壓力遮斷判定值", "R87S50", progressText)
					"R51" -> commTextStateFlow.value = Tip("正在讀取壓力值", "R87R51", progressText)
					"C41" -> commTextStateFlow.value = Tip("正在設定中心遮斷", "R87C41", progressText)
				}
				val r87 = "ZD${sendStep.adr}R87"
				val d19Time = if (commResult.containsKey("D87D19")) {
					(commResult["D87D19"] as D87D19Info).data
				} else {
					null
				}
				val aLine = RD64H.createR87Aline(
					securityLevel = sendStep.securityLevel, time = d19Time,
					cc = sendStep.cc, adr = sendStep.adr, op = sendStep.op, data = sendStep.data
				)
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

	// 經過時間
	private fun elapsedTime():String {
		val diffMillisecond = System.currentTimeMillis() - startTime
		val diffSecond = diffMillisecond.toFloat() / 1000
		return String.format("%.1f", diffSecond)
	}


	// 依步驟接收電文 !!!電文處理中途
	private fun onReceiveByStep(readSP:ByteArray) = viewModelScope.launch {
		receivedCount++
		if (receiveSteps.isEmpty()) {
			onCommEnd()
			return@launch
		}
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

				is D87D19Step -> {
					val info = BaseInfo.get(respText, D87D19Info::class.java) as D87D19Info
					commResult["D87D19"] = info
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

				is D87D24Step -> {
					val info = BaseInfo.get(respText, D87D24Info::class.java) as D87D24Info
					commResult["D87D24"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}

				is D87D16Step -> {
					val info = BaseInfo.get(respText, D87D16Info::class.java) as D87D16Info
					commResult["D87D16"] = info
					receiveSteps.removeAt(0)
					continueSend = true
				}

				// todo 其他R87項目...
			}

			if (continueSend) sendByStep()
		} catch (error:Exception) {
			error.printStackTrace()
			// 錯誤處理: 檢查是不是D16 或 D36
			kotlin.runCatching {
				val info = BaseInfo.get(respText, D16Info::class.java) as D16Info
				commResult["error_D16"] = info
			}
			kotlin.runCatching {
				val info = BaseInfo.get(respText, D36Info::class.java) as D36Info
				commResult["error_D36"] = info
			}
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