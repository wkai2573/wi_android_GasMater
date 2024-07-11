package com.wavein.gasmeter.tools.rd64h

import android.util.Log


data class ALine(
	val aLineRaw:String,
	var securityLevel:SecurityLevel? = null,
) {
	
	private var cc:String = ""
	private val cch get() = cc.toByteArray().toHex()
	val isFirstPart get() = cch[7] == '0'

	var adr:String = ""
	private var dp:String = ""
	private var op:String = ""
	private var fullData:String = ""
	var data:String = ""
	private var dateH:String = ""
	private var macH:String = ""

	init {
		val uBusText = RD64H.textALineToUBus(aLineRaw)
		val groupValues = Regex("^(.{4})(.{14})\\2(.{4})(.{3})(.{64})(.)$").find(uBusText)?.groupValues
		if (groupValues != null) {
			cc = groupValues[1]
			adr = groupValues[2]
			dp = groupValues[3]
			op = groupValues[4]
			fullData = groupValues[5]
			val bcc = groupValues[6]
			if (!isFirstPart) {
				fullData = dp + op + fullData
				dp = ""
				op = ""
			}
			if (securityLevel == null) securityLevel = SecurityLevel.getSecurityLevel(dp)
			if (securityLevel == SecurityLevel.Auth) {
				val dataGroupValues = Regex("^(.*)(.{6})(.{16})$").find(fullData)!!.groupValues
				fullData = dataGroupValues[1]
				dateH = dataGroupValues[2].stringToHex()
				macH = dataGroupValues[3].stringToHex()
			}
			data = fullData.trimEnd()

			Log.i(
				"@@@ Recv_UBus",
				"CC:${cc.stringToHex()}h ADR:[$adr] DP:[$dp] OPC:[$op] DATA:[$fullData]" +
						(if (dateH.isNotEmpty()) " DATE:${dateH}h" else "") +
						(if (macH.isNotEmpty()) " MAC:${macH}h" else "") +
						" BCC:${bcc.stringToHex()}h"
			)
		}
	}
}