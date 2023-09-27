package com.wavein.gasmeter.ui.loading

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.wavein.gasmeter.databinding.DialogLoadingBinding
import com.wavein.gasmeter.tools.SharedEvent


@SuppressLint("MissingPermission")
class LoadingDialogFragment(
	private val onDismissCallback:((dialog:DialogInterface) -> Unit)? = null,
) : androidx.fragment.app.DialogFragment() {

	// binding & viewModel
	private var _binding:DialogLoadingBinding? = null
	private val binding get() = _binding!!

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		onDismissCallback?.invoke(dialog)
		_binding = null
	}

	override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
		return activity?.let {
			_binding = DialogLoadingBinding.inflate(it.layoutInflater)
			init(it)
			val dialog = ComponentDialog(it, theme).apply {
				setContentView(binding.root)
				setCanceledOnTouchOutside(false)
				onBackPressedDispatcher.addCallback { } // 覆寫返回鍵, https://stackoverflow.com/questions/21307858/detect-back-button-but-dont-dismiss-dialogfragment/72866899#72866899
				window?.decorView?.setBackgroundColor(Color.TRANSPARENT) // 透明背景, https://blog.csdn.net/Mr_Tony/article/details/104783448
			}
			dialog
		} ?: throw IllegalStateException("Activity cannot be null")
	}

	private fun init(activity:FragmentActivity) {
		binding.loadingTv.text = SharedEvent.loadingFlow.value
	}

	companion object {
		// 開啟視窗
		fun open(context:Context):LoadingDialogFragment {
			val supportFragmentManager = (context as FragmentActivity).supportFragmentManager
			return LoadingDialogFragment().apply {
				show(supportFragmentManager, "LoadingDialogFragment")
			}
		}
	}

}