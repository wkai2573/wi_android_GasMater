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
	private val savedStateHandle:SavedStateHandle,


) : ViewModel() {

	val sheetDissmissSharedFlow = MutableSharedFlow<SheetResult>()

	fun emitResult(event:SheetResult) = viewModelScope.launch {
		sheetDissmissSharedFlow.emit(event)
	}
}



sealed class SheetResult {
	data class S16(val data:String) : SheetResult()
	data class S50(val data:String) : SheetResult()
}