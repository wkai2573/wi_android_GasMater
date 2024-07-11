package com.wavein.gasmeter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Application : Application() {

	companion object {
		const val IS_DEV_MODE = true
	}
}