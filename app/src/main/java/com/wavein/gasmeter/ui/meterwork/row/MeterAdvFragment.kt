package com.wavein.gasmeter.ui.meterwork.row

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.databinding.FragmentMeterAdvBinding
import com.wavein.gasmeter.databinding.InputLayoutBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.rd64h.R87Step
import com.wavein.gasmeter.tools.rd64h.RD64H
import com.wavein.gasmeter.tools.rd64h.SecurityLevel
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.CommEndEvent
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.meterwork.row.detail.R03DetailSheet
import com.wavein.gasmeter.ui.meterwork.row.detail.R16DetailSheet
import com.wavein.gasmeter.ui.meterwork.row.detail.R50DetailSheet
import com.wavein.gasmeter.ui.setting.CsvViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeterAdvFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterAdvBinding? = null
	private val binding get() = _binding!!
	private val csvVM by activityViewModels<CsvViewModel>()
	private val blVM by activityViewModels<BluetoothViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()
	private val advVM by activityViewModels<MeterAdvViewModel>()

	// 實例 & 常數 & 變數
	private val meterBaseFragment:MeterBaseFragment get() = ((parentFragment as MeterRowFragment).parentFragment as MeterBaseFragment)
	private val setPassword = "1234567"
	private var r87Steps:MutableList<R87Step> = mutableListOf()
	private var estimatedTime:Int = 0
	private val checkboxes:MutableList<AppCompatCheckBox> = mutableListOf()

	override fun onDestroyView() {
		super.onDestroyView()
		SharedEvent.snackbarDefaultAnchorView = null
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterAdvBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// 更新小吃錨點
		SharedEvent.snackbarDefaultAnchorView = binding.sendFab

		// 通信按鈕
		binding.sendFab.setOnClickListener { btn ->
			if (!btn.isPressed) return@setOnClickListener
			if (r87Steps.isEmpty()) return@setOnClickListener
			refresh()
			// 視窗確認, 若steps含設定項-需要輸入密碼
			if (r87Steps.any { it.op.startsWith('S') || it.op.startsWith('C') }) {
				val inputLayoutBinding = InputLayoutBinding.inflate(LayoutInflater.from(requireContext()))
				val inputLayout = inputLayoutBinding.textInput.apply {
					hint = "請輸入設定密碼"
				}
				MaterialAlertDialogBuilder(requireContext())
					.setTitle("表進階讀取/設定")
					.setMessage("準備進行表讀取/設定\n耗時約${estimatedTime}秒")
					.setView(inputLayout)
					.setNegativeButton("取消") { dialog, which -> dialog.dismiss() }
					.setPositiveButton("確定") { dialog, which ->
						dialog.dismiss()
						val inputPw = inputLayout.editText?.text.toString()
						if (inputPw != setPassword) {
							lifecycleScope.launch {
								SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("密碼錯誤", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
							}
							return@setPositiveButton
						}
						meterBaseFragment.checkBluetoothOn { sendR87Telegram() }
					}
					.create()
					.apply {
						setOnShowListener { inputLayout.editText?.requestFocus() }
						show()
					}
			} else {
				MaterialAlertDialogBuilder(requireContext()).apply {
					setTitle("對表讀取")
					setMessage("準備對表進行讀取\n耗時約${estimatedTime}秒")
					setNeutralButton("取消") { dialog, which -> dialog.dismiss() }
					setPositiveButton("確定") { dialog, which ->
						dialog.dismiss()
						meterBaseFragment.checkBluetoothOn { sendR87Telegram() }
					}
					show()
				}
			}
		}

		// checkbox
		binding.apply {
			field23.binding?.readCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> refresh() } }
			field03.binding?.readCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> refresh() } }
			field16.binding?.readCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field16.binding?.writeCheckbox?.isChecked = false
					refresh()
				}
			}
			field16.binding?.writeCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field16.binding?.readCheckbox?.isChecked = false
					if (field16.writeValue.isEmpty()) {
						field16.binding?.writeDetailBtn?.callOnClick()
					}
					refresh()
				}
			}
			field57.binding?.readCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> if (hasMacKey(it)) refresh() } }
			field58.binding?.readCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> if (hasMacKey(it)) refresh() } }
			field59.binding?.readCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> if (hasMacKey(it)) refresh() } }
			field31.binding?.readCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field31.binding?.writeCheckbox?.isChecked = false
					refresh()
				}
			}
			field31.binding?.writeCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field31.binding?.readCheckbox?.isChecked = false
					refresh()
				}
			}
			field50.binding?.readCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field50.binding?.writeCheckbox?.isChecked = false
					refresh()
				}
			}
			field50.binding?.writeCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field50.binding?.readCheckbox?.isChecked = false
					if (field50.writeValue.isEmpty()) {
						field50.binding?.writeDetailBtn?.callOnClick()
					}
					refresh()
				}
			}
			field51.binding?.readCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> if (hasMacKey(it)) refresh() } }
			field41.binding?.writeCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field42.binding?.writeCheckbox?.isChecked = false
					refresh()
				}
			}
			field42.binding?.writeCheckbox?.let {
				it.setOnCheckedChangeListener { _, _ ->
					if (!it.isPressed) return@setOnCheckedChangeListener
					if (!hasMacKey(it)) return@setOnCheckedChangeListener
					field41.binding?.writeCheckbox?.isChecked = false
					refresh()
				}
			}
			field02.binding?.writeCheckbox?.let { it.setOnCheckedChangeListener { _, _ -> if (hasMacKey(it)) refresh() } }
			checkboxes.addAll(
				listOfNotNull(
					field23.binding?.readCheckbox,
					field03.binding?.readCheckbox,
					field16.binding?.readCheckbox, field16.binding?.writeCheckbox,
					field57.binding?.readCheckbox,
					field58.binding?.readCheckbox,
					field59.binding?.readCheckbox,
					field31.binding?.readCheckbox, field31.binding?.writeCheckbox,
					field50.binding?.readCheckbox, field50.binding?.writeCheckbox,
					field51.binding?.readCheckbox,
					field41.binding?.writeCheckbox,
					field42.binding?.writeCheckbox,
					field02.binding?.writeCheckbox,
				)
			)
		}

		// 詳細按鈕
		binding.apply {
			// R03
			field03.binding?.row1DetailBtn?.setOnClickListener {
				val alarmInfo = field03.binding?.row1AlarmInfo?.text.toString()
				if (alarmInfo.length != 8) return@setOnClickListener
				preventDoubleClick(it)
				lifecycleScope.launch {
					SharedEvent.loadingFlow.value = Tip("正在開啟詳細")
					delay(500)
					val r03sheet = R03DetailSheet()
					r03sheet.arguments = Bundle().apply {
						putString("value", alarmInfo)
					}
					r03sheet.show(requireActivity().supportFragmentManager, "r03sheet")
				}
			}
			field03.binding?.row2DetailBtn?.setOnClickListener {
				val alarmInfo = field03.binding?.row2AlarmInfo?.text.toString()
				if (alarmInfo.length != 8) return@setOnClickListener
				preventDoubleClick(it)
				lifecycleScope.launch {
					SharedEvent.loadingFlow.value = Tip("正在開啟詳細")
					delay(500)
					val r03sheet = R03DetailSheet()
					r03sheet.arguments = Bundle().apply {
						putString("value", alarmInfo)
					}
					r03sheet.show(requireActivity().supportFragmentManager, "r03sheet")
				}
			}

			// R16
			field16.binding?.readDetailBtn?.setOnClickListener {
				val readValue = field16.readValue
				if (readValue.isEmpty()) return@setOnClickListener
				preventDoubleClick(it)
				lifecycleScope.launch {
					SharedEvent.loadingFlow.value = Tip("正在開啟詳細")
					delay(500)
					val r16sheet = R16DetailSheet()
					r16sheet.arguments = Bundle().apply {
						putString("type", "read")
						putString("read", readValue)
					}
					r16sheet.show(requireActivity().supportFragmentManager, "r16sheet")
				}
			}
			field16.binding?.writeDetailBtn?.setOnClickListener {
				preventDoubleClick(it)
				lifecycleScope.launch {
					SharedEvent.loadingFlow.value = Tip("正在開啟詳細")
					delay(500)
					val r16sheet = R16DetailSheet()
					r16sheet.arguments = Bundle().apply {
						putString("type", "write")
						putString("read", field16.readValue.ifEmpty { null })
						if (field16.writeValue.length != 18) {
							field16.setWriteValue("@@@@@@@@@" + field16.readValue.ifEmpty { "@@@@@@@@@" })
						}
						val list = field16.writeValue.chunked(9)
						putString("writeMask", list[0])
						putString("writeValue", list[1])
					}
					r16sheet.show(requireActivity().supportFragmentManager, "r16sheet")
				}
			}
			// R50
			field50.binding?.readDetailBtn?.setOnClickListener {
				val readValue = field50.readValue
				if (readValue.isEmpty()) return@setOnClickListener
				preventDoubleClick(it)
				val r50sheet = R50DetailSheet()
				r50sheet.arguments = Bundle().apply {
					putString("type", "read")
					putString("value", readValue)
				}
				r50sheet.show(requireActivity().supportFragmentManager, "r50sheet")
			}
			field50.binding?.writeDetailBtn?.setOnClickListener {
				preventDoubleClick(it)
				val r50sheet = R50DetailSheet()
				r50sheet.arguments = Bundle().apply {
					putString("type", "write")
					putString("value", if (field50.writeValue.length != 13) field50.readValue else field50.writeValue)
				}
				r50sheet.show(requireActivity().supportFragmentManager, "r50sheet")
			}
		}

		// 訂閱選擇的meterRow
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterRowFlow.asStateFlow().collectLatest {
					it?.let {
						binding.field23.setReadValue(it.shutdownHistory1 + it.shutdownHistory2 + it.shutdownHistory3 + it.shutdownHistory4 + it.shutdownHistory5)
						binding.field03.setReadValue(it.alarmInfo1 + it.alarmInfo2)
						binding.field16.setReadValue(it.meterStatus ?: "")
						binding.field16.setWriteValue(if (it.meterStatus.isNullOrEmpty()) "" else "@@@@@@@@@${it.meterStatus}")
						binding.field57.setReadValues(splitStringByLength(it.hourlyUsage ?: "", 4))
						binding.field58.setReadValues(splitStringByLength((it.maximumUsage ?: "").padStart(4) + (it.maximumUsageTime ?: ""), 4, 2, 2, 2, 2))
						binding.field59.setReadValues(splitStringByLength((it.oneDayMaximumUsage ?: "").padStart(6) + (it.oneDayMaximumUsageDate ?: ""), 6, 2, 2))
						binding.field31.setReadValue((it.registerFuseFlowRate1 ?: "").padStart(4) + (it.registerFuseFlowRate2 ?: "").padStart(4))
						binding.field50.setReadValue(it.pressureShutOffJudgmentValue ?: "")
						binding.field50.setWriteValue(it.pressureShutOffJudgmentValue ?: "")
						binding.field51.setReadValues(splitStringByLength(it.pressureValue ?: "", 4))
					}
					refresh()
				}
			}
		}

		// 訂閱detail關閉後寫入設定欄
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				advVM.sheetDissmissSharedFlow.collectLatest { event ->
					when (event) {
						is SheetResult.S16 -> binding.field16.setWriteValue(event.data)
						is SheetResult.S50 -> binding.field50.setWriteValue(event.data)
					}
					refresh()
				}
			}
		}

		// 訂閱通信成功後, 取消全部checkboxes
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commEndSharedEvent.collectLatest { event:CommEndEvent ->
					if (event is CommEndEvent.Success) {
						checkboxes.forEach { it.isChecked = false }
						refresh()
					}
				}
			}
		}

		refresh()
	}

	// 需要認證通信的電文，勾選前檢查需有MacKey
	private fun hasMacKey(it:CheckBox):Boolean {
		return if (RD64H.Auth.macKey.isEmpty()) {
			lifecycleScope.launch {
				SharedEvent.eventFlow.emit(
					SharedEvent.ShowSnackbar("未設定通信金鑰(Session Key)\n請先至\"連線\"頁面設定。", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE)
				)
			}
			it.isChecked = false
			false
		} else true
	}

	// 分割字串by指定長度
	private fun splitStringByLength(input:String, vararg lengths:Int):List<String> {
		val result = mutableListOf<String>()
		var startIndex = 0

		for (length in lengths) {
			val endIndex = startIndex + length.coerceAtMost(input.length - startIndex)
			result.add(input.substring(startIndex, endIndex))
			startIndex = endIndex
		}

		return result
	}

	// 防連點
	private fun preventDoubleClick(it:View) {
		lifecycleScope.launch {
			it.isEnabled = false
			delay(2000)
			it.isEnabled = true
		}
	}

	// 開始R87通信
	private fun sendR87Telegram():Boolean {
		val meterId = meterVM.selectedMeterRowFlow.value?.meterId ?: return true
		meterBaseFragment.checkBluetoothOn {
			blVM.sendR87Telegram(meterId = meterId, r87Steps = r87Steps)
		}
		return false
	}

	// 刷新
	private fun refresh() {
		// 刷新變數
		refreshSteps()
		refreshEstimatedTime()
		// 刷新fab
		val isEnabled = estimatedTime > 0
		binding.sendFab.isEnabled = isEnabled
		val fabText = "通信" + if (isEnabled) " 約${estimatedTime}秒" else ""
		binding.sendFab.text = fabText
	}

	// 刷新Steps: 依checkbox勾選轉成R87Steps
	private fun refreshSteps() {
		r87Steps = mutableListOf()
		val meterId = meterVM.selectedMeterRowFlow.value?.meterId ?: return
		if (binding.field23.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R23"))
		if (binding.field03.binding?.readCheckbox?.isChecked == true || binding.field31.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R24"))
		if (binding.field16.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "R16"))
		if (binding.field16.binding?.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "S16", data = binding.field16.writeValue))
		if (binding.field57.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "R57"))
		if (binding.field58.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "R58"))
		if (binding.field59.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "R59"))
		if (binding.field31.binding?.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "S31", data = binding.field31.writeValue))
		if (binding.field50.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "R50"))
		if (binding.field50.binding?.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "S50", data = binding.field50.writeValue))
		if (binding.field51.binding?.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "R51"))
		if (binding.field41.binding?.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "C41"))
		if (binding.field42.binding?.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(securityLevel = SecurityLevel.Auth, adr = meterId, op = "C42"))
		if (binding.field02.binding?.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "C02"))
	}

	// 刷新耗時
	private fun refreshEstimatedTime() {
		if (r87Steps.isEmpty()) {
			estimatedTime = 0
			return
		}

		var totalPart = 0
		r87Steps.forEach { step ->
			totalPart += when (step.op) {
				"R23" -> 2
				else -> 1
			}
		}
		val btParentTransmissionTime = 5 // 5 ~ 20
		val estimatedTime = 1 +        // WT1
				0 +                        // D70: 經測試僅需0s, 說明書上卻寫: 2s=TO4
				1 +                        // WT4
				26.2 +                     // D36: 經測試僅需26.2s, 說明書上卻寫: (36 + btParentTransmissionTime)=TO2
				(3.5 + 17.2) * totalPart + // D87: 經測試僅需(WT2 + [11.8~17.2]) * n, 說明書上卻寫: (WT2 + 56) * n
				3.5                        // WT2
		this.estimatedTime = kotlin.math.ceil(estimatedTime).toInt()
	}
}