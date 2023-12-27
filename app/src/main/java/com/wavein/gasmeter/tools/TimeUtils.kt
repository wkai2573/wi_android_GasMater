package com.wavein.gasmeter.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

	fun getCurrentTime(format: String = "yyyy-MM-dd HH:mm:ss"): String {
		val dateFormat = SimpleDateFormat(format, Locale.getDefault())
		val currentTime = Date()
		return dateFormat.format(currentTime)
	}

}