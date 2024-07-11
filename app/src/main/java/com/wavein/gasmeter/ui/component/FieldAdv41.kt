package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv41Binding


class FieldAdv41 : LinearLayout {
	var binding:CustFieldAdv41Binding? = null

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdv41Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdv41Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdv41Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {

		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv41)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv41, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdv41_fieldAdv41Title)
		typedArray.recycle()

		binding?.titleTv?.text = title
	}
}