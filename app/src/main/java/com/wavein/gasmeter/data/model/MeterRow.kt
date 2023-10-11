package com.wavein.gasmeter.data.model

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import com.wavein.gasmeter.tools.Color_Success
import com.wavein.gasmeter.tools.toBoolean10
import com.wavein.gasmeter.tools.toString10

data class MeterRow(
	val group:String,                            // 群組
	val queue:Int,                               // 抄表順序
	val custId:String,                           // 客戶編號
	val custName:String,                         // 姓名
	val custAddr:String,                         // 地址

	val meterId:String,                          // 瓦斯表ID
	val meterDegree:Float? = null,               // 抄表值
	val meterStateRaw:String? = null,            // 表狀態(16:メーター状態)
	val meterReadTime:String? = null,            // 抄表時間

	val batteryVoltageDropAlarm:Boolean? = null, // 電池電壓警報
	val innerPipeLeakageAlarm:Boolean? = null,   // 洩漏警報
	val shutoff:Boolean? = null,                 // 遮斷
	val shutoffTime:String? = null,              // 遮斷時間
) {

	// 已抄數量提示
	val degreeRead:Boolean get() = meterDegree != null // 已抄表
	val readTip:String get() = if (degreeRead) "已抄表" else "未抄表"
	val readTipColor:Int get() = if (degreeRead) Color_Success else Color.RED
	val readTipSpannable:SpannableString
		get() {
			val spannable = SpannableString(readTip)
			val color = ForegroundColorSpan(readTipColor)
			spannable.setSpan(color, 0, readTip.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			return spannable
		}
	val queueAndIdWithTip:SpannableString
		get() = SpannableString.valueOf(SpannableStringBuilder().append("$this ").append(readTipSpannable))

	// combo下拉時顯示內容
	override fun toString():String {
		return "$queue" // "$queue ($meterId)"
	}



	// val batteryVoltageDropShutoff:Boolean,
	// val innerPipeLeakageShutoff:Boolean,

	init {
		// 分析meterStateRaw 做成其他變數
		parseMeterStateRaw()
	}

	fun parseMeterStateRaw() {}

}

// csvRow 轉 meterRow
fun Map<String, String>.toMeterRow():MeterRow? {
	try {
		val csvRow = this
		return MeterRow(
			group = csvRow["群組"] ?: return null,
			queue = csvRow["抄表順序"]?.toInt() ?: return null,
			custId = csvRow["客戶編號"] ?: return null,
			custName = csvRow["姓名"] ?: return null,
			custAddr = csvRow["地址"] ?: return null,
			meterId = csvRow["瓦斯表ID"] ?: return null,
			meterDegree = csvRow["抄表值"]?.toFloatOrNull(),
			meterStateRaw = csvRow["表狀態"],
			meterReadTime = csvRow["抄表時間"],
			batteryVoltageDropAlarm = csvRow["電池電壓警報"].toBoolean10(),
			innerPipeLeakageAlarm = csvRow["洩漏警報"].toBoolean10(),
			shutoff = csvRow["遮斷"].toBoolean10(),
			shutoffTime = csvRow["遮斷時間"],
		)
	} catch (e:Exception) {
		e.printStackTrace()
		return null
	}
}

// csvRows 轉 meterRows
fun List<Map<String, String>>.toMeterRows():List<MeterRow> {
	return this.mapNotNull { csvRow -> csvRow.toMeterRow() }
}

// meterRow 轉 csvRow
private fun MeterRow.toCsvRow():Map<String, String> {
	return mapOf(
		"群組" to this.group,
		"抄表順序" to this.queue.toString(),
		"客戶編號" to this.custId,
		"姓名" to this.custName,
		"地址" to this.custAddr,
		"瓦斯表ID" to this.meterId,
		"抄表值" to (this.meterDegree?.toString() ?: ""),
		"表狀態" to this.meterStateRaw.orEmpty(),
		"抄表時間" to this.meterReadTime.orEmpty(),
		"電池電壓警報" to this.batteryVoltageDropAlarm.toString10(),
		"洩漏警報" to this.innerPipeLeakageAlarm.toString10(),
		"遮斷" to this.shutoff.toString10(),
		"遮斷時間" to this.shutoffTime.orEmpty(),
	)
}

// meterRows 轉 csvRows
fun List<MeterRow>.toCsvRows():List<Map<String, String>> {
	return this.map { it.toCsvRow() }
}

// meterRows 轉 meterGroups
fun List<MeterRow>.toMeterGroups():List<MeterGroup> {
	return this
		.groupBy { it.group }
		.map { (group, meterRows) -> MeterGroup(group, meterRows) }
}

// csvRows 轉 meterGroups
fun List<Map<String, String>>.csvToMeterGroups():List<MeterGroup> {
	return this.toMeterRows().toMeterGroups()
}
