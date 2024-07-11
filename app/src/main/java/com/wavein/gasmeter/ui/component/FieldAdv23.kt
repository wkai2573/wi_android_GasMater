package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdv23Binding


class FieldAdv23 : LinearLayout {
	var binding:CustFieldAdv23Binding? = null

	var _readValue:String = ""

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdv23Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdv23Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdv23Binding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {

		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv23)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdv23, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdv23_fieldAdv23Title)
		val readValue = typedArray.getString(R.styleable.FieldAdv23_fieldAdv23ReadValue) ?: ""
		typedArray.recycle()

		binding?.titleTv?.text = title
		setReadValue(readValue)
	}

	fun setReadValue(data:String) {
		if (data.length != 65) return
		_readValue = data
		val shutdownHistoryList = data.chunked(13)
		val shutdownHistory1 = shutdownHistoryList[0]
		val shutdownHistory2 = shutdownHistoryList[1]
		val shutdownHistory3 = shutdownHistoryList[2]
		val shutdownHistory4 = shutdownHistoryList[3]
		val shutdownHistory5 = shutdownHistoryList[4]
		setTextView(shutdownHistory1, binding?.tv1Event, binding?.tv1Year, binding?.tv1Month, binding?.tv1Day, binding?.tv1Hour, binding?.tv1Flow)
		setTextView(shutdownHistory2, binding?.tv2Event, binding?.tv2Year, binding?.tv2Month, binding?.tv2Day, binding?.tv2Hour, binding?.tv2Flow)
		setTextView(shutdownHistory3, binding?.tv3Event, binding?.tv3Year, binding?.tv3Month, binding?.tv3Day, binding?.tv3Hour, binding?.tv3Flow)
		setTextView(shutdownHistory4, binding?.tv4Event, binding?.tv4Year, binding?.tv4Month, binding?.tv4Day, binding?.tv4Hour, binding?.tv4Flow)
		setTextView(shutdownHistory5, binding?.tv5Event, binding?.tv5Year, binding?.tv5Month, binding?.tv5Day, binding?.tv5Hour, binding?.tv5Flow)
	}

	private fun setTextView(
		shutdownHistory:String,
		tv1Event:TextView?, tv1Year:TextView?, tv1Month:TextView?, tv1Day:TextView?, tv1Hour:TextView?, tv1Flow:TextView?,
	) {
		tv1Event?.text = shutdownHistoryMeaning(shutdownHistory)
		tv1Year?.text = shutdownHistory.substring(1, 2)
		tv1Month?.text = shutdownHistory.substring(2, 4)
		tv1Day?.text = shutdownHistory.substring(4, 6)
		tv1Hour?.text = shutdownHistory.substring(6, 8)
		tv1Flow?.text = shutdownHistory.substring(8, 13)
	}

	private fun shutdownHistoryMeaning(shutdownHistory:String?):String {
		if (shutdownHistory.isNullOrEmpty()) return ""
		return when (shutdownHistory[0]) {
			'0' -> "0:無遮斷"
			'1' -> "1:內管洩漏遮斷"
			'2' -> "2:超出合計最大流量遮斷"
			'3' -> "3:超出個別最大流量遮斷"
			'4' -> "4:超出安全繼續時間遮斷"
			'5' -> "5:感震遮斷"
			'6' -> "6:壓力低下遮斷"
			'7' -> "7:警報器遮斷"
			'8' -> "8:常時電池電壓低下遮斷"
			'9' -> "9:通常電池電壓低下遮斷"
			'A' -> "A:壓力上昇遮斷"
			else -> ""
		}
	}
}