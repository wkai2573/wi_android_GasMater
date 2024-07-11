package com.wavein.gasmeter.tools

import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.ui.loading.Tip
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow


sealed class SharedEvent {

	enum class Color { Normal, Error, Success, Info }

	data class ShowSnackbar(
		val message:String,
		val color:Color = Color.Normal,
		val duration:Int = Snackbar.LENGTH_SHORT,
		val view:View? = snackbarDefaultView,
		val anchorView:View? = snackbarDefaultAnchorView,
	) : SharedEvent()

	data class ShowDialog(
		val title:String,
		val message:String,
		val positiveButton:Pair<CharSequence, DialogInterface.OnClickListener> = "ok" to DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() },
		val negativeButton:Pair<CharSequence, DialogInterface.OnClickListener>? = null,
		val neutralButton:Pair<CharSequence, DialogInterface.OnClickListener>? = null,
		val onDissmiss:DialogInterface.OnDismissListener = DialogInterface.OnDismissListener { dialog -> },
	) : SharedEvent()

	data class ShowDialogB(val alertDialog:AlertDialog) : SharedEvent()

	data class PlayEffect(val vibrate:Boolean = true, val sound:Boolean = true) : SharedEvent()

	companion object {

		val eventFlow = MutableSharedFlow<SharedEvent>()


		var snackbarDefaultView:View? = null
		var snackbarDefaultAnchorView:View? = null


		val loadingFlow = MutableStateFlow(Tip())


		private suspend fun showErrDialog(e:Exception) {
			var errMessage = e.message ?: "unknown error"
			for (element in e.stackTrace) {
				errMessage += "\nat ${element.fileName}:${element.lineNumber}"
			}
			eventFlow.emit(SharedEvent.ShowDialog("Error", errMessage))
		}


		suspend fun catching(handle:suspend () -> Unit) {
			try {
				handle()
			} catch (e:Exception) {
				showErrDialog(e)
			}
		}
	}
}