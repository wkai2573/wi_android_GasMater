package com.wavein.gasmeter.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {

	fun getCurrentTime(format: String): String {
		val dateFormat = SimpleDateFormat(format, Locale.getDefault())
		val currentTime = Date()
		return dateFormat.format(currentTime)
	}

}