package com.wavein.gasmeter.ui.component

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.FieldNormalBinding


// 自訂View元件: Field
class Field : LinearLayout {
	private var binding:FieldNormalBinding? = null

	fun setValue(text:String, @ColorInt color:Int? = null) {
		binding?.valueTv?.text = text
		color?.let { binding?.valueTv?.setTextColor(it) }
	}

	constructor(context:Context?) : super(context) {
		binding = FieldNormalBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = FieldNormalBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = FieldNormalBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.Field)
		else
			context.obtainStyledAttributes(attrs, R.styleable.Field, defStyle, 0)
		val title = typedArray.getString(R.styleable.Field_title)
		val value = typedArray.getString(R.styleable.Field_value)
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		binding?.valueTv?.text = value
	}
}