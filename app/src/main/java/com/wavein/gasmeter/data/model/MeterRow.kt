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
	// 原csv欄位(不照順序,*號為csv有保存欄位名)
	val group:String,                            // 群組 *群組號
	val groupName:String? = null,                // 群組名稱 *區域名稱
	val queue:String,                            // *抄錶順路
	val custId:String,                           // *客戶編號
	val custName:String,                         // *姓名
	val custAddr:String,                         // *地址
	val remark:String? = null,                   // *記事

	val meterId:String,                          // 瓦斯表ID *通信ID
	val meterDegree:Float? = null,               // 抄表值 *抄錶數值
	val meterReadTime:String? = null,            // 抄表時間 *抄錶日期

	val lastMeterDegree:Float? = null,           // 前次抄表值 *前次抄表數值
	val lastMeterReadTime:String? = null,        // 前次抄表時間 *前次抄表日期
	val isManualMeterDegree:Boolean? = null,     // *人工輸入
	var electricFieldStrength:String? = null,    // 電波強度

	// 原csv欄位,未使用
	val callingChannel:String? = null,           // *通信CH
	val businessesCode:String? = null,           // *事業体碼
	val dash:String? = null,                     // -

	// 進階欄位
	val alarmInfo1:String? = null,               // *告警1 (アラーム情報1 03-01)
	val alarmInfo2:String? = null,               // *告警2 (アラーム情報2 03-02) (讀取時用R24)
	val batteryVoltageDropAlarm:Boolean? = null, // *電池電壓警報
	val innerPipeLeakageAlarm:Boolean? = null,   // *洩漏警報
	val shutoff:Boolean? = null,                 // *遮斷
	val pressureValue:String? = null,            // *壓力值 (現在圧力値 51-01)
	val shutdownHistory1:String? = null,         // *遮斷履歷1 (遮断5回分履歴 23-01)
	val shutdownHistory2:String? = null,         // *遮斷履歷2
	val shutdownHistory3:String? = null,         // *遮斷履歷3
	val shutdownHistory4:String? = null,         // *遮斷履歷4
	val shutdownHistory5:String? = null,         // *遮斷履歷5
	val meterStatus:String? = null,              // 表狀態 (メーター状態 16-02)
	val hourlyUsage:String? = null,              // 每小時使用量 (時間使用量 57-01)
	val maximumUsage:String? = null,             // 最大使用量 (最大使用量 58-01)
	val oneDayMaximumUsage:String? = null,       // 1日最大使用量 (1日最大使用量 59-01)
	val registerFuseFlowRate1:String? = null,    // 登錄母火流量1 (登録口火流量1 24-06) (設定時用S31)
	val registerFuseFlowRate2:String? = null,    // 登錄母火流量2
	/** 壓力遮斷判定值 (圧力遮断判定値 50-01~50-05)
	 * 3bit (圧力低下判定値 50-01)
	 * 3bit (低下復圧状態判定値 50-02)
	 * 3bit (上昇復圧状態判定値 50-03)
	 * 3bit (圧力上昇判定値 50-04)
	 * 1bit (圧力遮断判定時間 50-05)
	 */
	val pressureShutOffJudgmentValue:String? = null,

	) {
	// 使用量
	val degreeUsed get() = if (meterDegree == null || lastMeterDegree == null) null else meterDegree - lastMeterDegree

	// 欄位顯示用
	val shutdownHistoryForFieldShow:String
		get() = "1. $shutdownHistory1 ${shutdownHistoryMeaning(shutdownHistory1)}\n" +
				"2. $shutdownHistory2 ${shutdownHistoryMeaning(shutdownHistory2)}\n" +
				"3. $shutdownHistory3 ${shutdownHistoryMeaning(shutdownHistory3)}\n" +
				"4. $shutdownHistory4 ${shutdownHistoryMeaning(shutdownHistory4)}\n" +
				"5. $shutdownHistory5 ${shutdownHistoryMeaning(shutdownHistory5)}"

	private fun shutdownHistoryMeaning(shutdownHistory:String?):String {
		if (shutdownHistory.isNullOrEmpty()) return ""
		return when (shutdownHistory[0]) {
			'0' -> "無遮斷"
			'1' -> "內管洩漏遮斷"
			'2' -> "超出合計最大流量遮斷"
			'3' -> "超出個別最大流量遮斷"
			'4' -> "超出安全繼續時間遮斷"
			'5' -> "感震遮斷"
			'6' -> "壓力低下遮斷"
			'7' -> "警報器遮斷"
			'8' -> "常時電池電壓低下遮斷"
			'9' -> "通常電池電壓低下遮斷"
			'A' -> "壓力上昇遮斷"
			else -> ""
		}
	}

	val alarmInfoForFieldShow:String
		get() = "1. $alarmInfo1 ${alarmInfoMeaning(alarmInfo1)}\n" +
				"2. $alarmInfo2 ${alarmInfoMeaning(alarmInfo2)}"

	private fun alarmInfoMeaning(alarmInfo:String?):String {
		if (alarmInfo == null) return ""
		val alarmInfoDetail = alarmInfoDetail(alarmInfo)
		val list = mutableListOf<String>()
		if (alarmInfoDetail["A8"]?.get("b4") == true) list.add("常時電池電壓低下遮斷")
		if (alarmInfoDetail["A4"]?.get("b4") == true) list.add("通常電池電壓低下遮斷")
		if (alarmInfoDetail["A2"]?.get("b4") == true) list.add("復歸洩漏確認遮斷")
		if (alarmInfoDetail["A1"]?.get("b4") == true) list.add("超出安全繼續時間遮斷")
		if (alarmInfoDetail["A2"]?.get("b3") == true) list.add("警報器遮斷")
		if (alarmInfoDetail["A1"]?.get("b3") == true) list.add("超出個別最大流量遮斷")
		if (alarmInfoDetail["A4"]?.get("b2") == true) list.add("測試遮斷")
		if (alarmInfoDetail["A3"]?.get("b2") == true) list.add("壓力上昇遮斷")
		if (alarmInfoDetail["A2"]?.get("b2") == true) list.add("壓力低下遮斷")
		if (alarmInfoDetail["A1"]?.get("b2") == true) list.add("超出合計最大流量遮斷")
		if (alarmInfoDetail["A8"]?.get("b1") == true) list.add("內管洩漏遮斷")
		if (alarmInfoDetail["A6"]?.get("b1") == true) list.add("解除後遮斷")
		if (alarmInfoDetail["A4"]?.get("b1") == true) list.add("遮斷中有脈衝遮斷")
		if (alarmInfoDetail["A3"]?.get("b1") == true) list.add("中心遮斷")
		if (alarmInfoDetail["A2"]?.get("b1") == true) list.add("感震遮斷")
		return list.joinToString("; ")
	}

	// 已抄數量提示
	val degreeRead:Boolean get() = meterDegree != null // 已抄表
	val degreeNegative:Boolean get() = meterDegree != null && (degreeUsed ?: 0f) < 0f // 使用量負數
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

	// 異常提示
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

	// combo下拉時顯示內容
	override fun toString():String {
		return queue
	}

	companion object {
		// 分析告警情報
		fun alarmInfoDetail(alarmInfo:String):Map<String, Map<String, Boolean>> {
			val alarmInfoDetail = mutableMapOf<String, Map<String, Boolean>>()
			for (i in alarmInfo.indices) {
				val infoDig = "A${8 - i}"
				val b4 = (alarmInfo[i].code and 0b00001000) != 0
				val b3 = (alarmInfo[i].code and 0b00000100) != 0
				val b2 = (alarmInfo[i].code and 0b00000010) != 0
				val b1 = (alarmInfo[i].code and 0b00000001) != 0
				alarmInfoDetail[infoDig] = mapOf("b4" to b4, "b3" to b3, "b2" to b2, "b1" to b1)
			}
			return alarmInfoDetail
		}
	}

}

// csvRow 轉 meterRow
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
			//degreeUsed = csvRow["使用量"]?.toFloatOrNull(),
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

// csvRows 轉 meterRows
fun List<Map<String, String>>.toMeterRows():List<MeterRow> {
	return this.mapNotNull { csvRow -> csvRow.toMeterRow() }
}

// meterRow 轉 csvRow
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
