package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv51Binding

// 自訂View元件: 進階欄位_要求(R51)
class FieldAdv51 : LinearLayout {
	var binding:CustFieldAdv51Binding? = null

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdv51Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdv51Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdv51Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv51)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv51, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdv51_fieldAdv51Title)
		val readValue = typedArray.getString(R.styleable.FieldAdv51_fieldAdv51ReadValue) ?: ""
		val unit = typedArray.getString(R.styleable.FieldAdv51_fieldAdv51Unit) ?: ""
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		setReadValue(readValue)
		binding?.readUnitTv?.text = unit
	}

	fun setReadValue(data:String) {
		binding?.readValueTv?.text = data
	}

}