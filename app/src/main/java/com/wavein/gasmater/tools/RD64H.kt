package com.wavein.gasmater.tools

import kotlin.experimental.xor

// RD64H 傳輸方法

object RD64H {

	private const val STX:Char = '\u0002'
	private const val ETX:Char = '\u0003'

	private fun getBCC(code:String):Char {
		var bcc = 0
		for (i in code.indices) {
			bcc = bcc.xor(code[i].code)
		}
		return bcc.toChar()
	}

	private fun addEvenParityChar(char:Char):Char {
		val parity = getEvenParity(char)
		val charValue = char.code
		val binaryString = "${parity}${charValue.toString(2).padStart(7, '0')}"
		val evenParityAddedValue = Integer.parseInt(binaryString, 2)
		return evenParityAddedValue.toChar()
	}

	private fun getEvenParity(char:Char):Int {
		val charValue = char.code
		var parity = 0
		for (i in 0 until 8) {
			if ((charValue shr i) and 1 == 1) {
				parity = parity.xor(1)
			}
		}
		return parity
	}

	private fun removeEvenParityChar(char:Char):Char {
		val charValue = char.code
		val evenParityRemovedValue = charValue and 0x7f
		return evenParityRemovedValue.toChar()
	}

	private fun hexToAscii(hex:String):String {
		return hex.chunked(2).joinToString("") { hexByte ->
			val charValue = Integer.parseInt(hexByte, 16)
			charValue.toChar().toString()
		}
	}

	/** 電文轉換
	 *
	 * @param {string} inputText 輸入字串
	 * @param {string} flag 特殊處理,包含以下字元則做指定處理
	 *  +h: 輸入 TEXT 輸出 HEX(無分隔字串,每字元2位數)
	 *  -h: 輸入 HEX 輸出 TEXT
	 *  +s: 補上STX,ETX,BCC
	 *  -s: 移除STX,ETX,BCC
	 *  +p: 每個字元補上偶數校驗碼
	 *  -p: 每個字元移除偶數校驗碼
	 * @returns {string} 轉換後的結果
	 */
	fun telegramConvert(inputText:String, flag:String):String {
		var output = inputText
		if (flag.contains("-h")) {
			output = hexToAscii(output)
		}
		if (flag.contains("+s")) {
			output = STX + output + ETX + getBCC(output + ETX)
		}
		if (flag.contains("+p")) {
			output = output.map { addEvenParityChar(it) }.joinToString("")
		}
		if (flag.contains("-p")) {
			output = output.map { removeEvenParityChar(it) }.joinToString("")
		}
		if (flag.contains("-s")) {
			output = output.replace("^\\x02(.*)\\x03.$".toRegex(), "$1")
		}
		if (flag.contains("+h")) {
			return output.toByteArray().joinToString("") { "%02X".format(it) }
		}
		return output
	}
}