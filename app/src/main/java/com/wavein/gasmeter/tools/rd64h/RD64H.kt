package com.wavein.gasmeter.tools.rd64h

// RD64H 傳輸方法

@OptIn(ExperimentalUnsignedTypes::class)
object RD64H {
	private const val STX:Char = '\u0002'
	private const val ETX:Char = '\u0003'
	private const val STXByte:UByte = 0x2u
	private const val ETXByte:UByte = 0x3u

	private fun getBCC(bytes:UByteArray):UByte {
		var bcc:UByte = 0u
		bytes.forEach { byte -> bcc = bcc xor byte }
		return bcc
	}

	private fun addEvenParityChar(byte:UByte):UByte {
		val charValue = byte.toInt()
		val parityByte = getEvenParityByte(charValue)
		return byte.xor(parityByte)
	}

	private fun getEvenParityByte(charValue:Int):UByte {
		var parity = 0
		for (i in 0 until 8) {
			if ((charValue ushr i) and 1 == 1) {
				parity = parity.xor(1)
			}
		}
		return if (parity == 1) 0x80u else 0u
	}

	private fun removeEvenParityChar(byte:UByte):UByte {
		return byte.and(0x7fu)
	}

	private fun hexToAscii(hex:String):String {
		return hex.chunked(2).joinToString("") { hexByte ->
			val charValue = Integer.parseInt(hexByte, 16)
			charValue.toChar().toString()
		}
	}

	// 產生R80電文, meterIds長度範圍 1~45
	fun createR80Text(btParentId:String, meterIds:List<String>):String {
		if (meterIds.size > 45 || meterIds.isEmpty()) throw IllegalArgumentException("單次抄表最多45台")
		val countCode = meterIds.size.toString().padStart(2, '0')
		val metersCode = meterIds.joinToString("") { "$it????" }
		val R80 = "ZA${btParentId}R8066$countCode$metersCode"
		return R80
	}

	/** 電文轉換
	 *
	 * @param {string | ByteArray | UByteArray} inputText 輸入字串
	 * @param {string} flag 特殊處理,包含以下字元則做指定處理
	 *  +s: 補上STX,ETX,BCC
	 *  -s: 移除STX,ETX,BCC
	 *  +p: 每個字元補上偶數校驗碼
	 *  -p: 每個字元移除偶數校驗碼
	 * @returns {string} 轉換後的結果
	 */
	fun telegramConvert(inputText:String, flag:String):ByteArray {
		return telegramConvert(inputText.toUByteArray(), flag)
	}

	fun telegramConvert(inputText:ByteArray, flag:String):ByteArray {
		return telegramConvert(inputText.toUByteArray(), flag)
	}

	fun telegramConvert(inputText:UByteArray, flag:String):ByteArray {
		var output = inputText
		if (flag.contains("+s")) {
			val bcc = getBCC(output + ETXByte)
			output = ubyteArrayOf(STXByte) + output + ETXByte + bcc
		}
		if (flag.contains("+p")) {
			output = output.map { addEvenParityChar(it) }.toUByteArray()
		}
		if (flag.contains("-p")) {
			output = output.map { removeEvenParityChar(it) }.toUByteArray()
		}
		if (flag.contains("-s")) {
			if (output[0] == STXByte && output[output.size - 2] == ETXByte) {
				output = output.copyOfRange(1, output.size - 2)
			}
		}
		return output.toByteArray()
	}


	/** 產生 R87_ALine部分的字串
	 *
	 * @param cc   可選, 預設 "\u0021\u0040\u0000\u0000" 字串[!@\0\0] bytes[21400000]
	 * @param adr  必填, 瓦斯表ID
	 * @param dp   可選, 事業體, 通常固定為 "\u0000EN1" 字串[00454>31]
	 * @param op   必填, 瓦斯表的操作代碼
	 * @param data 可選, 操作代碼附屬的資料, 會自動補空白到64位
	 * @returns R87_ALine字串
	 */
	fun createR87Aline(
		cc:String = "\u0021\u0040\u0000\u0000",
		adr:String,
		dp:String = "\u0000EN1",
		op:String,
		data:String = "",
	):UByteArray {
		val data64 = data.padEnd(64, ' ')
		val ccA = textUBusToALine(cc.toUByteArray())
		val adrA = textUBusToALine(adr.toUByteArray())
		val dpA = textUBusToALine(dp.toUByteArray())
		val opA = textUBusToALine(op.toUByteArray())
		val dataA = textUBusToALine(data64.toUByteArray())  // dataA = "202020..." (總長128)
		val bcc = getBCC("$cc$adr$adr$dp$op$data".toUByteArray())
		val bccA = textUBusToALine(ubyteArrayOf(bcc))  // bccA = "08"
		return ccA + adrA + adrA + dpA + opA + dataA + bccA
	}

	// UBus轉ALine
	private fun textUBusToALine(uBus:UByteArray):UByteArray {
		// 每1字元8bit 拆成2個4bit, 'p011'+4bit組成新字元, 每1個字元都會變2個字元
		val aLineChars = uBus.flatMap<UByte> {
			val bit8 = it.toInt().toString(2).padStart(8, '0')
			val bit4F = bit8.substring(0, 4)
			val bit4S = bit8.substring(4)
			val uF = "011$bit4F".toInt(2).toUByte() // 傳送的時候會補parity, 所以這裡不用加parity
			val uS = "011$bit4S".toInt(2).toUByte()
			listOf(uF, uS)
		}.toUByteArray()
		return aLineChars
	}

	// ALine轉UBus
	fun textALineToUBus(aLine: String): String {
		// 每2字元取後面4bit 組成1字元
		val uBusChars = aLine.chunked(2).map { char2 ->
			val binaryString = char2.map { c ->
				c.code.toString(2).padStart(4, '0').takeLast(4)
			}.joinToString("")
			Integer.parseInt(binaryString, 2).toChar().toString()
		}
		return uBusChars.joinToString("")
	}
}

// ==轉換方法==
@OptIn(ExperimentalUnsignedTypes::class)
private fun String.toUByteArray():UByteArray {
	return this.map { it.code.toUByte() }.toUByteArray()
}

// 轉成HEX方便看
@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.toHexString(separator:String = ""):String {
	return this.toUByteArray().joinToString(separator) { it.toInt().toString(16).padStart(2, '0').uppercase() }
}

// 轉成TEXT方便看
fun ByteArray.toText():String {
	return String(this, Charsets.UTF_8)
}
