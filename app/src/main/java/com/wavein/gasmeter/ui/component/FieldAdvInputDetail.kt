package com.wavein.gasmeter.ui.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.CustFieldAdvInputDetailBinding

// 自訂View元件: 進階讀取設定欄位(通用)
class FieldAdvInputDetail : LinearLayout {
	var binding:CustFieldAdvInputDetailBinding? = null

	val readValue get() = binding?.readValueTv?.text?.toString() ?: ""
	val writeValue get() = binding?.writeValueInput?.editText?.text?.toString() ?: ""

	fun setReadValue(text:String, @ColorInt color:Int? = null) {
		binding?.readValueTv?.text = text
		color?.let { binding?.readValueTv?.setTextColor(it) }
	}

	fun setWriteValue(text:String) {
		binding?.writeValueInput?.editText?.setText(text)
	}

	constructor(context:Context?) : super(context) {
		binding = CustFieldAdvInputDetailBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
	}

	constructor(context:Context?, attrs:AttributeSet?) : super(context, attrs) {
		binding = CustFieldAdvInputDetailBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs)
	}

	constructor(context:Context?, attrs:AttributeSet?, defStyle:Int) : super(context, attrs, defStyle) {
		binding = CustFieldAdvInputDetailBinding.inflate(LayoutInflater.from(getContext()), this, false)
		addView(binding?.root)
		initLayout(attrs, defStyle)
	}

	private fun initLayout(attrs:AttributeSet?, defStyle:Int? = null) {
		// 取得 xml 傳入參數
		val typedArray = if (defStyle == null)
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdvInputDetail)
		else
			context.obtainStyledAttributes(attrs, R.styleable.FieldAdvInputDetail, defStyle, 0)
		val title = typedArray.getString(R.styleable.FieldAdvInputDetail_fieldAdvTitle)
		val readLine = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_readLine, true)
		val readValue = typedArray.getString(R.styleable.FieldAdvInputDetail_readValue)
		val readDetailBtn = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_readDetailBtn, false)
		val readChecked = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_readChecked, false)
		val writeLine = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_writeLine, false)
		val writeValue = typedArray.getString(R.styleable.FieldAdvInputDetail_writeValue)
		val writeDetailBtn = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_writeDetailBtn, false)
		val writeChecked = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_writeChecked, false)
		val bottomDivider = typedArray.getBoolean(R.styleable.FieldAdvInputDetail_bottomDivider, true)
		typedArray.recycle()
		// ui
		binding?.titleTv?.text = title
		binding?.readLine?.visibility = if (readLine) View.VISIBLE else View.GONE
		binding?.readValueTv?.text = readValue
		binding?.readDetailBtn?.visibility = if (readDetailBtn) View.VISIBLE else View.GONE
		binding?.readCheckbox?.isChecked = readChecked
		binding?.writeLine?.visibility = if (writeLine) View.VISIBLE else View.GONE
		if (writeValue == "GONE") {
			binding?.writeValueInput?.visibility = View.INVISIBLE
		} else {
			binding?.writeValueInput?.editText?.setText(writeValue)
		}
		binding?.writeDetailBtn?.visibility = if (writeDetailBtn) View.VISIBLE else View.GONE
		binding?.writeCheckbox?.isChecked = writeChecked
		binding?.bottomDivider?.visibility = if (bottomDivider) View.VISIBLE else View.GONE
	}
}