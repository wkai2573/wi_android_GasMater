package com.wavein.gasmeter.tools

import android.content.DialogInterface
import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 共用事件
sealed class SharedEvent {

	enum class SnackbarColor { Normal, Error, Success, Info }

	data class ShowSnackbar(
		val message:String,
		val color:SnackbarColor = SnackbarColor.Normal,
		val duration:Int = Snackbar.LENGTH_SHORT,
		val view:View? = null,
	) : SharedEvent()

	data class ShowDialog(
		val title:String,
		val message:String,
		val positiveButton:Pair<CharSequence, DialogInterface.OnClickListener> = "ok" to DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() },
		val negativeButton:Pair<CharSequence, DialogInterface.OnClickListener>? = null,
		val neutralButton:Pair<CharSequence, DialogInterface.OnClickListener>? = null,
		val onDissmiss:DialogInterface.OnDismissListener = DialogInterface.OnDismissListener { dialog -> },
	) : SharedEvent()

	companion object {
		// 事件流
		val _eventFlow = MutableSharedFlow<SharedEvent>()
		val eventFlow = _eventFlow.asSharedFlow()
	}
}