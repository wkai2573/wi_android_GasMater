package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdvInputBinding


// 自訂View元件: FieldAdvStr
class FieldAdvInput : LinearLayout {
	private var binding:CustFieldAdvInputBinding? = null

	val readCheckbox get() = binding?.readCheckbox
	val writeCheckbox get() = binding?.writeCheckbox
	val writeValue get () = binding?.writeValueInput?.editText?.text?.toString() ?: ""

	fun setReadValue(text:String, @ColorInt color:Int? = null) {
		binding?.readValueTv?.text = text
		color?.let { binding?.readValueTv?.setTextColor(it) }
	}

	fun setWriteValue(text:String) {
		binding?.writeValueInput?.editText?.setText(text)
	}

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdvInputBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdvInputBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdvInputBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdvInput)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdvInput, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdvInput_advTitle)
		val readLine = typedArray.getBoolean(R.styleable.FieldAdvInput_readLine, true)
		val readValue = typedArray.getString(R.styleable.FieldAdvInput_readValue)
		val readDetailBtn = typedArray.getBoolean(R.styleable.FieldAdvInput_readDetailBtn, false)
		val readChecked = typedArray.getBoolean(R.styleable.FieldAdvInput_readChecked, false)
		val writeLine = typedArray.getBoolean(R.styleable.FieldAdvInput_writeLine, false)
		val writeValue = typedArray.getString(R.styleable.FieldAdvInput_writeValue)
		val writeDetailBtn = typedArray.getBoolean(R.styleable.FieldAdvInput_writeDetailBtn, false)
		val writeChecked = typedArray.getBoolean(R.styleable.FieldAdvInput_writeChecked, false)
		val bottomDivider = typedArray.getBoolean(R.styleable.FieldAdvInput_bottomDivider, true)
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		binding?.readLine?.visibility = if (readLine) View.VISIBLE else View.GONE
		binding?.readValueTv?.text = readValue
		binding?.readDetailBtn?.visibility = if (readDetailBtn) View.VISIBLE else View.GONE
		binding?.readCheckbox?.isChecked = readChecked
		binding?.writeLine?.visibility = if (writeLine) View.VISIBLE else View.GONE
		binding?.writeValueInput?.editText?.setText(writeValue)
		binding?.writeDetailBtn?.visibility = if (writeDetailBtn) View.VISIBLE else View.GONE
		binding?.writeCheckbox?.isChecked = writeChecked
		binding?.bottomDivider?.visibility = if (bottomDivider) View.VISIBLE else View.GONE
	}
}