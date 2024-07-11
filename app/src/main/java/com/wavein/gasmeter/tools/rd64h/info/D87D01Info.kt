package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D01Info(override val text:String) : BaseInfo(text) {
	var data:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D01Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.NoSecurity)
		data = aLine.data
		if (data.length != 10) throw Exception("D01結果 = \"$data\"")
	}

	override fun toString():String {
		return data
	}
}
