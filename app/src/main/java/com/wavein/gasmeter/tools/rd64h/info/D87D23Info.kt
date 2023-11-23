package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine

data class D87D23Info(override val text:String) : BaseInfo(text) {
	override var isCorrectParsed = false
	var data:String = ""
	var shutdownHistory1:String = ""
	var shutdownHistory2:String = ""
	var shutdownHistory3:String = ""
	var shutdownHistory4:String = ""
	var shutdownHistory5:String = ""

	// 寫入接續part
	fun writePart(text:String) {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text)
		if (matchResult != null) {
			val (meterId, aLineRaw) = matchResult.destructured
			val aLine = ALine(aLineRaw)
			data += aLine.data
			if (data.length == 65) {
				val shutdownHistoryList = data.chunked(13)
				shutdownHistory1 = shutdownHistoryList[0]
				shutdownHistory2 = shutdownHistoryList[1]
				shutdownHistory3 = shutdownHistoryList[2]
				shutdownHistory4 = shutdownHistoryList[3]
				shutdownHistory5 = shutdownHistoryList[4]
				isCorrectParsed = true
			}
		}
	}

	override fun toString():String {
		return data
	}
}
