package com.wavein.gasmeter.ui.component

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv03Binding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.row.detail.R16DetailSheet
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 自訂View元件: 進階欄位_告警情報專用(R03)
class FieldAdv03 : LinearLayout {
	var binding:CustFieldAdv03Binding? = null

	var _readValue:String = ""

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdv03Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdv03Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdv03Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv03)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv03, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdv03_fieldAdv03Title)
		val readValue = typedArray.getString(R.styleable.FieldAdv03_fieldAdv03ReadValue) ?: ""
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		setReadValue(readValue)
	}

	fun setReadValue(data:String) {
		if (data.length != 16) return
		_readValue = data
		val alarmInfoList = data.chunked(8)
		binding?.row1AlarmInfo?.text = alarmInfoList[0]
		binding?.row2AlarmInfo?.text = alarmInfoList[1]
	}

}