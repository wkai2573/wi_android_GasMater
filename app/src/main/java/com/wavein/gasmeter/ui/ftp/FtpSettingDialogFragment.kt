package com.wavein.gasmeter.ui.ftp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.wavein.gasmeter.databinding.DialogFtpSettingBinding
import com.wavein.gasmeter.databinding.DialogLoadingBinding
import com.wavein.gasmeter.tools.SharedEvent

@SuppressLint("MissingPermission")
class FtpSettingDialogFragment(
	private val ftpInfo:FtpInfo,
	private val saveBtnText:String? = null,
	private val saveBtnIcon:Drawable? = null,
	private val onSaveCallback:((ftpInfo:FtpInfo) -> Unit)? = null,
	private val onDismissCallback:((dialog:DialogInterface) -> Unit)? = null,
) : DialogFragment() {


	private var _binding:DialogFtpSettingBinding? = null
	private val binding get() = _binding!!
	private val ftpVM by activityViewModels<FtpViewModel>()

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		SharedEvent.snackbarDefaultView = null
		SharedEvent.snackbarDefaultAnchorView = null
		onDismissCallback?.invoke(dialog)
		_binding = null
	}

	override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
		return activity?.let {
			_binding = DialogFtpSettingBinding.inflate(it.layoutInflater)
			init(it)
			val dialog = ComponentDialog(it, theme).apply {
				setContentView(binding.root)
				setCanceledOnTouchOutside(false)
			}
			dialog
		} ?: throw IllegalStateException("Activity cannot be null")
	}


	private fun init(activity:FragmentActivity) {

		SharedEvent.snackbarDefaultView = binding.root
		SharedEvent.snackbarDefaultAnchorView = binding.buttonsLayout

		binding.hostInput.editText?.setText(ftpInfo.host)
		binding.usernameInput.editText?.setText(ftpInfo.username)
		binding.passwordInput.editText?.setText(ftpInfo.password)
		binding.remoteDirectoryInput.editText?.setText(ftpInfo.root)
		binding.closeBtn.setOnClickListener { dialog?.dismiss() }

		binding.testFtpBtn.setOnClickListener {
			ftpVM.testFtp(copiedFtpInfo)
		}
		if (saveBtnText != null) binding.saveBtn.text = saveBtnText
		if (saveBtnIcon != null) binding.saveBtn.icon = saveBtnIcon
		binding.saveBtn.setOnClickListener {
			onSaveCallback?.invoke(copiedFtpInfo)
			dialog?.dismiss()
		}
	}

	private val copiedFtpInfo
		get() = ftpInfo.copy(
			host = binding.hostInput.editText?.text.toString(),
			username = binding.usernameInput.editText?.text.toString(),
			password = binding.passwordInput.editText?.text.toString(),
			root = binding.remoteDirectoryInput.editText?.text.toString(),
		)

	companion object {

		fun open(
			context:Context,
			ftpInfo:FtpInfo,
			saveBtnText:String? = null,
			saveBtnIcon:Drawable? = null,
			onSaveCallback:((ftpInfo:FtpInfo) -> Unit)? = null,
			onDismissCallback:((dialog:DialogInterface) -> Unit)? = null
		) {
			val supportFragmentManager = (context as FragmentActivity).supportFragmentManager
			FtpSettingDialogFragment(
				ftpInfo = ftpInfo,
				saveBtnText = saveBtnText,
				saveBtnIcon = saveBtnIcon,
				onSaveCallback = onSaveCallback,
				onDismissCallback = onDismissCallback
			).show(supportFragmentManager, "FtpSettingDialogFragment")
		}
	}

}
