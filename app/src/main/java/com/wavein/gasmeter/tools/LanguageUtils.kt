


package com.wavein.gasmeter.tools

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.util.*

object LanguageUtils {

	
	fun setLanguage(context:Context, lan:String) {
		context.getSharedPreferences("settings", 0).edit {
			putString("language", lan)
			this.commit()
		}
	}

	
	fun getCheckedItem(context:Context):Int =
		when (context.getSharedPreferences("settings", 0).getString("language", "")) {
			"auto" -> 0
			"zh-rCN" -> 1
			"zh-rTW" -> 2
			"en" -> 3
			"ja" -> 4
			else -> 0
		}

	
	fun getLocale(context:Context):Locale =
		when (context.getSharedPreferences("settings", 0).getString("language", "")) {
			"auto" -> getSysLocale()
			"zh-rCN" -> Locale("zh", "CN")
			"zh-rTW" -> Locale("zh", "TW")
			"en" -> Locale("en")
			"ja" -> Locale("ja")
			else -> getSysLocale()
		}

	
	private fun getSysLocale():Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
		LocaleList.getDefault()[0]
	} else {
		Locale.getDefault()
	}


	@RequiresApi(Build.VERSION_CODES.N)
	fun wrap(context:Context, newLocale:Locale?):ContextWrapper {
		var mContext = context
		val res:Resources = mContext.resources
		val configuration:Configuration = res.configuration


		mContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			configuration.setLocale(newLocale)
			val localeList = LocaleList(newLocale)
			LocaleList.setDefault(localeList)
			configuration.setLocales(localeList)
			mContext.createConfigurationContext(configuration)
		} else {
			configuration.setLocale(newLocale)
			mContext.createConfigurationContext(configuration)
		}
		return ContextWrapper(mContext)
	}




	fun changeLanguage(activity:ComponentActivity, langText:String) {
		val lang = LanguageUtils.getLocale(activity)
		if (lang.language == langText) return
		val newLang = langText
		LanguageUtils.setLanguage(activity, newLang)
		activity.recreate()
	}
}

