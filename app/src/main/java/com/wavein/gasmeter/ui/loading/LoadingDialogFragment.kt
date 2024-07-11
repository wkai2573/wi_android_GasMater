package com.wavein.gasmeter.ui.loading

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wavein.gasmeter.databinding.DialogLoadingBinding
import com.wavein.gasmeter.tools.SharedEvent
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission")
class LoadingDialogFragment(
	private val onDismissCallback:((dialog:DialogInterface) -> Unit)? = null,
) : androidx.fragment.app.DialogFragment() {


	private var binding:DialogLoadingBinding? = null

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		onDismissCallback?.invoke(dialog)
		binding = null
	}

	override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
		return activity?.let {
			binding = DialogLoadingBinding.inflate(it.layoutInflater)
			init(it)
			val dialog = ComponentDialog(it, theme).apply {
				setContentView(binding!!.root)
				setCanceledOnTouchOutside(false)
				onBackPressedDispatcher.addCallback { }
				window?.decorView?.setBackgroundColor(Color.TRANSPARENT)
			}
			dialog
		} ?: throw IllegalStateException("Activity cannot be null")
	}

	private fun init(activity:FragmentActivity) {

		lifecycleScope.launch {
			activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
				SharedEvent.loadingFlow.asStateFlow().collectLatest { tip ->
					binding?.titleTv?.text = tip.title
					binding?.subtitleTv?.visibility = if (tip.subtitle.isEmpty()) View.GONE else View.VISIBLE
					binding?.subtitleTv?.text = tip.subtitle
					binding?.progressTv?.visibility = if (tip.progress.isEmpty()) View.GONE else View.VISIBLE
					binding?.progressTv?.text = tip.progress
				}
			}
		}
	}

	companion object {

		fun open(context:Context):LoadingDialogFragment {
			val supportFragmentManager = (context as FragmentActivity).supportFragmentManager
			return LoadingDialogFragment().apply {
				show(supportFragmentManager, "LoadingDialogFragment")
			}
		}
	}

}