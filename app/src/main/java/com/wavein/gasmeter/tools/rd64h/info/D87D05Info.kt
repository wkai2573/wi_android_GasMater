package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D05Info(override val text:String) : BaseInfo(text) {
	var data:String = "" //指針値(01-01) 9位 + アラーム情報1(03-01) 8位
	var d05Info:D05Info? = null

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
		data = aLine.data
		if (data.length != 17) throw Exception("D05結果 = \"$data\"")

		d05Info = D05Info.createByData(meterId, aLine.data)
	}

	override fun toString():String {
		return "text: $text, data: $data, D05Info: ${d05Info?.toString() ?: "null"}"
	}
}
