package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine

data class D87D23Info(override val text:String) : BaseInfo(text) {
	override var isCorrectParsed = false
	var data:String = ""

	// 寫入接續part
	fun writePart(text:String) {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text)
		if (matchResult != null) {
			val (meterId, aLineRaw) = matchResult.destructured
			val aLine = ALine(aLineRaw)
			data += aLine.data
			isCorrectParsed = true
		}
	}

	override fun toString():String {
		return data
	}
}
