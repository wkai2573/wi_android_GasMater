package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustDetailOptionBinding
import com.wavein.gasmeter.ui.meterwork.row.detail.OptionEnum
import java.security.cert.PKIXRevocationChecker.Option


// 自訂View元件: Field
class DetailOption : LinearLayout {
	private var binding:CustDetailOptionBinding? = null

	val selected
		get():OptionEnum = when (binding?.toggleGroup?.checkedButtonId) {
			R.id.btn1 -> OptionEnum.Keep
			R.id.btn2 -> OptionEnum.Enable
			R.id.btn3 -> OptionEnum.Disable
			else -> OptionEnum.Keep
		}

	fun setSelected(selected:OptionEnum) {
		when (selected) {
			OptionEnum.Keep -> binding?.toggleGroup?.check(R.id.btn1)
			OptionEnum.Enable -> binding?.toggleGroup?.check(R.id.btn2)
			OptionEnum.Disable -> binding?.toggleGroup?.check(R.id.btn3)
		}
	}

	fun setEditable(editable:Boolean) {
		binding?.toggleGroup?.isEnabled = editable
		binding?.btn1?.visibility = if (editable) VISIBLE else GONE
	}

	constructor(context:Context?) : super(context) {
		binding = CustDetailOptionBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustDetailOptionBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustDetailOptionBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.DetailOption)
		else
			context.obtainStyledAttributes(attrs, R.styleable.DetailOption, defStyle, 0)
		val title = typedArray.getString(R.styleable.DetailOption_detailOptionTitle)
		val editable = typedArray.getBoolean(R.styleable.DetailOption_detailOptionEditable, true)
		val optionText = typedArray.getString(R.styleable.DetailOption_detailOptionText) ?: "維持|啟用|關閉"
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		setEditable(editable)
		val optionTextList = optionText.split("|")
		binding?.btn1?.text = optionTextList.getOrElse(0) { "" }
		binding?.btn2?.text = optionTextList.getOrElse(1) { "" }
		binding?.btn3?.text = optionTextList.getOrElse(2) { "" }
	}
}