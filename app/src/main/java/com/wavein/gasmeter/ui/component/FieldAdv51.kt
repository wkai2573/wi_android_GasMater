package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.gridlayout.widget.GridLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv51Binding

// 自訂View元件: 進階欄位_要求_可增加顯示欄位(R51,R57,R58,R59)
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
		readHeaders = typedArray.getString(R.styleable.FieldAdv51_fieldAdv51ReadHeaders)?.split(',') ?: emptyList()
		readValues = typedArray.getString(R.styleable.FieldAdv51_fieldAdv51ReadValues)?.split(',') ?: emptyList()
		readMap = readHeaders.zip(readValues).toMap()
		setReadMap(readMap)
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
	}

	private var readHeaders = emptyList<String>()
	private var readValues = emptyList<String>()
	private var readMap = mapOf<String, String>()

	fun setReadHeaders(readHeaders:List<String>, resetReadMap:Boolean = false) {
		this.readHeaders = readHeaders
		if (resetReadMap) {
			readMap = readHeaders.zip(readValues).toMap()
			setReadMap(readMap)
		}
	}

	fun setReadValues(readValues:List<String>, resetReadMap:Boolean = false) {
		this.readValues = readValues
		if (resetReadMap) {
			readMap = readHeaders.zip(readValues).toMap()
			setReadMap(readMap)
		}
	}

	fun setReadMap(readMap:Map<String, String>) {
		this.readMap = readMap
		this.readHeaders = readMap.keys.toList()
		this.readValues = readMap.values.toList()

		binding?.gridLayout?.removeAllViews()
		binding?.gridLayout?.columnCount = readMap.size + 1

		val inflater = LayoutInflater.from(context)
		this.readHeaders.forEachIndexed { index, header ->
			val headerTv = inflater.inflate(R.layout.item_tv_header, this, false) as TextView
			headerTv.text = header
			headerTv.layoutParams = GridLayout.LayoutParams(headerTv.layoutParams).apply {
				columnSpec = GridLayout.spec(index, 0.5f)
			}
			binding?.gridLayout?.addView(headerTv)
		}
		this.readValues.forEachIndexed { index, value ->
			val cellTv = inflater.inflate(R.layout.item_tv_cell, this, false) as TextView
			cellTv.text = value
			cellTv.layoutParams = GridLayout.LayoutParams(cellTv.layoutParams).apply {
				columnSpec = GridLayout.spec(index, 0.5f)
			}
			binding?.gridLayout?.addView(cellTv)
		}
	}

}