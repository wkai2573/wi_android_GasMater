package com.wavein.gasmeter.tools.rd64h

// 分析接收電文的ALine資料
data class ALine(
	val aLineRaw:String, // 原始內容
) {
	/**
	 * [cc] 4字元, 轉16進位[cch] 將為 "12000100",
	 *
	 * 5-6碼為多筆設定/要求(Rxx)時的index, 連續的設定/要求時, 需要使用不同的index
	 *
	 * 7-8碼為分割part, 00=無分割, 10=2分割_第1part, 11=2分割_第2part
	 * 第8碼若不是0 -> 表示為接續part -> 接續part不含[dp]&[op],後段的資料(7+64位元)皆為[data]
	 */
	private var cc:String = ""
	private val cch get() = cc.toByteArray().toHexString()
	val isFirstPart get() = cch[7] == '0'

	var adr:String = ""  // ADR: 14字元, 瓦斯表ID
	private var dp:String = ""   // DP: 4字元, 事業體 (僅首part)
	private var op:String = ""   // OP: 3字元, 回傳的操作代碼 (僅首part)
	var data:String = "" // DATA: 64字元, 回傳的資料, 自動移除後面空白

	init {
		val uBusText = RD64H.textALineToUBus(aLineRaw)
		val groupValues = Regex("^(.{4})(.{14})\\2(.{4})(.{3})(.{64}).$").find(uBusText)?.groupValues
		if (groupValues != null) {
			cc = groupValues[1]
			adr = groupValues[2]
			dp = groupValues[3]
			op = groupValues[4]
			data = groupValues[5]
			if (!isFirstPart) {
				data = dp + op + data
				dp = ""
				op = ""
			}
			data = data.trimEnd()
		}
	}
}