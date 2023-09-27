package com.wavein.gasmeter.tools

import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

// 共用事件
sealed class SharedEvent {

	enum class Color { Normal, Error, Success, Info }

	data class ShowSnackbar(
		val message:String,
		val color:Color = Color.Normal,
		val duration:Int = Snackbar.LENGTH_SHORT,
		val anchorView:View? = null,
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

	data class ShowDialogB(
		val builder:AlertDialog,
	) : SharedEvent()

	companion object {
		// 事件流
		val eventFlow = MutableSharedFlow<SharedEvent>()

		// 可觀察變數
		val loadingFlow = MutableStateFlow("")
	}
}