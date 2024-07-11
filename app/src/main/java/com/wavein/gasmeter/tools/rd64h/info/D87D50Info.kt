package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel


data class D87D50Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var pressureShutOffJudgmentValue:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D50Info")
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
