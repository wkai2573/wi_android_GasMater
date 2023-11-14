package com.wavein.gasmeter.data.model

// 讓adapter知道item有無選中, 換底色用
data class Selectable<T>(
	var selected:Boolean = false,
	var data:T
)
