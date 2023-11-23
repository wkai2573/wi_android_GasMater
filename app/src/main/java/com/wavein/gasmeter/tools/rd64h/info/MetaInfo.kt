package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.R87Step

// 此次電文組合標頭
data class MetaInfo(
	override val text:String,
	val op:String,
	val meterIds:List<String>,
	val r87Steps:List<R87Step>? = null,
) : BaseInfo(text) {
	override var isCorrectParsed = false
}