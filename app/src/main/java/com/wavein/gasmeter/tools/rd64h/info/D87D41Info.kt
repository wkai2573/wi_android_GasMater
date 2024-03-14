package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D41Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var alarmInfo1:String = "" // アラーム情報1(03-01) 8位

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D41Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 8) throw Exception("D41結果 = \"$data\"")
		alarmInfo1 = data
	}

	override fun toString():String {
		return data
	}
}
