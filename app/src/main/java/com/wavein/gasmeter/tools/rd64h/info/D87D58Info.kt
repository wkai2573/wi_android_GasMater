package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D58Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var maximumUsage:String = ""
	var maximumUsageTime:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D58Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 12) throw Exception("D58結果 = \"$data\"")
		maximumUsage = data.substring(0, 8)
		maximumUsageTime = data.substring(8, 12)
	}

	override fun toString():String {
		return data
	}
}
