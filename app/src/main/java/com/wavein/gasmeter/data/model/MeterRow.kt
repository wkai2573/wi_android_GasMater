package com.wavein.gasmeter.data.model

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import com.wavein.gasmeter.tools.Color_Success
import com.wavein.gasmeter.tools.toBoolean10
import com.wavein.gasmeter.tools.toString10
import java.text.DecimalFormat

data class MeterRow(

	val group:String,
	val groupName:String? = null,
	val queue:String,
	val custId:String,
	val custName:String,
	val custAddr:String,
	val remark:String? = null,

	val meterId:String,
	val meterDegree:Float? = null,
	val meterReadTime:String? = null,

	val lastMeterDegree:Float? = null,
	val lastMeterReadTime:String? = null,
	val isManualMeterDegree:Boolean? = null,
	val callingChannel:String? = null,
	var electricFieldStrength:String? = null,


	val businessesCode:String? = null,
	val dash:String? = null,


	val alarmInfo1:String? = null,
	val alarmInfo2:String? = null,
	val batteryVoltageDropAlarm:Boolean? = null,
	val innerPipeLeakageAlarm:Boolean? = null,
	val shutoff:Boolean? = null,
	val pressureValue:String? = null,
	val shutdownHistory1:String? = null,
	val shutdownHistory2:String? = null,
	val shutdownHistory3:String? = null,
	val shutdownHistory4:String? = null,
	val shutdownHistory5:String? = null,
	val meterStatus:String? = null,
	val hourlyUsage:String? = null,
	val maximumUsage:String? = null,
	val maximumUsageTime:String? = null,
	val oneDayMaximumUsage:String? = null,
	val oneDayMaximumUsageDate:String? = null,
	val registerFuseFlowRate1:String? = null,
	val registerFuseFlowRate2:String? = null,
	
	val pressureShutOffJudgmentValue:String? = null,

	) {

	val degreeUsed get() = if (meterDegree == null || lastMeterDegree == null) null else meterDegree - lastMeterDegree


	val degreeRead:Boolean get() = meterDegree != null
	val degreeNegative:Boolean get() = meterDegree != null && (degreeUsed ?: 0f) < 0f
	val readTip:String get() = if (degreeRead) "已抄表" else "未抄表"
	val readTipColor:Int get() = if (degreeRead) Color_Success else Color.RED
	private val readTipSpannable:SpannableString
		get() {
			val spannable = SpannableString(readTip)
			val color = ForegroundColorSpan(readTipColor)
			spannable.setSpan(color, 0, readTip.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			return spannable
		}
	val queueAndIdWithTip:SpannableString
		get() = SpannableString.valueOf(SpannableStringBuilder().append("$this ").append(readTipSpannable))


	val error:String
		get() {
			return if (degreeNegative) {
				val format = DecimalFormat("0.#")
				val degreeUsedStr = format.format(degreeUsed)
				"使用量負數: $degreeUsedStr"
			} else {
				""
			}
		}


	override fun toString():String {
		return queue
	}

	companion object {

		fun data2BitsMap(data:String, charKeyLetter:String):Map<String, Map<String, Boolean>> {
			val bitsMap = mutableMapOf<String, Map<String, Boolean>>()
			for (i in data.indices) {
				val charIndex = "$charKeyLetter${data.length - i}"
				val b4 = (data[i].code and 0b00001000) != 0
				val b3 = (data[i].code and 0b00000100) != 0
				val b2 = (data[i].code and 0b00000010) != 0
				val b1 = (data[i].code and 0b00000001) != 0
				bitsMap[charIndex] = mapOf("b4" to b4, "b3" to b3, "b2" to b2, "b1" to b1)
			}
			return bitsMap
		}


		fun bitsMap2Data(bitsMap:Map<String, Map<String, Boolean>>):String {
			var data = ""
			bitsMap.forEach { (charKey, bMap) ->
				val b4 = if (bMap["b4"] == true) 0b1000 else 0b0
				val b3 = if (bMap["b3"] == true) 0b0100 else 0b0
				val b2 = if (bMap["b2"] == true) 0b0010 else 0b0
				val b1 = if (bMap["b1"] == true) 0b0001 else 0b0
				val charCode = 0b01000000 or b4 or b3 or b2 or b1
				val char = charCode.toChar()
				data += char
			}
			return data
		}
	}

}


fun Map<String, String>.toMeterRow():MeterRow? {
	try {
		val csvRow = this
		return MeterRow(
			queue = csvRow["抄錶順路"] ?: return null,
			custId = csvRow["客戶編號"] ?: return null,
			custName = csvRow["姓名"] ?: return null,
			dash = csvRow["-"] ?: return null,
			custAddr = csvRow["地址"] ?: return null,
			remark = csvRow["記事"] ?: return null,
			meterId = csvRow["通信ID"] ?: return null,
			group = csvRow["群組號"] ?: return null,
			groupName = csvRow["區域名稱"] ?: return null,
			meterDegree = csvRow["抄錶數值"]?.toFloatOrNull(),
			isManualMeterDegree = csvRow["人工輸入"].toBoolean10(),
			meterReadTime = csvRow["抄錶日期"] ?: return null,
			batteryVoltageDropAlarm = csvRow["電池電壓警報"].toBoolean10(),
			innerPipeLeakageAlarm = csvRow["洩漏警報"].toBoolean10(),
			shutoff = csvRow["遮斷"].toBoolean10(),
			callingChannel = csvRow["通信CH"] ?: return null,
			businessesCode = csvRow["事業体碼"] ?: return null,
			lastMeterDegree = csvRow["前次抄表數值"]?.toFloatOrNull(),
			lastMeterReadTime = csvRow["前次抄表日期"] ?: return null,

			alarmInfo1 = csvRow["告警1"] ?: return null,
			alarmInfo2 = csvRow["告警2"] ?: return null,
			pressureValue = csvRow["壓力值"] ?: return null,
			shutdownHistory1 = csvRow["遮斷履歷1"] ?: return null,
			shutdownHistory2 = csvRow["遮斷履歷2"] ?: return null,
			shutdownHistory3 = csvRow["遮斷履歷3"] ?: return null,
			shutdownHistory4 = csvRow["遮斷履歷4"] ?: return null,
			shutdownHistory5 = csvRow["遮斷履歷5"] ?: return null,
		)
	} catch (e:Exception) {
		e.printStackTrace()
		return null
	}
}


fun List<Map<String, String>>.toMeterRows():List<MeterRow> {
	return this.mapNotNull { csvRow -> csvRow.toMeterRow() }
}


private fun MeterRow.toCsvRow():Map<String, String> {
	return mapOf(
		"抄錶順路" to this.queue,
		"客戶編號" to this.custId,
		"姓名" to this.custName,
		"-" to this.dash.orEmpty(),
		"地址" to this.custAddr,
		"記事" to this.remark.orEmpty(),
		"通信ID" to this.meterId,
		"群組號" to this.group,
		"區域名稱" to this.groupName.orEmpty(),
		"抄錶數值" to (this.meterDegree?.toString() ?: ""),
		"人工輸入" to this.isManualMeterDegree.toString10(),
		"抄錶日期" to this.meterReadTime.orEmpty(),
		"電池電壓警報" to this.batteryVoltageDropAlarm.toString10(),
		"洩漏警報" to this.innerPipeLeakageAlarm.toString10(),
		"遮斷" to this.shutoff.toString10(),
		"通信CH" to this.callingChannel.orEmpty(),
		"事業体碼" to this.businessesCode.orEmpty(),
		"前次抄表數值" to (this.lastMeterDegree?.toString() ?: ""),
		"前次抄表日期" to this.lastMeterReadTime.orEmpty(),
		"使用量" to (this.degreeUsed?.toString() ?: ""),
		"告警1" to this.alarmInfo1.orEmpty(),
		"告警2" to this.alarmInfo2.orEmpty(),
		"壓力值" to this.pressureValue.orEmpty(),
		"遮斷履歷1" to this.shutdownHistory1.orEmpty(),
		"遮斷履歷2" to this.shutdownHistory2.orEmpty(),
		"遮斷履歷3" to this.shutdownHistory3.orEmpty(),
		"遮斷履歷4" to this.shutdownHistory4.orEmpty(),
		"遮斷履歷5" to this.shutdownHistory5.orEmpty(),
	)
}


fun List<MeterRow>.toCsvRows():List<Map<String, String>> {
	return this.map { it.toCsvRow() }
}


fun List<MeterRow>.toMeterGroups():List<MeterGroup> {
	return this
		.groupBy { it.group }
		.map { (group, meterRows) -> MeterGroup(group, meterRows) }
}


fun List<Map<String, String>>.csvToMeterGroups():List<MeterGroup> {
	return this.toMeterRows().toMeterGroups()
}
