package com.wavein.gasmeter.ui.metersearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class MeterSearchViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle,
) : ViewModel() {


	val searchStateFlow = MutableStateFlow(SearchState())
}


data class SearchState(
	val text:String = "",
	val lowBattery:Boolean = false,
	val innerPipeLeakage:Boolean = false,
	val shutoff:Boolean = false,
) {
	val notSearch:Boolean = text.isEmpty() && !lowBattery && !innerPipeLeakage && !shutoff
}