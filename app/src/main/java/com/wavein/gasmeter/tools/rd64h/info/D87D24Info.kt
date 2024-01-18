package com.wavein.gasmeter.tools.rd64h.info

import com.wavein.gasmeter.tools.rd64h.ALine
import com.wavein.gasmeter.tools.rd64h.SecurityLevel

data class D87D24Info(override val text:String) : BaseInfo(text) {
	var data:String = ""
	var alarmInfo1:String = ""            // アラーム情報1(03-01) 8位
	var alarmInfo2:String = ""            // アラーム情報2(03-02) 8位
	var stateValue:String = ""            // 状態値№(24-01) 1位
	var q0:String = ""                    // Q0(03-03) 5位
	var q0ShutOffSettingTime:String = ""  // Q0遮断設定時間(24-02) 3位
	var q0TimerTime:String = ""           // Q0タイマ時間(24-03) 3位
	var numberOfRetries:String = ""       // リトライ回数(24-04) 2位
	var innerPipeLeakage:String = ""      // 内管漏洩有無(01-02) 1位
	var fuseFlowRegistration:String = ""  // 口火流量登録有無(24-05) 1位
	var registerFuseFlowRate1:String = "" // 登録口火流量1(24-06) 4位
	var registerFuseFlowRate2:String = "" // 登録口火流量2(24-06) 4位

	init {
		val matchResult = Regex("^ZD(.{14})D87(.+)$").find(text) ?: throw Exception("異常")
		val (meterId, aLineRaw) = matchResult.destructured
		val aLine = ALine(aLineRaw, SecurityLevel.Auth)
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
