package com.wavein.gasmeter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Application : Application() {

	companion object {
		const val IS_DEV_MODE = false // todo 正式時記得設為false
		const val R89_CHANNEL_CODE = "66"
		const val R80_CHANNEL_CODE = "66"
	}
}