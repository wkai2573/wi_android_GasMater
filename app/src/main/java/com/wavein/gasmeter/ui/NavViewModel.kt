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
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
) : ViewModel() {

	// 事件變數
	// 導航至指定fragment
	private val _navigateSharedFlow = MutableSharedFlow<Int>()
	val navigateSharedFlow = _navigateSharedFlow.asSharedFlow()

	fun navigate(navId:Int) = viewModelScope.launch {
		_navigateSharedFlow.emit(navId)
	}

	// meterBase頁中切換tab
	val meterBaseChangeTabStateFlow = MutableStateFlow(-1)

	// 瓦斯表的back鍵目的地
	// 若從群組瓦斯表進入, 為false
	// 若從查詢進入, 為true
	var meterRowPageBackDestinationIsSearch = false

	// meterBase頁中點返回鍵
	val meterBaseBackKeyClickSharedFlow = MutableSharedFlow<Boolean>()
	fun meterBaseOnBackKeyClick(smoothScroll:Boolean) = viewModelScope.launch {
		meterBaseBackKeyClickSharedFlow.emit(smoothScroll)
	}

}
