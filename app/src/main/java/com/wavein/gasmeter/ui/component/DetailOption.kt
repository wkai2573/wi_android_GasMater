package com.wavein.gasmeter.ui.component

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustDetailOptionBinding
import com.wavein.gasmeter.ui.meterwork.row.detail.OptionEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



class DetailOption : LinearLayout {
	private var binding:CustDetailOptionBinding? = null

	var readValue:Boolean? = null

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

	fun setBold(selected:OptionEnum) {
		binding?.btn1?.setTypeface(null, Typeface.NORMAL)
		binding?.btn2?.setTypeface(null, Typeface.NORMAL)
		binding?.btn3?.setTypeface(null, Typeface.NORMAL)
		when (selected) {
			OptionEnum.Keep -> binding?.btn1?.setTypeface(null, Typeface.BOLD_ITALIC)
			OptionEnum.Enable -> binding?.btn2?.setTypeface(null, Typeface.BOLD_ITALIC)
			OptionEnum.Disable -> binding?.btn3?.setTypeface(null, Typeface.BOLD_ITALIC)
		}
	}


	private fun refreshStyle() {
		binding?.btn2?.let { setBtnColor(it, selected == OptionEnum.Enable, readValue != true) }
		binding?.btn3?.let { setBtnColor(it, selected == OptionEnum.Disable, readValue != false) }
	}

	private fun setBtnColor(btn:MaterialButton, selected:Boolean, isRed:Boolean) {
		if (!selected) {

			btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#e0deec"))
			btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#f2f3f8"))
			btn.setTextColor(Color.parseColor("#6869a3"))
		} else {
			if (isRed) {

				btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_light_error))
				btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_light_errorContainer))
				btn.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_error))
			} else {

				btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_light_primary))
				btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ebeff6"))
				btn.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_primary))
			}
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

		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.DetailOption)
		else
			context.obtainStyledAttributes(attrs, R.styleable.DetailOption, defStyle, 0)
		val title = typedArray.getString(R.styleable.DetailOption_detailOptionTitle)
		val editable = typedArray.getBoolean(R.styleable.DetailOption_detailOptionEditable, true)
		val optionText = typedArray.getString(R.styleable.DetailOption_detailOptionText) ?: "維持|啟用|關閉"
		typedArray.recycle()

		binding?.titleTv?.text = title
		setEditable(editable)
		val optionTextList = optionText.split("|")
		binding?.btn1?.text = optionTextList.getOrElse(0) { "" }
		binding?.btn2?.text = optionTextList.getOrElse(1) { "" }
		binding?.btn3?.text = optionTextList.getOrElse(2) { "" }


		if (binding?.btn1?.text != "") {
			binding?.toggleGroup?.addOnButtonCheckedListener { group, checkedId, isChecked ->
				if (isChecked) refreshStyle()
			}
		}
	}
}