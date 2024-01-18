package com.wavein.gasmeter.ui.ncc

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.FragmentNccBinding
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.rd64h.*
import com.wavein.gasmeter.tools.rd64h.info.*
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import com.wavein.gasmeter.ui.bluetooth.CommEndEvent
import com.wavein.gasmeter.ui.bluetooth.CommState
import com.wavein.gasmeter.ui.bluetooth.ConnectEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@ExperimentalUnsignedTypes
class NccFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentNccBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BluetoothViewModel>()
	private var jobs:MutableList<Job> = mutableListOf()

	// adapter
	private var logItems = mutableListOf<LogMsg>()
	private lateinit var logAdapter:LogAdapter

	// cb
	private var onBluetoothOn:(() -> Unit)? = null
	private var onConnected:(() -> Unit)? = null
	private var onConnectionFailed:(() -> Unit)? = null

	override fun onDestroyView() {
		super.onDestroyView()
		jobs.forEach { it.cancel() }
		jobs.clear()
		// 防止內存洩漏
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentNccBinding.inflate(inflater, container, false)
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

		// 訂閱藍牙事件
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.connectEventFlow.asSharedFlow().collectLatest { event ->
					when (event) {
						ConnectEvent.Connecting -> addMsg("連接中...", LogMsgType.System)
						ConnectEvent.Connected -> addMsg("已連接", LogMsgType.System)
						ConnectEvent.ConnectionFailed -> {
							addMsg("設備連接失敗", LogMsgType.System)
							onConnectionFailed?.invoke()
							onConnectionFailed = null
						}

						ConnectEvent.Listening -> {}
						ConnectEvent.ConnectionLost -> addMsg("設備連接中斷", LogMsgType.System)
						is ConnectEvent.BytesSent -> {
							val sendSP = event.byteArray
							val send = RD64H.telegramConvert(sendSP, "-s-p")
							val sendText = send.toText()
							val sendSPHex = sendSP.toHex()
							val showText = "$sendText [$sendSPHex]"
							addMsg(showText, LogMsgType.Send)
						}

						is ConnectEvent.BytesReceived -> {
							val readSP = event.byteArray
							val read = RD64H.telegramConvert(readSP, "-s-p")
							val readText = read.toText()
							val readSPHex = readSP.toHex()
							val showText = "$readText [$readSPHex]"
							addMsg(showText, LogMsgType.Resp)
						}
					}
				}
			}
		}

		// 訂閱溝通狀態
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commStateFlow.asStateFlow().collectLatest { state ->
					when (state) {
						CommState.NotConnected -> {
							binding.progressBar.visibility = View.GONE
						}

						CommState.Communicating, CommState.Connecting -> {
							binding.progressBar.visibility = View.VISIBLE
						}

						CommState.ReadyCommunicate -> {
							binding.progressBar.visibility = View.GONE
							onConnected?.invoke()
							onConnected = null
						}
					}
				}
			}
		}

		// 訂閱溝通結束事件
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commEndSharedEvent.asSharedFlow().collectLatest { event ->
					when (event) {
						is CommEndEvent.Success -> {
							addMsg(event.commResult.toString(), LogMsgType.Result)
						}

						is CommEndEvent.Error -> {
							addMsg(event.commResult.toString(), LogMsgType.Error)
						}
					}
				}
			}
		}

		// 訂閱通信中 進度文字
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commTextStateFlow.asStateFlow().collectLatest {
					val text = "通信狀態🔹 ${it.title} ${it.subtitle} ${it.progress}"
					binding.commTv.text = text
				}
			}
		}

		// UI: 提示文字
		val htmlText = "顏色說明🔹 <font color='#d68b00'>系統</font> <font color='#0000ff'>傳送</font>" +
				" <font color='#4a973b'>接收</font> <font color='#ff3fa4'>分析結果</font> <font color='#ff0000'>錯誤</font>"
		binding.tipTv.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT)

		// UI: LogRv & 清空按鈕
		logItems = mutableListOf()
		logAdapter = LogAdapter(requireContext(), R.layout.item_logmsg, logItems)
		binding.logList.adapter = logAdapter
		binding.clearBtn.setOnClickListener {
			onResume()
			clearMsg()
		}

		// UI: 選擇設備按鈕
		binding.btSelectBtn.setOnClickListener {
			onResume()
			BtDialogFragment.open(requireContext())
		}
		binding.btDisconnectBtn.setOnClickListener {
			onResume()
			blVM.disconnectDevice()
		}

		// UI: 發送按鈕
		binding.sendBtn.setOnClickListener {
			onResume()
			val toSendText = binding.sendInput.editText?.text.toString()
			checkBluetoothOn { blVM.sendSingleTelegram(toSendText) }
		}

		// UI: R80個別抄表按鈕
		binding.meterInput.editText?.setText(Preference[Preference.NCC_METER_ID, "00000002306003"]) //讀取上次輸入
		binding.action1Btn.setOnClickListener {
			onResume()
			val meterId = binding.meterInput.editText?.text.toString()
			Preference[Preference.NCC_METER_ID] = meterId //紀錄本次輸入
			// [R80] 成功
			// checkBluetoothOn { blVM.sendR80Telegram(listOf(meterId)) }
			// [R87_R05+R23] 成功
			// checkBluetoothOn {
			// 	blVM.sendR87Telegram(
			// 		meterId, listOf(
			// 		 	R87Step(adr = meterId, op = "R05"), // 讀數&狀態
			// 			R87Step(adr = meterId, op = "R23"), // 五回遮斷履歷
			// 		)
			// 	)
			// }
			// [R23+R24] 成功
			// checkBluetoothOn {
			// 	blVM.sendR87Telegram(
			// 		meterId, listOf(
			// 			R87Step(adr = meterId, op = "R23"), // 五回遮斷履歷
			// 			R87Step(adr = meterId, op = "R24"), // 讀數&狀態
			// 		)
			// 	)
			// }
			// [R87_R19] 成功
			// checkBluetoothOn {
			// 	blVM.sendR87Telegram(
			// 		meterId, listOf(
			// 			R87Step(adr = meterId, op = "R19"), // 時刻
			// 		)
			// 	)
			// }
			// [R87_R16] 成功
			// checkBluetoothOn {
			// 	blVM.sendR87Telegram(
			// 		meterId, listOf(
			// 			R87Step(adr = meterId, op = "R16", securityLevel = SecurityLevel.Auth), // 表狀態
			// 		)
			// 	)
			// }
			// [R87_S16] 成功
			 checkBluetoothOn {
			 	blVM.sendR87Telegram(
			 		meterId, listOf(
			 			R87Step(adr = meterId, op = "S16", data = "B@@@@@@@@@C@IIB@D@", securityLevel = SecurityLevel.Auth), // 設定表狀態
			 		)
			 	)
			 }
		}
		// UI: R80群組抄表按鈕
		binding.action2Btn.setOnClickListener {
			onResume()
			checkBluetoothOn { blVM.sendR80Telegram(listOf("00000002306003", "00000002306005")) }
		}

		// UI: 測試按鈕
		binding.testBtn.setOnClickListener {
			val respText =
				"ZD00000002306003D87120001003030303030303032333036303033303030303030303233303630303300454>314430353030303031323838394042424043404840202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202079"
			val info = BaseInfo.get(respText, D87D05Info::class.java) as D87D05Info
			addMsg(info.text)
		}
	}

	override fun onResume() {
		super.onResume()
		// 關閉軟鍵盤
		val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(binding.sendInput.editText?.windowToken, 0)
		binding.sendInput.editText?.clearFocus()
	}

	// 藍牙請求器
	private val bluetoothRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			this.onBluetoothOn?.invoke()
			this.onBluetoothOn = null
		}
		this.onBluetoothOn = null
	}

	// 檢查藍牙是否開啟
	private fun checkBluetoothOn(onConnected:() -> Unit) {
		this.onBluetoothOn = { checkReadyCommunicate(onConnected) }
		if (!blVM.isBluetoothOn()) {
			blVM.checkBluetoothOn(bluetoothRequestLauncher)
		} else {
			onBluetoothOn?.invoke()
			onBluetoothOn = null
		}
	}

	// 檢查能不能進行通信
	private fun checkReadyCommunicate(onConnected:() -> Unit) {
		when (blVM.commStateFlow.value) {
			CommState.NotConnected -> autoConnectDevice(onConnected)
			CommState.ReadyCommunicate -> onConnected.invoke()
			CommState.Connecting -> addMsg("連接中，不可進行其他通信", LogMsgType.System)
			CommState.Communicating -> addMsg("通信中，不可進行其他通信", LogMsgType.System)
		}
	}

	// 如果有連線過的設備,直接嘗試連線, 沒有若連線失敗則開藍牙窗
	private fun autoConnectDevice(onConnected:() -> Unit) {
		if (blVM.autoConnectDeviceStateFlow.value != null) {
			this.onConnectionFailed = { BtDialogFragment.open(requireContext()) }
			this.onConnected = onConnected
			blVM.connectDevice()
		} else {
			this.onConnected = onConnected
			BtDialogFragment.open(requireContext())
		}
	}

	private fun addMsg(text:String, type:LogMsgType = LogMsgType.Send) {
		val newItem = LogMsg(text, type)
		logItems.add(newItem)
		logAdapter.notifyDataSetChanged()
		lifecycleScope.launch {
			delay(100L)
			binding.logList.setSelection(logAdapter.count - 1)
		}
	}

	private fun clearMsg() {
		logItems.clear()
		logAdapter.notifyDataSetChanged()
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


class LogAdapter(context:Context, resource:Int, groups:List<LogMsg>) : ArrayAdapter<LogMsg>(context, resource, groups) {
	override fun getView(position:Int, convertView:View?, parent:ViewGroup):View {
		val view:TextView = super.getView(position, convertView, parent) as TextView
		val logMsg:LogMsg = getItem(position)!!
		view.text = logMsg.spannable
		view.setOnClickListener {
			// 點擊複製至剪貼簿
			val clipboard:ClipboardManager? = ContextCompat.getSystemService(context, ClipboardManager::class.java)
			val clip = ClipData.newPlainText("label", view.text.toString())
			clipboard?.setPrimaryClip(clip)
			Toast.makeText(context, "已複製至剪貼簿", Toast.LENGTH_SHORT).show()
		}
		return view
	}
}

enum class LogMsgType { Send, Resp, Result, Error, System }

data class LogMsg(val text:String, val type:LogMsgType = LogMsgType.Send) {

	val color:Int
		get() = when (type) {
			LogMsgType.Send -> Color.BLUE
			LogMsgType.Resp -> Color.parseColor("#ff4a973b")
			LogMsgType.Result -> Color.parseColor("#ffff3fa4")
			LogMsgType.Error -> Color.RED
			else -> Color.parseColor("#ffd68b00")
		}
	var spannable:SpannableString

	private fun getTime():String {
		val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
		val date = Date()
		return dateFormat.format(date)
	}

	init {
		val time = getTime()
		val logText = "$time $text"
		spannable = SpannableString(logText)
		val color = ForegroundColorSpan(color)
		spannable.setSpan(color, time.length + 1, logText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
	}

}


