// 語言切換
// 參考: https://blog.imyan.ren/posts/9e078f24/

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

	/**
	 * 设置语言的值
	 * @param context 上下文
	 * @param lan 需要设置的语言
	 */
	fun setLanguage(context:Context, lan:String) {
		context.getSharedPreferences("settings", 0).edit {
			putString("language", lan)
			this.commit()
		}
	}

	/**
	 * 获取应用于选择语言对话框的 checkedItem
	 */
	fun getCheckedItem(context:Context):Int =
		when (context.getSharedPreferences("settings", 0).getString("language", "")) {
			"auto" -> 0
			"zh-rCN" -> 1
			"zh-rTW" -> 2
			"en" -> 3
			"ja" -> 4
			else -> 0
		}

	/**
	 * 获取当前设置的 Locale
	 */
	fun getLocale(context:Context):Locale =
		when (context.getSharedPreferences("settings", 0).getString("language", "")) {
			"auto" -> getSysLocale()
			"zh-rCN" -> Locale("zh", "CN")
			"zh-rTW" -> Locale("zh", "TW")
			"en" -> Locale("en")
			"ja" -> Locale("ja")
			else -> getSysLocale()
		}

	/**
	 * 获取当前系统的 Locale
	 */
	private fun getSysLocale():Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
		LocaleList.getDefault()[0]
	} else {
		Locale.getDefault()
	}

	// 在每一个 Activity 或者你封装好的 BaseActivity 里重写attachBaseContext方法
	@RequiresApi(Build.VERSION_CODES.N)
	fun wrap(context:Context, newLocale:Locale?):ContextWrapper {
		var mContext = context
		val res:Resources = mContext.resources
		val configuration:Configuration = res.configuration

		//注意 Android 7.0 處理方式不同
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


	// 手動切換語言
	// 使用方式: changeLanguage("zh-rTW") "ja" "en"
	fun changeLanguage(activity:ComponentActivity, langText:String) {
		val lang = LanguageUtils.getLocale(activity)
		if (lang.language == langText) return
		val newLang = langText
		LanguageUtils.setLanguage(activity, newLang)
		activity.recreate()
	}
}

