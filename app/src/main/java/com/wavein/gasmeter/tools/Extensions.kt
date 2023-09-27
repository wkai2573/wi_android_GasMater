package com.wavein.gasmeter.tools

import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

// 其他自訂擴展

fun Snackbar.allowInfiniteLines():Snackbar {
	return apply { (view.findViewById<View?>(com.google.android.material.R.id.snackbar_text) as? TextView?)?.isSingleLine = false }
}