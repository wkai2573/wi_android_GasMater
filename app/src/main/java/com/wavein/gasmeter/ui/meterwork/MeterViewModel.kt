package com.wavein.gasmeter.ui.meterwork

import androidx.lifecycle.ViewModel
import com.wavein.gasmeter.data.model.MeterGroup
import com.wavein.gasmeter.data.model.MeterRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MeterViewModel : ViewModel() {

	val meterRowsStateFlow = MutableStateFlow<List<MeterRow>>(emptyList())

	// 選擇的group & meter
	// group設定後要清除meter的選擇, 所以設為private
	private val selectedMeterGroupFlow = MutableStateFlow<MeterGroup?>(null)
	val selectedMeterGroupStateFlow get() = selectedMeterGroupFlow.asStateFlow()
	fun setSelectedMeterGroup(meterGroup:MeterGroup?) {
		selectedMeterGroupFlow.value = meterGroup
		selectedMeterRowFlow.value = null
	}

	val selectedMeterRowFlow = MutableStateFlow<MeterRow?>(null)

	// 左分頁filter
	val groupsFilterFlow = MutableStateFlow(Filter.All)

	// 中分頁filter
	val metersFilterFlow = MutableStateFlow(Filter.All)

}

enum class Filter { All, Undone }