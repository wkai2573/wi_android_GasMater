package com.wavein.gasmeter.tools.rd64h.info

// 此次電文組合標頭
data class MetaInfo(
	override val text:String,
	val op:String,
	val meterIds:List<String>? = null,
) : BaseInfo(text) {
	override var isCorrectParsed = false
}