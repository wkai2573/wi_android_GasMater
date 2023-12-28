package com.wavein.gasmeter.tools

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TimeUtils {

	// 不建議用了, 請參考下面的 example() 來直接用
	fun getCurrentTime(pattern:String = "yyyy-MM-dd HH:mm:ss"):String {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern))
	}

	// 時間範例
	private fun example() {
		val 三年前_年份 = LocalDate.now().minusYears(3).format(DateTimeFormatter.ofPattern("yyyy"))
		val 上個月_時間 = LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:MM:ss"))
	}
}