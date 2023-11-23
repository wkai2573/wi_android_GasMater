package com.wavein.gasmeter.tools.rd64h.info

// 子機無回應時, 會回傳D16
data class D16Info(override val text:String) : BaseInfo(text) {
	var btParentId:String = ""
	var isBatteryVoltageLow:Boolean = false
	var isCarrierBusy:Boolean = false
	var isChildUnitNotResponding:Boolean = false

	init {
		val regex = Regex("^(ZA)(.{14})(D16)(.)(.)$")
		val matchResult = regex.find(text) ?: throw Exception("異常")
		val (ZA, btParentId, D16, alarm1, alarm2) = matchResult.destructured
		val alarmValue = alarm1[0].code
		this.isBatteryVoltageLow = alarmValue and 0b00000111 == 0
		this.isCarrierBusy = alarmValue and 0b00001011 == 0
		this.isChildUnitNotResponding = alarmValue and 0b00001101 == 0
	}
}