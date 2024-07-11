package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv16Binding


class FieldAdv16 : LinearLayout {
	var binding:CustFieldAdv16Binding? = null

	val readValue get() = binding?.readValueTv?.text.toString()
	val writeValue get() = binding?.writeValueTv?.text.toString()

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdv16Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdv16Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdv16Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {

		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv16)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv16, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdv16_fieldAdv16Title)
		val readValue = typedArray.getString(R.styleable.FieldAdv16_fieldAdv16ReadValue) ?: ""
		typedArray.recycle()

		binding?.titleTv?.text = title
		setReadValue(readValue)
	}

	fun setReadValue(data:String) {
		binding?.readValueTv?.text = data
	}

	fun setWriteValue(data:String) {
		binding?.writeValueTv?.text = data
	}

}