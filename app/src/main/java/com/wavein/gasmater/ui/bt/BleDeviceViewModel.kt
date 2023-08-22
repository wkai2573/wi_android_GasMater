package com.wavein.gasmater.ui.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class BleDeviceViewModel @Inject constructor(
	savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
) : ViewModel() {

	// 尋找BLE設備, https://developer.android.com/guide/topics/connectivity/bluetooth/find-ble-devices

	// 實例
	private lateinit var bluetoothManager:BluetoothManager
	private lateinit var bluetoothAdapter:BluetoothAdapter
	private lateinit var bluetoothLeScanner:BluetoothLeScanner
	lateinit var leDeviceListAdapter:LeDeviceListAdapter

	// 常數
	private val filters:List<ScanFilter> = listOf(
		ScanFilter.Builder()
			// .setDeviceName("Pokemon GO Plus +")
			.build()
	)
	private val scanSettings:ScanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
	private val scanPeriod:Long = 5000

	// 觀察變數
	val btDeviceListStateFlow = MutableStateFlow(emptyList<BluetoothDevice>())
	val scanningStateFlow = MutableStateFlow(false)

	// 初始化實例
	fun init(context:Context, itemOnClick:(BluetoothDevice) -> Unit) {
		bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		bluetoothAdapter = bluetoothManager.adapter
		bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
		leDeviceListAdapter = LeDeviceListAdapter(itemOnClick)
	}

	// 開始掃描
	@SuppressLint("MissingPermission")
	fun scanLeDevice() = viewModelScope.launch {
		if (!scanningStateFlow.value) {
			scanningStateFlow.value = true
			bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback)
			delay(scanPeriod)
			scanningStateFlow.value = false
			bluetoothLeScanner.stopScan(leScanCallback)
		} else {
			scanningStateFlow.value = false
			bluetoothLeScanner.stopScan(leScanCallback)
		}
	}

	// 掃描結果CB
	private val leScanCallback:ScanCallback = object : ScanCallback() {
		override fun onScanResult(callbackType:Int, result:ScanResult) {
			super.onScanResult(callbackType, result)
			val btDeviceList = btDeviceListStateFlow.value.toMutableList()
			if (!btDeviceList.any { it.address == result.device.address }) {
				btDeviceList.add(result.device)
				btDeviceListStateFlow.value = btDeviceList.toList()
			}
		}
	}

}