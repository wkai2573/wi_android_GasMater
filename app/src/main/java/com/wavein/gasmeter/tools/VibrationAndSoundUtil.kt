package com.wavein.gasmeter.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.wavein.gasmeter.R

// https://chat.openai.com/share/19842966-df8b-415d-81bb-83c9dc2e40b3
class VibrationAndSoundUtil(private val context:Context) {

	private var soundPool:SoundPool? = null
	private var soundId:Int = 0
	private val vibrator:Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

	init {
		// 初始化 SoundPool
		soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			val audioAttributes = AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.build()

			SoundPool.Builder()
				.setMaxStreams(1)
				.setAudioAttributes(audioAttributes)
				.build()
		} else {
			SoundPool(1, AudioManager.STREAM_MUSIC, 0)
		}

		// 載入音效檔案
		soundId = soundPool?.load(context, R.raw.achive_sound, 1) ?: 0
	}

	// 震動並播放音效的函數
	fun vibrateAndPlaySound(vibrate:Boolean = true, sound:Boolean = true) {
		// 震動
		if (vibrate) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
			} else {
				// 兼容舊版本的震動
				vibrator.vibrate(200)
			}
		}

		// 播放音效
		if (sound) {
			soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
		}
	}

	// 釋放資源的函數，應該在不再使用時呼叫
	fun releaseResources() {
		soundPool?.release()
		vibrator.cancel()
	}
}