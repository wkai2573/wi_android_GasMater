package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D24Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var alarmInfo1:String = ""
	var alarmInfo2:String = ""
	var stateValue:String = ""
	var q0:String = ""
	var q0ShutOffSettingTime:String = ""
	var q0TimerTime:String = ""
	var numberOfRetries:String = ""
	var innerPipeLeakage:String = ""
	var fuseFlowRegistration:String = ""
	var registerFuseFlowRate1:String = ""
	var registerFuseFlowRate2:String = ""

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常:D87D24Info")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.NoSecurity)
		data = aLine.data
		if (data.length != 40) throw Exception("D24結果 = \"$data\"")

		val alineMatchResult = Regex("^(.{8})(.{8})(.)(.{5})(.{3})(.{3})(.{2})(.)(.)(.{4})(.{4})$").find(data)
		val groups = alineMatchResult!!.groupValues
		this.alarmInfo1 = groups[1]
		this.alarmInfo2 = groups[2]
		this.stateValue = groups[3]
		this.q0 = groups[4]
		this.q0ShutOffSettingTime = groups[5]
		this.q0TimerTime = groups[6]
		this.numberOfRetries = groups[7]
		this.innerPipeLeakage = groups[8]
		this.fuseFlowRegistration = groups[9]
		this.registerFuseFlowRate1 = groups[10]
		this.registerFuseFlowRate2 = groups[11]
	}

	override fun toString():String {
		return data
	}
}
