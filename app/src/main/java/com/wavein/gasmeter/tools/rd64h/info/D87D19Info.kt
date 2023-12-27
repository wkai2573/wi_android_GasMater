package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine

data class D87D19Info(override val text:String) : BaseInfo(text) {
	var data:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw)
		data = aLine.data
	}

	override fun toString():String {
		return data
	}
}
