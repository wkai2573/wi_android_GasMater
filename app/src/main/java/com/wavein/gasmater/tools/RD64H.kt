package com.wavein.gasmater.tools

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
}

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
