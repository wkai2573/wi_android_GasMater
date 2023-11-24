package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustErrorTextviewBinding


// 自訂View元件: ErrorTextView
class ErrorTextView : LinearLayout {
	private var binding:CustErrorTextviewBinding? = null

	fun setText(text:String?, @ColorInt color:Int? = null) {
		if (text.isNullOrEmpty()) {
			binding?.errTvCard?.visibility = View.GONE
		} else {
			binding?.errTvCard?.visibility = View.VISIBLE
			binding?.errTv?.text = text
		}
		color?.let { binding?.errTv?.setTextColor(it) }
	}

	constructor(context:Context?) : super(context) {
		binding = CustErrorTextviewBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustErrorTextviewBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustErrorTextviewBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.ErrorTextView)
		else
			context.obtainStyledAttributes(attrs, R.styleable.ErrorTextView, defStyle, 0)
		val text = typedArray.getString(R.styleable.ErrorTextView_errorText)
		typedArray.recycle()
		// ui
		setText(text)
	}
}