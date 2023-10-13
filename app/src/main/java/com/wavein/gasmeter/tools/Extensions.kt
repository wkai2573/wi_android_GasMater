package com.wavein.gasmeter.tools

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.CheckResult
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

// 其他自訂擴展

val Color_Success = Color.parseColor("#4a973b")

// 多行Snackbar
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

// 防抖EditText, https://stackoverflow.com/questions/63426845/android-edittext-coroutine-debounce-operator-like-rxjava
@ExperimentalCoroutinesApi
@CheckResult
fun EditText.textChanges():Flow<CharSequence?> {
	return callbackFlow {
		val listener = object : TextWatcher {
			override fun afterTextChanged(s:Editable?) = Unit
			override fun beforeTextChanged(s:CharSequence?, start:Int, count:Int, after:Int) = Unit
			override fun onTextChanged(s:CharSequence?, start:Int, before:Int, count:Int) {
				trySend(s)
			}
		}
		addTextChangedListener(listener)
		awaitClose { removeTextChangedListener(listener) }
	}.onStart { emit(text) }
}