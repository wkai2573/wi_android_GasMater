package com.wavein.gasmeter.tools.rd64h

import android.util.Log

// 分析接收電文的ALine資料
// todo 要加安全性securityLevel
data class ALine(
	val aLineRaw:String, // 原始內容
) {
	/**
	 * @param cc, 8位hex, 每2位為一組, 以下以CC1~CC4解釋
	 *   CC1 電文傳送的類型, 21h=無線傳送 12h=無線接收, 71h=有線傳送 17h=有線接收
	 *   CC2 連續電文的開始與結束, 40h=開始, 00h=中間, 20h=結束
	 *   CC3 電文index, 連續電文的傳送&接收的index需要不同, 建議從00h往上加, 因為只管傳送, 每次加02h
	 *   CC4 多part使用, 00h=無分割, 10h=2分割_第1part, 11h=2分割_第2part
	 *       第8碼若不是0 -> 表示為接續part -> 接續part不含[dp]&[op],後段的資料(7+64位元)皆為[data]
	 */
	private var cc:String = ""
	private val cch get() = cc.toByteArray().toHex()
	val isFirstPart get() = cch[7] == '0'

	var adr:String = ""  // ADR: 14字元, 瓦斯表ID
	private var dp:String = ""   // DP: 4字元, 事業體 (僅首part)
	private var op:String = ""   // OP: 3字元, 回傳的操作代碼 (僅首part)
	private var fullData:String = "" // DATA: 64字元, 回傳的資料, 自動移除後面空白
	var data:String = ""             // 同上, 移除後面空白

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
			data = fullData.trimEnd()

			Log.i(
				"@@@ Recv",
				"CC:${cc.stringToHex()}h ADR:[$adr] DP:[$dp] OPC:[$op] DATA:[$fullData]" +
						" BCC:${bcc.stringToHex()}h"
			)
		}
	}
}