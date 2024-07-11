package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D51Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var pressureValue:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D51Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 4) throw Exception("D51結果 = \"$data\"")
		pressureValue = data
	}

	override fun toString():String {
		return data
	}
}
