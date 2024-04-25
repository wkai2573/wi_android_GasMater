package com.wavein.gasmeter.tools.rd64h.info

// 對子機, 接收D34電文
data class GwD34Info(override val text:String) : BaseInfo(text) {
	var data:String = ""

	init {
		val regex = Regex("^ZD(.{14})D34(.*)\$")
		val matchResult = regex.find(text) ?: throw Exception("異常:GwD34Info")
		val (data) = matchResult.destructured
		this.data = data
	}
}
