package com.wavein.gasmater.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

private const val TAG = "BluetoothLeService"

class BluetoothLeService : Service() {

	private val binder = LocalBinder()

	override fun onBind(intent:Intent):IBinder {
		return binder
	}

	private var bluetoothAdapter:BluetoothAdapter? = null

	fun initialize(context:Context):Boolean {
		val bluetoothManager:BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
		bluetoothAdapter = bluetoothManager?.adapter
		if (bluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
			return false
		}
		return true
	}

	inner class LocalBinder : Binder() {
		fun getService():BluetoothLeService {
			return this@BluetoothLeService
		}
	}
}