package com.wavein.gasmeter.ui.meterwork.row

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.FragmentMeterAdvBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.rd64h.R87Step
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
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

	// 實例
	private val meterBaseFragment:MeterBaseFragment get() = ((parentFragment as MeterRowFragment).parentFragment as MeterBaseFragment)

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

		// 傳送按鈕
		binding.sendFab.setOnClickListener {
			if (checkboxToR87Steps().isEmpty()) return@setOnClickListener
			// 視窗提示耗時 & 確認
			MaterialAlertDialogBuilder(requireContext()).apply {
				setTitle("進階查詢/設定")
				setMessage("準備進行進階查詢/設定\n耗時約${getEstimatedTime()}秒")
				setNeutralButton("取消") { dialog, which -> dialog.dismiss() }
				setPositiveButton("確定") { dialog, which ->
					dialog.dismiss()
					sendR87Telegram()
				}
				show()
			}
		}

		// checkbox
		binding.apply {
			listOf(field23, field03, field57, field58, field59, field51).forEach { field ->
				field.readCheckbox?.setOnCheckedChangeListener { buttonView, isChecked -> refreshFab() }
			}
			listOf(field16, field31, field50).forEach { field ->
				field.readCheckbox?.setOnCheckedChangeListener { buttonView, isChecked ->
					if (!buttonView.isPressed) return@setOnCheckedChangeListener
					field.writeCheckbox?.isChecked = false
					refreshFab()
				}
				field.writeCheckbox?.setOnCheckedChangeListener { buttonView, isChecked ->
					if (!buttonView.isPressed) return@setOnCheckedChangeListener
					field.readCheckbox?.isChecked = false
					refreshFab()
				}
			}
		}

		// todo 詳細按鈕
		binding.apply {
			// R16
			field16.readDetailBtn?.setOnClickListener {
				val readValue = field16.readValue
				if (readValue.isEmpty()) return@setOnClickListener
				preventDoubleClick(it)
				lifecycleScope.launch {
					SharedEvent.loadingFlow.value = Tip("正在開啟詳細")
					delay(500)
					val r16sheet = R16DetailSheet()
					r16sheet.arguments = Bundle().apply {
						putString("type", "read")
						putString("value", readValue)
					}
					r16sheet.show(requireActivity().supportFragmentManager, "r16sheet")
				}
			}
			field16.writeDetailBtn?.setOnClickListener {
				preventDoubleClick(it)
				lifecycleScope.launch {
					SharedEvent.loadingFlow.value = Tip("正在開啟詳細")
					delay(500)
					val r16sheet = R16DetailSheet()
					r16sheet.arguments = Bundle().apply {
						putString("type", "write")
						if (field16.writeValue.length != 18) {
							putString("mask", "@@@@@@@@@")
							putString("value", field16.readValue)
						} else {
							val list = field16.writeValue.chunked(9)
							putString("mask", list[0])
							putString("value", list[1])
						}
					}
					r16sheet.show(requireActivity().supportFragmentManager, "r16sheet")
				}
			}
			// R50
			field50.readDetailBtn?.setOnClickListener {
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
			field50.writeDetailBtn?.setOnClickListener {
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
						binding.field23.setReadValue(it.shutdownHistoryForFieldShow)
						binding.field03.setReadValue(it.alarmInfoForFieldShow)
						binding.field16.setReadValue(it.meterStatus ?: "")
						binding.field57.setReadValue(it.hourlyUsage ?: "")
						binding.field58.setReadValue(it.maximumUsage ?: "")
						binding.field59.setReadValue(it.oneDayMaximumUsage ?: "")
						binding.field31.setReadValue("${it.registerFuseFlowRate1} + ${it.registerFuseFlowRate2}")
						binding.field50.setReadValue(it.pressureShutOffJudgmentValue ?: "")
						binding.field51.setReadValue(it.pressureValue ?: "")
					}
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
						// todo ...
					}
				}
			}
		}

		refreshFab()
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
			blVM.sendR87Telegram(
				meterId = meterId,
				r87Steps = checkboxToR87Steps(),
			)
		}
		return false
	}

	// 依checkbox勾選轉成R87Steps
	private fun checkboxToR87Steps():List<R87Step> {
		val r87Steps = mutableListOf<R87Step>()
		val meterId = meterVM.selectedMeterRowFlow.value?.meterId ?: return r87Steps
		if (binding.field23.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R23"))
		if (binding.field03.readCheckbox?.isChecked == true || binding.field31.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R24"))
		if (binding.field16.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R16"))
		if (binding.field16.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "S16", data = binding.field16.writeValue))
		if (binding.field57.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R57"))
		if (binding.field58.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R58"))
		if (binding.field59.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R59"))
		if (binding.field31.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "S31", data = binding.field31.writeValue))
		if (binding.field50.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R50"))
		if (binding.field50.writeCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "S50", data = binding.field50.writeValue))
		if (binding.field51.readCheckbox?.isChecked == true)
			r87Steps.add(R87Step(adr = meterId, op = "R51"))
		return r87Steps
	}

	// 計算耗時
	private fun getEstimatedTime():Int {
		val r87Steps = checkboxToR87Steps()
		if (r87Steps.isEmpty()) return 0

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
		return kotlin.math.ceil(estimatedTime).toInt()
	}

	// 刷新FAB
	private fun refreshFab() {
		val estimatedTime = getEstimatedTime()
		val isEnabled = estimatedTime > 0
		binding.sendFab.isEnabled = isEnabled
		val fabText = "通信" + if (isEnabled) " 約${estimatedTime}秒" else ""
		binding.sendFab.text = fabText
	}
}