package com.wavein.gasmeter.ui.meterwork.row.detail

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.BsheetR50DetailBinding
import com.wavein.gasmeter.ui.meterwork.row.MeterAdvViewModel
import com.wavein.gasmeter.ui.meterwork.row.SheetResult


class R50DetailSheet : BottomSheetDialogFragment() {

	private lateinit var binding:BsheetR50DetailBinding
	private val advVM by activityViewModels<MeterAdvViewModel>()

	// 傳入值
	private val type by lazy { arguments?.getString("type") ?: "read" }
	private val value by lazy { arguments?.getString("value") ?: "" }
	private val param1 by lazy { kotlin.runCatching { value.substring(0, 3).toInt().toString() }.getOrNull() ?: "" }
	private val param2 by lazy { kotlin.runCatching { value.substring(3, 6).toInt().toString() }.getOrNull() ?: "" }
	private val param3 by lazy { kotlin.runCatching { value.substring(6, 9).toInt().toString() }.getOrNull() ?: "" }
	private val param4 by lazy { kotlin.runCatching { value.substring(9, 12).toInt().toString() }.getOrNull() ?: "" }
	private val param5 by lazy { kotlin.runCatching { value.substring(12, 13).toInt().toString() }.getOrNull() ?: "" }

	override fun onDismiss(dialog:DialogInterface) {
		super.onDismiss(dialog)
		// 關閉時將設定值傳送給訂閱者
		if (type == "write") {
			advVM.emitResult(SheetResult.S50(getResult()))
		}
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		binding = BsheetR50DetailBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) // 防止鍵盤覆蓋BottomSheet
		BottomSheetBehavior.from(binding.sheet).apply {
			peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO // 最大高度
			state = BottomSheetBehavior.STATE_EXPANDED // 要被鍵盤往上推, 需使用STATE_EXPANDED
		}

		binding.closeBtn.setOnClickListener { dismiss() }

		//根據傳入值，設定UI
		binding.input1.editText?.setText(param1)
		binding.input2.editText?.setText(param2)
		binding.input3.editText?.setText(param3)
		binding.input4.editText?.setText(param4)
		binding.input5.editText?.setText(param5)
		binding.input1.editText?.addTextChangedListener(textWatcher(3))
		binding.input2.editText?.addTextChangedListener(textWatcher(3))
		binding.input3.editText?.addTextChangedListener(textWatcher(3))
		binding.input4.editText?.addTextChangedListener(textWatcher(3))
		binding.input5.editText?.addTextChangedListener(textWatcher(1))
		when (type) {
			"read" -> {
				binding.input1.editText?.isEnabled = false
				binding.input2.editText?.isEnabled = false
				binding.input3.editText?.isEnabled = false
				binding.input4.editText?.isEnabled = false
				binding.input5.editText?.isEnabled = false
			}

			"write" -> {}
		}
	}

	private fun textWatcher(maxLength:Int) = object : TextWatcher {
		override fun beforeTextChanged(s:CharSequence?, start:Int, count:Int, after:Int) {}
		override fun onTextChanged(s:CharSequence?, start:Int, before:Int, count:Int) {}
		override fun afterTextChanged(editable:Editable) {
			if (editable.length > maxLength) editable.delete(maxLength, editable.length)
		}
	}

	// 將ui設定 轉成 傳送用data
	private fun getResult():String {
		val param1 = binding.input1.editText?.text.toString().padStart(3, '0').substring(0, 3)
		val param2 = binding.input2.editText?.text.toString().padStart(3, '0').substring(0, 3)
		val param3 = binding.input3.editText?.text.toString().padStart(3, '0').substring(0, 3)
		val param4 = binding.input4.editText?.text.toString().padStart(3, '0').substring(0, 3)
		val param5 = binding.input5.editText?.text.toString().padStart(1, '0').substring(0, 1)
		return "$param1$param2$param3$param4$param5"
	}

}