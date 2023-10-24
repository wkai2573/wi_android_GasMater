package com.wavein.gasmeter.tools.rd64h.info

data class D05Info(override val text:String) : BaseInfo(text) {
	override var isCorrectParsed = false
	var btParentInfo = ""
	var electricFieldStrength = ""
	var btParentLowBattery = ""
	var entityCodeLast2 = ""
	var meterId = ""
	var meterDegree = 0f
	var alarmInfo1 = ""
	var alarmInfoDetail = mutableMapOf<String, Map<String, Boolean>>()

	init {
		val regex = Regex("^(.)(.)(.{2})(.{14})(D05)(\\d{9}.{8})\\s*$")
		val matchResult = regex.find(text)
		if (matchResult != null) {
			val (electricFieldStrength, btParentLowBattery, entityCodeLast2, meterId, OP, data) = matchResult.destructured
			this.electricFieldStrength = electricFieldStrength
			this.btParentLowBattery = btParentLowBattery
			this.btParentInfo = electricFieldStrength + btParentLowBattery
			this.entityCodeLast2 = entityCodeLast2
			this.meterId = meterId
			parseData(data)
		}
	}

	private fun parseData(data:String) {
		isCorrectParsed = true
		val meterDegree = data.substring(0, 9)
		val alarmInfo1 = data.substring(9)
		this.meterDegree = meterDegree.toFloat() / 1000
		this.alarmInfo1 = alarmInfo1
		this.alarmInfoDetail = mutableMapOf()
		for (i in alarmInfo1.indices) {
			val infoDig = "A${8 - i}"
			val b4 = (alarmInfo1[i].code and 0b00001000) != 0
			val b3 = (alarmInfo1[i].code and 0b00000100) != 0
			val b2 = (alarmInfo1[i].code and 0b00000010) != 0
			val b1 = (alarmInfo1[i].code and 0b00000001) != 0
			alarmInfoDetail[infoDig] = mapOf("b4" to b4, "b3" to b3, "b2" to b2, "b1" to b1)
		}
	}

	companion object {
		fun createByData(meterId:String, data:String):D05Info {
			return D05Info("").apply {
				this.meterId = meterId
				parseData(data)
			}
		}
	}

	override fun toString():String {
		return "瓦斯表ID:$meterId\n度數:$meterDegree\n告警情報:$alarmInfo1"
	}
}


class D05mInfo : BaseInfo("") {
	var list = mutableListOf<D05Info>()
	override fun toString():String = list.toString()
}