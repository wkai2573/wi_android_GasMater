package com.wavein.gasmeter.ui.meterwork.row

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeterAdvViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	//...
) : ViewModel() {

	val sheetDissmissSharedFlow = MutableSharedFlow<SheetResult>()

	fun emitResult(event:SheetResult) = viewModelScope.launch {
		sheetDissmissSharedFlow.emit(event)
	}

}


// 溝通結束事件
sealed class SheetResult {
	data class S16(val data:String) : SheetResult()
}