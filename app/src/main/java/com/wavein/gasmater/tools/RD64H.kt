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

	// ==Step: 傳收參數==

	open class BaseStep
	data class SingleSendStep(val text:String) : BaseStep()
	class SingleRespStep : BaseStep()
	class __AStep : BaseStep()
	class __5Step : BaseStep()
	class D70Step : BaseStep()
	data class R80Step(val meterIds:List<String>) : BaseStep()
	data class D05Step(val count:Int) : BaseStep()

	// ==Info: 回傳結果分析==

	fun getInfo(text:String, infoClass:Class<*>):BaseInfo {
		val info = when {
			D70Info::class.java.isAssignableFrom(infoClass) -> {
				val d70Info = D70Info(text)
				if (d70Info.isCorrectParsed) d70Info else null
			}

			D05Info::class.java.isAssignableFrom(infoClass) -> {
				val d05Info = D05Info(text)
				if (d05Info.isCorrectParsed) d05Info else null
			}

			else -> null
		}
		if (info != null) return info

		val d16Info = D16Info(text)
		if (d16Info.isCorrectParsed) return d16Info
		return BaseInfo(text)
	}

	open class BaseInfo(open val text:String) {
		override fun toString():String = text
	}

	data class D16Info(override val text:String) : BaseInfo(text) {
		var isCorrectParsed = false
		var btParentId:String = ""
		var isBatteryVoltageLow:Boolean = false
		var isCarrierBusy:Boolean = false
		var isChildUnitNotResponding:Boolean = false

		init {
			val regex = Regex("^(ZA)(.{14})(D16)(.)(.)$")
			val matchResult = regex.find(text)
			if (matchResult != null) {
				isCorrectParsed = true
				val (text, ZA, btParentId, D16, alarm1, alarm2) = matchResult.destructured
				val alarmValue = alarm1[0].code
				this.isBatteryVoltageLow = alarmValue and 0b00000111 == 0
				this.isCarrierBusy = alarmValue and 0b00001011 == 0
				this.isChildUnitNotResponding = alarmValue and 0b00001101 == 0
			}
		}
	}

	data class D70Info(override val text:String) : BaseInfo(text) {
		var isCorrectParsed = false
		var btParentId:String = ""

		init {
			val regex = Regex("^(ZA)(.{14})(D70)$")
			val matchResult = regex.find(text)
			if (matchResult != null) {
				isCorrectParsed = true
				val (ZA, btParentId, D70) = matchResult.destructured
				this.btParentId = btParentId
			}
		}
	}

	data class D05Info(override val text:String) : BaseInfo(text) {
		var isCorrectParsed = false
		var btParentInfo = ""
		var entityCodeLast2 = ""
		var meterId = ""
		var meterDegree = ""
		var alarmInfo1 = ""
		var alarmInfoDetail = mutableMapOf<String, Map<String, Boolean>>()

		init {
			val regex = Regex("^(.{2})(.{2})(.{14})(D05)(.{9})(.{8})\\s*$")
			val matchResult = regex.find(text)
			if (matchResult != null) {
				isCorrectParsed = true
				val (btParentInfo, entityCodeLast2, meterId, OP, meterDegree, alarmInfo1) = matchResult.destructured
				this.btParentInfo = btParentInfo
				this.entityCodeLast2 = entityCodeLast2
				this.meterId = meterId
				this.meterDegree = meterDegree
				this.alarmInfo1 = alarmInfo1
				this.alarmInfoDetail = mutableMapOf()
				for (i in alarmInfo1.indices) {
					val infoDig = "A${8 - i}"
					val b4 = alarmInfo1[i].code and 0b00000111 == 0
					val b3 = alarmInfo1[i].code and 0b00001011 == 0
					val b2 = alarmInfo1[i].code and 0b00001101 == 0
					val b1 = alarmInfo1[i].code and 0b00001110 == 0
					alarmInfoDetail[infoDig] = mapOf("b4" to b4, "b3" to b3, "b2" to b2, "b1" to b1)
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
