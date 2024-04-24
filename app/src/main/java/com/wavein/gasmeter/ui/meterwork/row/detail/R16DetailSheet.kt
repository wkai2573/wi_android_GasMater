package com.wavein.gasmeter.ui.meterwork.row.detail

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.databinding.BsheetR16DetailBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.component.DetailOption
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.row.SheetResult
import com.wavein.gasmeter.ui.meterwork.row.MeterAdvViewModel

class R16DetailSheet : BottomSheetDialogFragment() {

	private lateinit var binding:BsheetR16DetailBinding
	private val advVM by activityViewModels<MeterAdvViewModel>()

	// 傳入值
	private val type by lazy { arguments?.getString("type") ?: "read" }
	private val readBitsMap by lazy {
		var value = arguments?.getString("read") ?: return@lazy null
		if (value.length < 9) value = value.padEnd(9, '@')
		MeterRow.data2BitsMap(value, "M")
	}
	private val writeMaskBitsMap by lazy {
		var mask = arguments?.getString("writeMask") ?: "@@@@@@@@@"
		if (mask.length < 9) mask = mask.padEnd(9, '@')
		MeterRow.data2BitsMap(mask, "M")
	}
	private val writeValueBitsMap by lazy {
		var value = arguments?.getString("writeValue") ?: "@@@@@@@@@"
		if (value.length < 9) value = value.padEnd(9, '@')
		MeterRow.data2BitsMap(value, "M")
	}

	private val optionViews
		get():Array<DetailOption> {
			return binding.run {
				arrayOf(
					optionM9b3, optionM9b2, optionM9b1,
					optionM8b4, optionM8b3, optionM8b2, optionM8b1,
					optionM7b4, optionM7b3, optionM7b2, optionM7b1,
					optionM6b4, optionM6b3, optionM6b2, optionM6b1,
					optionM5b4, optionM5b3, optionM5b2, optionM5b1,
					optionM4b4, optionM4b3, optionM4b2, optionM4b1,
					optionM3b4, optionM3b3, optionM3b2, optionM3b1,
					optionM2b4, optionM2b3, optionM2b2, optionM2b1,
					optionM1b4, optionM1b3, optionM1b2, optionM1b1,
				)
			}
		}

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		// 關閉時將設定值傳送給訂閱者
		if (type == "write") {
			advVM.emitResult(SheetResult.S16(getResult()))
		}
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		binding = BsheetR16DetailBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		SharedEvent.loadingFlow.value = Tip("")

		BottomSheetBehavior.from(binding.sheet).apply {
			peekHeight = 9999
			state = BottomSheetBehavior.STATE_EXPANDED
		}

		binding.closeBtn.setOnClickListener { dismiss() }

		if (type == "read") binding.writeTipTv.visibility = View.GONE

		//根據傳入值，設定UI(維持按鈕顯示 & 各option的值)
		when (type) {
			"read" -> optionViews.forEach {
				val tagSplit = it.tag.toString().split("|")
				val charIndex = tagSplit[0]
				val bitIndex = tagSplit[1]
				val enabled = readBitsMap!![charIndex]!![bitIndex]!!
				it.setSelected(if (enabled) OptionEnum.Enable else OptionEnum.Disable)
				it.setEditable(false)
			}

			"write" -> optionViews.forEach { it ->
				val tagSplit = it.tag.toString().split("|")
				val charIndex = tagSplit[0]
				val bitIndex = tagSplit[1]
				val maskEnabled = writeMaskBitsMap[charIndex]!![bitIndex]!!
				val valueEnabled = writeValueBitsMap[charIndex]!![bitIndex]!!
				val optionEnum = if (!maskEnabled) {
					OptionEnum.Keep
				} else {
					if (valueEnabled) OptionEnum.Enable else OptionEnum.Disable
				}
				val readValue = readBitsMap?.get(charIndex)?.getValue(bitIndex)
				it.readValue = readValue
				it.setSelected(optionEnum)

				// 瓦斯表目前的設定 設為粗體
				if (readBitsMap != null) {
					it.setBold(if (readValue == true) OptionEnum.Enable else OptionEnum.Disable)
				}
			}
		}
	}

	// 將設定值轉成bits字串
	private fun getResult():String {
		val muskBitsMap = this.writeMaskBitsMap.mapValues { (_, value) -> value.toMutableMap() }.toMutableMap()
		val valueBitsMap = this.writeValueBitsMap.mapValues { (_, value) -> value.toMutableMap() }.toMutableMap()
		optionViews.forEach {
			val tagSplit = it.tag.toString().split("|")
			val charIndex = tagSplit[0]
			val bitIndex = tagSplit[1]

			when (it.selected) {
				OptionEnum.Keep -> {
					muskBitsMap[charIndex]!![bitIndex] = false
				}

				OptionEnum.Enable -> {
					muskBitsMap[charIndex]!![bitIndex] = true
					valueBitsMap[charIndex]!![bitIndex] = true
				}

				OptionEnum.Disable -> {
					muskBitsMap[charIndex]!![bitIndex] = true
					valueBitsMap[charIndex]!![bitIndex] = false
				}
			}
		}
		return MeterRow.bitsMap2Data(muskBitsMap) + MeterRow.bitsMap2Data(valueBitsMap)
	}

}