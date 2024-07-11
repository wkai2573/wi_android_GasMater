package com.wavein.gasmeter.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

	@Provides
	@Singleton
	fun provideBluetoothManager(@ApplicationContext context:Context):BluetoothManager? =
		context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager


	@Provides
	@Singleton
	fun provideBluetoothAdapter(bluetoothManager:BluetoothManager?):BluetoothAdapter? =
		bluetoothManager?.adapter


	@Provides
	@Singleton
	fun provideBluetoothLeScanner(bluetoothAdapter:BluetoothAdapter?):BluetoothLeScanner? =
		bluetoothAdapter?.bluetoothLeScanner

}
