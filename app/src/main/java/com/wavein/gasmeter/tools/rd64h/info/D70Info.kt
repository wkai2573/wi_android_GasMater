package com.wavein.gasmeter.tools.rd64h.info

data class D70Info(override val text:String) : BaseInfo(text) {
	override var isCorrectParsed = false
	var btParentId:String = ""

	init {
		val regex = Regex("^(ZA)(.{14})(D70)$")
		val matchResult = regex.find(text)
		if (matchResult != null) {
			isCorrectParsed = true
			val (ZA, btParentId, D70) = matchResult.destructured
			this.btParentId = btParentId
		}
	}
}