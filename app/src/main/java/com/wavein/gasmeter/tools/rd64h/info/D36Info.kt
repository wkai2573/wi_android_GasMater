package com.wavein.gasmeter.tools.rd64h.info


data class D36Info(override val text:String) : BaseInfo(text) {
	var btParentAlarmRaw:String = ""
	var meterId:String = ""
	var alarmRaw:String = ""

	init {
		val regex = Regex("^([0-9][01])ZD(.{14})D36(..)\$")
		val matchResult = regex.find(text) ?: throw Exception("異常:D36Info")
		val (btParentAlarmRaw, meterId, alarmRaw) = matchResult.destructured
		this.btParentAlarmRaw = btParentAlarmRaw
		this.meterId = meterId
		this.alarmRaw = alarmRaw
	}
}
