package com.wavein.gasmeter.tools.rd64h

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.math.min





@OptIn(ExperimentalUnsignedTypes::class)
fun String.toUBytes():UByteArray {
	return this.map { it.code.toUByte() }.toUByteArray()
}


@OptIn(ExperimentalUnsignedTypes::class)
fun String.stringToHex():String {
	val bytes = this.toUBytes()
	return bytes.joinToString("") { "%02X".format(it.toInt()) }
}


@OptIn(ExperimentalUnsignedTypes::class)
fun String.stringToBin():String {
	val bytes = this.toUBytes()
	return bytes.joinToString("") { "%8s".format(Integer.toBinaryString(it.toInt())).replace(' ', '0') }
}


@OptIn(ExperimentalUnsignedTypes::class)
fun String.hexToUBytes():UByteArray {
	return chunked(2).map { it.toUByte(16) }.toUByteArray()
}


@OptIn(ExperimentalUnsignedTypes::class)
fun String.hexToString():String {
	val ubytes = this.chunked(2).map { it.toUByte(16) }.toUByteArray()
	return ubytes.toText()
}


@OptIn(ExperimentalUnsignedTypes::class)
fun UByteArray.toHex(separator:String = ""):String {
	return this.joinToString(separator) { it.toInt().toString(16).padStart(2, '0').uppercase() }
}


@OptIn(ExperimentalUnsignedTypes::class)
fun UByteArray.toText():String {
	return this.map { byte -> byte.toInt().toChar() }.joinToString(separator = "")
}


@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.toHex(separator:String = ""):String {
	return this.toUByteArray().toHex(separator)
}


fun ByteArray.toText():String {
	return this.map { byte -> byte.toUByte().toInt().toChar() }.joinToString(separator = "")
}



enum class SecurityLevel {
	NoSecurity, Auth, Confidential, Key;


	companion object {
		fun getSecurityLevel(dp:String):SecurityLevel {
			val dpBin = dp.stringToBin()
			return when (dpBin.substring(0..2)) {
				"00" -> SecurityLevel.NoSecurity
				"01" -> SecurityLevel.Auth
				"10" -> SecurityLevel.Confidential
				"11" -> SecurityLevel.Key
				else -> SecurityLevel.NoSecurity
			}
		}
	}
}


@OptIn(ExperimentalUnsignedTypes::class)
object RD64H {
	private const val STX:Char = '\u0002'
	private const val ETX:Char = '\u0003'
	const val STXByte:UByte = 0x2u
	const val ETXByte:UByte = 0x3u

	private fun getBCC(bytes:UByteArray):UByte {
		var bcc:UByte = 0u
		bytes.forEach { byte -> bcc = bcc xor byte }
		return bcc
	}

	fun checkBCC(bytes:UByteArray):Boolean {
		if (bytes.size < 3) return false
		if (bytes[0] != STXByte) return false
		if (bytes[bytes.size - 2] != ETXByte) return false
		val bcc = bytes[bytes.size - 1]
		return getBCC(bytes.copyOfRange(1, bytes.size - 1)) == bcc
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


	fun createR80Text(btParentId:String, meterIds:List<String>, callingChannel:String):String {
		if (meterIds.size > 45 || meterIds.isEmpty()) throw IllegalArgumentException("單次抄表最多45台")
		val countCode = meterIds.size.toString().padStart(2, '0')
		val metersCode = meterIds.joinToString("") { "$it????" }
		val R80 = "ZA${btParentId}R80$callingChannel$countCode$metersCode"
		return R80
	}

	
	fun telegramConvert(inputText:String, flag:String):ByteArray {
		return telegramConvert(inputText.toUBytes(), flag)
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
			if (checkBCC(output)) {
				output = output.copyOfRange(1, output.size - 2)
			}
		}
		return output.toByteArray()
	}

	
	fun createR87Aline(
		securityLevel:SecurityLevel = SecurityLevel.NoSecurity,
		cc:String = "\u0021\u0040\u0000\u0000",
		adr:String,
		op:String,
		data:String = "",
		date:String? = null,
	):UByteArray {
		when (securityLevel) {

			SecurityLevel.Auth -> {
				val ccU = cc.toUBytes()
				val adrU = adr.toUBytes()
				val dp = "\u0040EN1"
				val dpU = dp.toUBytes()
				val opU = op.toUBytes()
				val data42 = data.padEnd(42, ' ')
				val data42U = data42.toUBytes()
				val dateHex = date ?: getNowTimeString()
				val dateU = dateHex.hexToUBytes()
				val macU = Auth.calcMac((ccU + adrU + adrU + dpU + opU + data42U + dateU).toByteArray()).toUByteArray()
				val bcc = getBCC(ccU + adrU + adrU + dpU + opU + data42U + dateU + macU)
				val fullTelegramU = ccU + adrU + adrU + dpU + opU + data42U + dateU + macU + ubyteArrayOf(bcc)
				Log.i(
					"@@@ Send_UBus",
					"CC:${ccU.toHex()}h ADR:[$adr] DP:[$dp] OPC:[$op] DATA:[$data42]" +
							" DATE:${dateHex}h MAC:${macU.toHex()}h BCC:${ubyteArrayOf(bcc).toHex()}h"
				)
				return textUBusToALine(fullTelegramU)
			}

			else -> {
				val ccU = cc.toUBytes()
				val adrU = adr.toUBytes()
				val dp = when (op) {
					"C02" -> "\u0000*00"
					else -> "\u0000EN1"
				}
				val dpU = dp.toUBytes()
				val opU = op.toUBytes()
				val data64 = data.padEnd(64, ' ')
				val data64U = data64.toUBytes()
				val bcc = getBCC(ccU + adrU + adrU + dpU + opU + data64U)
				val fullTelegramU = ccU + adrU + adrU + dpU + opU + data64U + ubyteArrayOf(bcc)
				Log.i(
					"@@@ Send_UBus",
					"CC:${ccU.toHex()}h ADR:[$adr] DP:[$dp] OPC:[$op] DATA:[$data64]" +
							" BCC:${ubyteArrayOf(bcc).toHex()}h"
				)
				return textUBusToALine(fullTelegramU)
			}
		}
	}


	private fun textUBusToALine(uBus:UByteArray):UByteArray {

		val aLineChars = uBus.flatMap<UByte> {
			val bit8 = it.toInt().toString(2).padStart(8, '0')
			val bit4F = bit8.substring(0, 4)
			val bit4S = bit8.substring(4)
			val uF = "011$bit4F".toInt(2).toUByte()
			val uS = "011$bit4S".toInt(2).toUByte()
			listOf(uF, uS)
		}.toUByteArray()
		return aLineChars
	}


	fun textALineToUBus(aLine:String):String {

		val uBusChars = aLine.chunked(2).map { char2 ->
			val binaryString = char2.map { c ->
				c.code.toString(2).padStart(4, '0').takeLast(4)
			}.joinToString("")
			Integer.parseInt(binaryString, 2).toChar().toString()
		}
		return uBusChars.joinToString("")
	}


	private fun getNowTimeString():String {
		val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
		val date = Date()
		return dateFormat.format(date)
	}


	object Auth {



		var cryptKey:ByteArray = "0000000000000000".toByteArray()
		var macKey:ByteArray = "0000000000000000".toByteArray()

		private fun String.substringLength(startIndex:Int, length:Int):String {
			return this.substring(startIndex, min(startIndex + length, this.length))
		}


		private val byteKey = byteArrayOf(
			115.toByte(), 118.toByte(), 174.toByte(), 240.toByte(), 237.toByte(), 174.toByte(), 141.toByte(), 92.toByte(),
			167.toByte(), 30.toByte(), 143.toByte(), 33.toByte(), 38.toByte(), 216.toByte(), 142.toByte(), 23.toByte(),
		)
		private val byteInitialVector = byteArrayOf(
			37.toByte(), 104.toByte(), 252.toByte(), 192.toByte(), 240.toByte(), 203.toByte(), 19.toByte(), 101.toByte(),
			200.toByte(), 205.toByte(), 172.toByte(), 161.toByte(), 208.toByte(), 226.toByte(), 191.toByte(), 249.toByte(),
		)


		fun decryptKeyFile(text:String):Pair<ByteArray, ByteArray> {
			if (text.length != 512) return Pair(byteArrayOf(), byteArrayOf())

			val array = ByteArray(16)
			val array2 = ByteArray(16)
			val array3 = ByteArray(224)

			for (num in 0 until 16) {
				array[num] = Integer.parseInt(text.substringLength(0 + num * 2, 2), 16).toByte()
				array2[num] = Integer.parseInt(text.substringLength(32 + num * 2, 2), 16).toByte()
			}

			for (num2 in 0 until 224) {
				array3[num2] = Integer.parseInt(text.substringLength(64 + num2 * 2, 2), 16).toByte()
			}

			val array4 = ByteArray(16)
			val array5 = ByteArray(16)

			for (num3 in 0 until 8) {
				for (num4 in 0 until 16) {
					array4[num4] = (array4[num4].toUByte().toInt() xor array3[num3 * 8 + num4].toUByte().toInt()).toByte()
					array5[num4] = (array5[num4].toUByte().toInt() xor array3[(num3 + 8) * 8 + num4].toUByte().toInt()).toByte()
				}
			}

			val decryptedArray = decryptData(array, array4, array4)
			val decryptedArray2 = decryptData(array2, array5, array5)

			val cryptKey = decryptData(decryptedArray, byteKey, byteInitialVector)
			val macKey = decryptData(decryptedArray2, byteKey, byteInitialVector)

			return Pair(cryptKey, macKey)
		}


		private fun encryptData(data:ByteArray, key:ByteArray, iv:ByteArray):ByteArray {
			val cipher = Cipher.getInstance("AES/CBC/NoPadding")
			val secretKeySpec = SecretKeySpec(key, "AES")
			val ivParameterSpec = IvParameterSpec(iv)
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

			val encrypted = cipher.doFinal(data)

			return encrypted
		}


		private fun decryptData(data:ByteArray, key:ByteArray, iv:ByteArray):ByteArray {
			val cipher = Cipher.getInstance("AES/CBC/NoPadding")
			val secretKeySpec = SecretKeySpec(key, "AES")
			val ivParameterSpec = IvParameterSpec(iv)
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

			val decrypted = cipher.doFinal(data)

			return decrypted
		}


		fun calcMac(origDataString:ByteArray, macKey:ByteArray = RD64H.Auth.macKey):ByteArray {
			var dataString = origDataString

			val (array1, array2) = makeSubKey(macKey)
			val array3 = ByteArray(16)

			val num = (dataString.size + 15) / 16

			val flag:Boolean = if (num == 0) {
				false
			} else {
				(dataString.size % 16 == 0)
			}

			if (flag) {
				for (num2 in 0 until 16) {
					dataString[(num - 1) * 16 + num2] = (dataString[(num - 1) * 16 + num2] xor array1[num2])
				}
			} else {
				val num3 = dataString.size
				val dataStringNew = ByteArray(num * 16)
				System.arraycopy(dataString, 0, dataStringNew, 0, dataString.size)
				dataString = dataStringNew
				dataString[num3] = 128.toByte()

				for (num4 in 0 until 16) {
					dataString[(num - 1) * 16 + num4] = (dataString[(num - 1) * 16 + num4] xor array2[num4])
				}
			}

			var array4 = ByteArray(16)
			val iv = ByteArray(16)

			for (i in 0 until num) {
				for (num6 in 0 until 16) {
					array3[num6] = (array4[num6] xor dataString[i * 16 + num6])
				}

				array4 = encryptData(array3, macKey, iv)
			}

			return array4
		}

		private fun makeSubKey(key:ByteArray):Pair<ByteArray, ByteArray> {
			val dataString = ByteArray(16)
			val iv = ByteArray(16)

			val array = encryptData(dataString, key, iv)

			val k1:ByteArray = if ((array[0].toUByte().toInt() and 128) == 0) {
				rotateBit(array)
			} else {
				val temp = rotateBit(array)
				temp[15] = (temp[15].toUByte().toInt() xor 135).toByte()
				temp
			}

			val k2:ByteArray = if ((k1[0].toUByte().toInt() and 128) == 0) {
				rotateBit(k1)
			} else {
				val temp = rotateBit(k1)
				temp[15] = (temp[15].toUByte().toInt() xor 135).toByte()
				temp
			}

			return Pair(k1, k2)
		}

		private fun rotateBit(inputArray:ByteArray):ByteArray {
			var b = 0
			val array = ByteArray(inputArray.size)

			for (i in inputArray.size - 1 downTo 0) {
				array[i] = ((inputArray[i].toUByte().toInt() shl 1) + b).toByte()
				b = inputArray[i].toUByte().toInt() ushr 7
			}

			return array
		}
	}
}