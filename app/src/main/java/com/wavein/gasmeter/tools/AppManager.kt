package com.wavein.gasmeter.tools

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings

object AppManager {



	private lateinit var uniqueCode:String
	lateinit var client:String
	private fun initUniqueCode(context:Context) {
		uniqueCode = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
		client = "${Build.MANUFACTURER}/${Build.MODEL}/$uniqueCode"
	}


	fun initAll(application:Application) {
		Preference.init(application)
		NetworkInfo.initDetectionNetwork(application)
		initUniqueCode(application)
	}

	var firstEnterApp = true

}