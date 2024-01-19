package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

// 註: 設定時 R16表狀態的"圧力遮断判定値書込禁止"要是關的
data class D87D50Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var pressureShutOffJudgmentValue:String = "" // 圧力遮断判定値(50-01~04) 13位

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 13) throw Exception("D50結果 = \"$data\"")
		pressureShutOffJudgmentValue = data
	}

	override fun toString():String {
		return data
	}
}
