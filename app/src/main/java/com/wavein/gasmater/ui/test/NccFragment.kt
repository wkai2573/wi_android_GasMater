package com.wavein.gasmater.ui.test

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.FragmentNccBinding
import com.wavein.gasmater.ui.bt.BtDialogFragment
import com.wavein.gasmater.ui.setting.BlueToothViewModel
import com.wavein.gasmater.ui.setting.ConnectEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
						ConnectEvent.Listening -> addMsg("監聽中...", LogMsgType.System)
						ConnectEvent.ConnectionLost -> addMsg("連接中斷", LogMsgType.System)
						is ConnectEvent.TextReceived -> {
							addMsg(event.text, LogMsgType.Resp)
						}

						else -> {}
					}
				}
			}
		}

		// UI: 藍牙按鈕
		binding.btSelectBtn.setOnClickListener {
			val supportFragmentManager = (activity as FragmentActivity).supportFragmentManager
			val dialog = BtDialogFragment()
			dialog.show(supportFragmentManager, "BtDialogFragment")
		}

		// UI: Log相關
		logItems = mutableListOf<LogMsg>()
		logAdapter = LogAdapter(requireContext(), R.layout.item_logmsg, logItems)
		binding.logList.adapter = logAdapter
		binding.sendBtn.setOnClickListener { sendMsg(binding.sendEt.text.toString()) }
		binding.clearBtn.setOnClickListener { clearMsg() }
	}

	private fun sendMsg(text:String) {
		blVM.sendTextToDevice(text)
		val hexString = blVM.sendTextToDevice(text) ?: return
		val showText = "$text [$hexString]"
		addMsg(showText)
		// 關閉軟鍵盤
		val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(binding.sendEt.windowToken, 0)
	}

	private fun addMsg(text:String, type:LogMsgType = LogMsgType.Send) {
		val newItem = LogMsg(text, type)
		logItems.add(newItem)
		logAdapter.notifyDataSetChanged()
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

enum class LogMsgType { Send, Resp, System }

data class LogMsg(val text:String, val type:LogMsgType = LogMsgType.Send) {

	val color:Int
		get() = when (type) {
			LogMsgType.Send -> Color.BLUE
			LogMsgType.Resp -> Color.parseColor("#ff4a973b")
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


