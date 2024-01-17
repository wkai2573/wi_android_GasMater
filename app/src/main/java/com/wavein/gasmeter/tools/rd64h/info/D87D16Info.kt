package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine

data class D87D16Info(override val text:String) : BaseInfo(text) {
	var data:String = "" // メーター状態(16-02) 9位

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
