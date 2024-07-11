package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D16Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var meterStatus:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D16Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 9) throw Exception("D16結果 = \"$data\"")
		meterStatus = data
	}

	override fun toString():String {
		return data
	}
}
