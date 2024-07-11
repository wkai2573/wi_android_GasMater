package com.wavein.gasmeter.ui.meterwork.row.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.databinding.BsheetR03DetailBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.component.DetailOption
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.row.MeterAdvViewModel

class R03DetailSheet : BottomSheetDialogFragment() {

	private lateinit var binding:BsheetR03DetailBinding
	private val advVM by activityViewModels<MeterAdvViewModel>()


	private val valueBitsMap by lazy {
		var value = arguments?.getString("value") ?: "@@@@@@@@"
		if (value.length < 8) value = value.padEnd(8, '@')
		MeterRow.data2BitsMap(value, "A")
	}

	private val optionViews
		get():Array<DetailOption> {
			return binding.run {
				arrayOf(
					optionA8b4, optionA8b3, optionA8b2, optionA8b1,
					optionA7b4, optionA7b3, optionA7b2, optionA7b1,
					optionA6b4, optionA6b3, optionA6b2, optionA6b1,
					optionA5b4, optionA5b3, optionA5b2, optionA5b1,
					optionA4b4, optionA4b3, optionA4b2, optionA4b1,
					optionA3b4, optionA3b3, optionA3b2, optionA3b1,
					optionA2b4, optionA2b3, optionA2b2, optionA2b1,
					optionA1b4, optionA1b3, optionA1b2, optionA1b1,
				)
			}
		}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		binding = BsheetR03DetailBinding.inflate(inflater, container, false)
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


		optionViews.forEach {
			val tagSplit = it.tag.toString().split("|")
			val charIndex = tagSplit[0]
			val bitIndex = tagSplit[1]
			val enabled = valueBitsMap[charIndex]!![bitIndex]!!
			it.setSelected(if (enabled) OptionEnum.Enable else OptionEnum.Disable)
			it.setEditable(false)
		}
	}

}