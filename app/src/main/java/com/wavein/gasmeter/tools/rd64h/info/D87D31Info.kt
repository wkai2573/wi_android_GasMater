package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D31Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var registerFuseFlowRate1:String = ""
	var registerFuseFlowRate2:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D31Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 8) throw Exception("D31結果 = \"$data\"")
		registerFuseFlowRate1 = data.substring(0, 4)
		registerFuseFlowRate2 = data.substring(4, 8)
	}

	override fun toString():String {
		return data
	}
}
