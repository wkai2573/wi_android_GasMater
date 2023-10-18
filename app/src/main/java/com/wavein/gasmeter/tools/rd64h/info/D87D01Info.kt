package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine

data class D87D01Info(override val text:String) : BaseInfo(text) {
	override var isCorrectParsed = false
	var data:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text)
		if (matchResult != null) {
			val (meterId, aLineRaw) = matchResult.destructured
			val aLine = ALine(aLineRaw)
			data = aLine.data
			isCorrectParsed = true
		}
	}

	override fun toString():String {
		return data
	}
}
