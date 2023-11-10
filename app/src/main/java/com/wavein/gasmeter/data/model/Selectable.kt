package com.wavein.gasmeter.data.model

data class Selectable<T>(
	var selected:Boolean = false,
	var data:T
)
