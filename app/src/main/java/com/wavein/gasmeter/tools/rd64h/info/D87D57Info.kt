package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D57Info(override val text:String) : BaseInfo(text) {
	var data:String = "" // 時間使用量(57-01) hourlyUsage 4位

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 4) throw Exception("D57結果 = \"$data\"")
	}

	override fun toString():String {
		return data
	}
}
