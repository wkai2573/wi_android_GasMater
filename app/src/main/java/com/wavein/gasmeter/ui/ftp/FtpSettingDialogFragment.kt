package com.wavein.gasmeter.ui.ftp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
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

@SuppressLint("MissingPermission")
class FtpSettingDialogFragment(
	private val ftpInfo:FtpInfo,
	private val onSaveCallback:((ftpInfo:FtpInfo) -> Unit)? = null,
	private val onDismissCallback:((dialog:DialogInterface) -> Unit)? = null,
) : DialogFragment() {

	// binding & viewModel
	private var _binding:DialogFtpSettingBinding? = null
	private val binding get() = _binding!!
	private val ftpVM by activityViewModels<FtpViewModel>()

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		ftpVM.snackbarView = null
		ftpVM.snackbarAnchorView = null
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

	// 初始化ui
	private fun init(activity:FragmentActivity) {
		//設定snackbar位置, dismiss後要改回null
		ftpVM.snackbarView = binding.root
		ftpVM.snackbarAnchorView = binding.buttonsLayout

		binding.hostEt.setText(ftpInfo.host)
		binding.usernameEt.setText(ftpInfo.username)
		binding.passwordEt.setText(ftpInfo.password)
		binding.remoteDirectoryEt.setText(ftpInfo.root)
		binding.closeBtn.setOnClickListener { dialog?.dismiss() }

		binding.testFtpBtn.setOnClickListener {
			ftpVM.testFtp(copiedFtpInfo)
		}
		binding.saveBtn.setOnClickListener {
			onSaveCallback?.invoke(copiedFtpInfo)
			dialog?.dismiss()
		}
	}

	private val copiedFtpInfo
		get() = ftpInfo.copy(
			host = binding.hostEt.text.toString(),
			username = binding.usernameEt.text.toString(),
			password = binding.passwordEt.text.toString(),
			root = binding.remoteDirectoryEt.text.toString(),
		)

	companion object {
		// 開啟ftp設定視窗
		fun open(
			context:Context,
			ftpInfo:FtpInfo,
			onSaveCallback:((ftpInfo:FtpInfo) -> Unit)? = null,
			onDismissCallback:((dialog:DialogInterface) -> Unit)? = null
		) {
			val supportFragmentManager = (context as FragmentActivity).supportFragmentManager
			FtpSettingDialogFragment(
				ftpInfo = ftpInfo,
				onSaveCallback = onSaveCallback,
				onDismissCallback = onDismissCallback
			).show(supportFragmentManager, "FtpSettingDialogFragment")
		}
	}

}
