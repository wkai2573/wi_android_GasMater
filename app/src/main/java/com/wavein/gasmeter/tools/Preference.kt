package com.wavein.gasmeter.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// APP儲存資料: 參考jim的PreferenceProvider
object Preference {

	// 外部使用的key
	const val APP_ENV = "appEnv"
	const val APP_KEY = "appKey"
	const val TITLE = "title"
	const val LOGO_URL = "logoUrl"
	const val DATE_EXPIRED = "dateExpired"

	private const val SHARED_PREF_NAME = "secret_shared_prefs_webrtc"

	private lateinit var sharedPreferences:SharedPreferences

	// 初始化
	fun init(context:Context) {
		val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
			.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
			.build()

		sharedPreferences = EncryptedSharedPreferences.create(
			context,
			SHARED_PREF_NAME,
			masterKey,
			EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
			EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
		)
	}

	// 清空
	fun clear(key:String) {
		sharedPreferences.edit().remove(key).apply()
	}

	// 儲存
	operator fun <T> set(key:String, value:T?) {
		val editor = sharedPreferences.edit()
		when (value) {
			is Boolean -> {
				editor.putBoolean(key, (value as Boolean?)!!)
			}
			is Set<*> -> {
				editor.putStringSet(key, value as Set<String?>?)
			}
			is String -> {
				editor.putString(key, value as String?)
			}
			is Float -> {
				editor.putFloat(key, (value as Float?)!!)
			}
			is Long -> {
				editor.putLong(key, (value as Long?)!!)
			}
			is Int -> {
				editor.putInt(key, (value as Int?)!!)
			}
		}
		editor.apply()
	}

	// 讀取
	operator fun <T> get(key:String, defaultValue:T):T? {
		when (defaultValue) {
			is Boolean -> {
				val ret = sharedPreferences.getBoolean(key, (defaultValue as Boolean?)!!)
				return ret as T
			}
			is Collection<*> -> {
				val result = sharedPreferences.getStringSet(key, HashSet<String>())
				return result as T?
			}
			is String -> {
				val ret = sharedPreferences.getString(key, defaultValue as String?)
				return ret as T?
			}
			is Float -> {
				val result = sharedPreferences.getFloat(key, (defaultValue as Float?)!!)
				return result as T
			}
			is Long -> {
				val result = sharedPreferences.getLong(key, (defaultValue as Long?)!!)
				return result as T
			}
			is Int -> {
				val result = sharedPreferences.getInt(key, (defaultValue as Int?)!!)
				return result as T
			}
			else -> return null
		}
	}

}