package com.wavein.gasmeter.ui.meterwork

import androidx.lifecycle.ViewModel
import com.wavein.gasmeter.data.model.MeterGroup
import com.wavein.gasmeter.data.model.MeterRow
import kotlinx.coroutines.flow.MutableStateFlow

class MeterViewModel : ViewModel() {

	// 選擇的group & meter
	val selectedMeterGroupFlow = MutableStateFlow<MeterGroup?>(null)
	val selectedMeterRowFlow = MutableStateFlow<MeterRow?>(null)

	// 左分頁filter
	val groupsFilterFlow = MutableStateFlow(Filter.Undone)

	// 中分頁filter
	val metersFilterFlow = MutableStateFlow(Filter.Undone)

}

enum class Filter {All, Undone}