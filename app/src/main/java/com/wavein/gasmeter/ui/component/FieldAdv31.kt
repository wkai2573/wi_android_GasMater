package com.wavein.gasmeter.ui.component

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv31Binding


// 自訂View元件: 進階讀取設定欄位_登錄母火流量(RS31)
class FieldAdv31 : LinearLayout {
	var binding:CustFieldAdv31Binding? = null

	val readValue:String
		get() {
			val readLowerLimit = kotlin.runCatching { binding?.readLowerLimitInput?.editText?.text?.toString()?.toFloat() }.getOrElse { 0f }
			val readLowerLimitStr = String.format("%.2f", readLowerLimit).replace(".", "").padStart(4, '0')
			val readUpperLimit = kotlin.runCatching { binding?.readUpperLimitInput?.editText?.text?.toString()?.toFloat() }.getOrElse { 0f }
			val readUpperLimitStr = String.format("%.2f", readUpperLimit).replace(".", "").padStart(4, '0')
			return readLowerLimitStr + readUpperLimitStr
		}
	val writeValue:String
		get() {
			val writeLowerLimit = kotlin.runCatching { binding?.writeLowerLimitInput?.editText?.text?.toString()?.toFloat() }.getOrElse { 0f }
			val writeLowerLimitStr = String.format("%.2f", writeLowerLimit).replace(".", "").padStart(4, '0')
			val writeUpperLimit = kotlin.runCatching { binding?.writeUpperLimitInput?.editText?.text?.toString()?.toFloat() }.getOrElse { 0f }
			val writeUpperLimitStr = String.format("%.2f", writeUpperLimit).replace(".", "").padStart(4, '0')
			return writeLowerLimitStr + writeUpperLimitStr
		}

	fun setReadValue(data:String) {
		if (data.length != 8) return
		val list = data.chunked(8).map {
			val value = it.toFloat()
			return@map if (value == 0f) 0 else value / 100
		}
		binding?.readLowerLimitInput?.editText?.setText(list[0].toString())
		binding?.readUpperLimitInput?.editText?.setText(list[1].toString())
	}

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdv31Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdv31Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdv31Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv31)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv31, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdv31_fieldAdv31Title)
		val readChecked = typedArray.getBoolean(R.styleable.FieldAdv31_fieldAdv31ReadChecked, false)
		val writeChecked = typedArray.getBoolean(R.styleable.FieldAdv31_fieldAdv31WriteChecked, false)
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		binding?.readCheckbox?.isChecked = readChecked
		binding?.writeCheckbox?.isChecked = writeChecked
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