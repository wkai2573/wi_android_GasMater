package com.wavein.gasmeter.ui.meterwork.row

import androidx.lifecycle.ViewModel
import com.wavein.gasmeter.tools.rd64h.R87Step
import kotlinx.coroutines.flow.MutableStateFlow

class MeterAdvViewModel : ViewModel() {

	//val meterRowsStateFlow = MutableStateFlow<List<MeterRow>>(emptyList())

	val r87StepsStateFlow = MutableStateFlow<List<R87Step>>(emptyList())

}
