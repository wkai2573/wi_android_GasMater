package com.wavein.gasmeter.tools

object SortUtils {

	// 按前綴分組，按數字排序
	fun <T> List<T>.groupedByPrefixSortedByNumber(selector:(T) -> String):List<T> {
		return this.sortedWith { s1, s2 ->
			val (prefix1, number1) = splitString(selector(s1))
			val (prefix2, number2) = splitString(selector(s2))

			if (prefix1 == prefix2) {
				compareNumbers(number1, number2)
			} else {
				prefix1.compareTo(prefix2)
			}
		}
	}

	private fun splitString(s:String):Pair<String, Int?> {
		val matchResult = Regex("([a-zA-Z]*)(\\d*)").matchEntire(s)
		val prefix = matchResult?.groups?.get(1)?.value ?: ""
		val number = matchResult?.groups?.get(2)?.value?.toIntOrNull()
		return prefix to number
	}

	private fun compareNumbers(n1:Int?, n2:Int?):Int {
		return when {
			n1 == null && n2 == null -> 0
			n1 == null -> -1
			n2 == null -> 1
			else -> {
				if (n1 == 11 && n2 == 2) 1
				else if (n1 == 2 && n2 == 11) -1
				else n1.compareTo(n2)
			}
		}
	}

}