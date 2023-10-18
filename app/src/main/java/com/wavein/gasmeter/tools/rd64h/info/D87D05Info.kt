package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine

data class D87D05Info(override val text:String) : BaseInfo(text) {
	override var isCorrectParsed = false
	var data:String = ""
	var d05Info:D05Info? = null

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text)
		if (matchResult != null) {
			val (meterId, aLineRaw) = matchResult.destructured
			val aLine = ALine(aLineRaw)
			data = aLine.data
			d05Info = D05Info.createByData(meterId, aLine.data)
			isCorrectParsed = d05Info?.isCorrectParsed ?: false
		}
	}

	override fun toString():String {
		return "text: $text, data: $data, D05Info: ${d05Info?.toString() ?: "null"}"
	}
}
