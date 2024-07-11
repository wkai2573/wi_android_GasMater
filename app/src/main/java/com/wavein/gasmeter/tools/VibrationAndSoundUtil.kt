package com.wavein.gasmeter.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.wavein.gasmeter.R


class VibrationAndSoundUtil(private val context:Context) {

	private var soundPool:SoundPool? = null
	private var soundId:Int = 0
	private val vibrator:Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

	init {

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


		soundId = soundPool?.load(context, R.raw.achive_sound, 1) ?: 0
	}


	fun vibrateAndPlaySound(vibrate:Boolean = true, sound:Boolean = true) {

		if (vibrate) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
			} else {

				vibrator.vibrate(200)
			}
		}


		if (sound) {
			soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
		}
	}


	fun releaseResources() {
		soundPool?.release()
		vibrator.cancel()
	}
}