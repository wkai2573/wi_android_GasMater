package com.wavein.gasmater.ui.test

import android.Manifest
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.FragmentNccBinding
import com.wavein.gasmater.tools.RD64H
import com.wavein.gasmater.tools.toHexString
import com.wavein.gasmater.tools.toText
import com.wavein.gasmater.ui.bt.BtDialogFragment
import com.wavein.gasmater.ui.setting.BlueToothViewModel
import com.wavein.gasmater.ui.setting.CommEndEvent
import com.wavein.gasmater.ui.setting.CommState
import com.wavein.gasmater.ui.setting.ConnectEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NccFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentNccBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BlueToothViewModel>()
	private var jobs:MutableList<Job> = mutableListOf()

	// adapter
	private var logItems = mutableListOf<LogMsg>()
	private lateinit var logAdapter:LogAdapter

	// cb
	private var onConnected:(() -> Unit)? = null

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

		// 註冊藍牙事件
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.connectEventFlow.asSharedFlow().collectLatest { event ->
					when (event) {
						ConnectEvent.Connecting -> addMsg("連接中...", LogMsgType.System)
						ConnectEvent.Connected -> addMsg("已連接", LogMsgType.System)
						ConnectEvent.ConnectionFailed -> addMsg("連接失敗", LogMsgType.System)
						ConnectEvent.Listening -> {}
						ConnectEvent.ConnectionLost -> addMsg("連接中斷", LogMsgType.System)
						is ConnectEvent.BytesSent -> {
							val sendSP = event.byteArray
							val send = RD64H.telegramConvert(sendSP, "-s-p")
							val sendText = send.toText()
							val sendSPHex = sendSP.toHexString()
							val showText = "$sendText [$sendSPHex]"
							addMsg(showText, LogMsgType.Send)
						}

						is ConnectEvent.BytesReceived -> {
							val readSP = event.byteArray
							val read = RD64H.telegramConvert(readSP, "-s-p")
							val readText = read.toText()
							val readSPHex = readSP.toHexString()
							val showText = "$readText [$readSPHex]"
							addMsg(showText, LogMsgType.Resp)
						}
					}
				}
			}
		}

		// 註冊溝通狀態
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

		// 註冊溝通結束事件
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

		//TODO 註冊通信中 進度文字
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commTextStateFlow.asStateFlow().collectLatest {
					val text = "通信狀態🔹 $it"
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
		binding.clearBtn.setOnClickListener { clearMsg() }

		// UI: 選擇設備按鈕
		binding.btSelectBtn.setOnClickListener {
			val supportFragmentManager = (activity as FragmentActivity).supportFragmentManager
			BtDialogFragment().show(supportFragmentManager, "BtDialogFragment")
			this.onConnected = null
		}
		binding.btDisconnectBtn.setOnClickListener {
			blVM.disconnectDevice()
		}

		// UI: 發送按鈕
		binding.sendBtn.setOnClickListener {
			val toSendText = binding.sendEt.text.toString()
			checkReadyCommunicate { blVM.sendSingleTelegram(toSendText) }
			// 關閉軟鍵盤
			val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
			imm?.hideSoftInputFromWindow(binding.sendEt.windowToken, 0)
		}

		// UI: R80個別抄表按鈕
		binding.action1Btn.setOnClickListener {
			checkReadyCommunicate { blVM.sendR80Telegram(listOf("00000002306003")) }
		}
		// UI: R80群組抄表按鈕
		binding.action2Btn.setOnClickListener {
			checkReadyCommunicate { blVM.sendR80Telegram(listOf("00000002306003", "00000002306004")) }
		}
	}

	// 檢查能不能進行通信
	private fun checkReadyCommunicate(onConnected:() -> Unit) {
		when (blVM.commStateFlow.value) {
			CommState.NotConnected -> {
				val supportFragmentManager = (activity as FragmentActivity).supportFragmentManager
				BtDialogFragment().show(supportFragmentManager, "BtDialogFragment")
				this.onConnected = onConnected
			}

			CommState.ReadyCommunicate -> onConnected.invoke()
			CommState.Connecting -> {
				addMsg("連接中，不可進行其他通信", LogMsgType.System)
			}

			CommState.Communicating -> {
				addMsg("通信中，不可進行其他通信", LogMsgType.System)
			}
		}
	}


	private fun addMsg(text:String, type:LogMsgType = LogMsgType.Send) {
		val newItem = LogMsg(text, type)
		logItems.add(newItem)
		logAdapter.notifyDataSetChanged()
		binding.logList.setSelection(logAdapter.count - 1)
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


