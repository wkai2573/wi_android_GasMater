package com.wavein.gasmeter.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle,
) : ViewModel() {



	private val _navigateSharedFlow = MutableSharedFlow<Int>()
	val navigateSharedFlow = _navigateSharedFlow.asSharedFlow()

	fun navigate(navId:Int) = viewModelScope.launch {
		_navigateSharedFlow.emit(navId)
	}


	val meterBaseChangeTabStateFlow = MutableStateFlow(-1)




	var meterRowPageBackDestinationIsSearch = false


	val meterBaseBackKeyClickSharedFlow = MutableSharedFlow<Boolean>()
	fun meterBaseOnBackKeyClick(smoothScroll:Boolean) = viewModelScope.launch {
		meterBaseBackKeyClickSharedFlow.emit(smoothScroll)
	}

}
