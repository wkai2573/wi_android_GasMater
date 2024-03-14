package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D59Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var oneDayMaximumUsage:String = ""     // 1日最大使用量(59-01) 6位
	var oneDayMaximumUsageDate:String = "" // 1日最大使用量月日(59-02) 4位

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D59Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 10) throw Exception("D59結果 = \"$data\"")
		oneDayMaximumUsage = data.substring(0, 6)
		oneDayMaximumUsageDate = data.substring(6, 10)
	}

	override fun toString():String {
		return data
	}
}
