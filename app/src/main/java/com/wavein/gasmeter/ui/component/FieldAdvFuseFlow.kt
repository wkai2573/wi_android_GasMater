package com.wavein.gasmeter.ui.component

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdvFuseflowBinding


// 自訂View元件: 進階讀取設定欄位(登錄母火流量)
class FieldAdvFuseFlow : LinearLayout {
	var binding:CustFieldAdvFuseflowBinding? = null

	val readValue get() = binding?.readValueTv?.text ?: ""
	val writeValue:String
		get() {
			val writeLowerLimit = kotlin.runCatching { binding?.writeLowerLimitInput?.editText?.text?.toString()?.toFloat() }.getOrElse { 0f }
			val writeLowerLimitStr = String.format("%.2f", writeLowerLimit).replace(".", "").padStart(4, '0')
			val writeUpperLimit = kotlin.runCatching { binding?.writeUpperLimitInput?.editText?.text?.toString()?.toFloat() }.getOrElse { 0f }
			val writeUpperLimitStr = String.format("%.2f", writeUpperLimit).replace(".", "").padStart(4, '0')
			return writeLowerLimitStr + writeUpperLimitStr
		}

	fun setReadValue(text:String) {
		binding?.readValueTv?.text = text
	}

	fun setWriteLowerLimit(text:String) {
		binding?.writeLowerLimitInput?.editText?.setText(text)
	}

	fun setWriteUpperLimit(text:String) {
		binding?.writeUpperLimitInput?.editText?.setText(text)
	}

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdvFuseflowBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdvFuseflowBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdvFuseflowBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdvFuseFlow)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdvFuseFlow, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdvFuseFlow_fieldAdvFuseFlowTitle)
		val readChecked = typedArray.getBoolean(R.styleable.FieldAdvFuseFlow_fieldAdvFuseFlowReadChecked, false)
		val writeChecked = typedArray.getBoolean(R.styleable.FieldAdvFuseFlow_fieldAdvFuseFlowWriteChecked, false)
		val bottomDivider = typedArray.getBoolean(R.styleable.FieldAdvFuseFlow_fieldAdvFuseFlowBottomDivider, true)
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		binding?.readCheckbox?.isChecked = readChecked
		binding?.writeCheckbox?.isChecked = writeChecked
		binding?.bottomDivider?.visibility = if (bottomDivider) View.VISIBLE else View.GONE
		binding?.writeLowerLimitInput?.editText?.addTextChangedListener(textWatcher())
		binding?.writeUpperLimitInput?.editText?.addTextChangedListener(textWatcher())
	}

	private fun textWatcher() = object : TextWatcher {
		override fun beforeTextChanged(s:CharSequence?, start:Int, count:Int, after:Int) {}
		override fun onTextChanged(s:CharSequence?, start:Int, before:Int, count:Int) {}
		override fun afterTextChanged(editable:Editable) {
			// 自動補.
			if (editable.length >= 3 && !editable.contains('.')) editable.replace(2, 2, ".")
			// 不可負數
			if (editable.startsWith('-')) editable.delete(0, 1)
			// 小數點後最多2位
			if (editable.contains('.')) {
				val split = editable.toString().split('.')
				if (split[1].length > 2) {
					val length = split[0].length + 3
					editable.delete(length, editable.length)
				}
			}
			// 最多5位
			if (editable.length > 5) editable.delete(5, editable.length)
		}
	}
}