package com.wavein.gasmeter.tools

import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

// 其他自訂擴展

val Color_Success = Color.parseColor("#4a973b")

fun Snackbar.allowInfiniteLines():Snackbar {
	return apply { (view.findViewById<View?>(com.google.android.material.R.id.snackbar_text) as? TextView?)?.isSingleLine = false }
}

// 指定 string<->boolean 互轉
fun String?.toBoolean10():Boolean? {
	return when (this) {
		"1" -> true
		"0" -> false
		else -> null
	}
}

fun Boolean?.toString10():String {
	return when (this) {
		true -> "1"
		false -> "0"
		else -> ""
	}
}