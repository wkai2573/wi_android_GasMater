package com.wavein.gasmeter.data.model

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import com.wavein.gasmeter.tools.Color_Success

data class MeterGroup(
	val group:String,
	val meterRows:List<MeterRow>
) {
	// 總數 & 已抄表
	private val totalCount:Int get() = meterRows.count()
	private val readCount:Int get() = meterRows.count { it.degreeRead }

	// 已抄數量提示
	val allRead:Boolean get() = readCount == totalCount
	val readTip:String get() = "${readCount}/${totalCount}"
	val readTipColor:Int get() = if (allRead) Color_Success else Color.RED
	val readTipSpannable:SpannableString
		get() {
			val spannable = SpannableString(readTip)
			val color = ForegroundColorSpan(readTipColor)
			spannable.setSpan(color, 0, readTip.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			return spannable
		}
	val groupWithTip:SpannableString
		get() = SpannableString.valueOf(SpannableStringBuilder().append("$group (").append(readTipSpannable).append(")"))

	// combo顯示內容
	override fun toString():String {
		return this.group
	}
}