package com.wavein.gasmeter.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavein.gasmeter.tools.SharedEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject


@HiltViewModel
@SuppressLint("MissingPermission")
class BleViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle,

	private val bluetoothManager:BluetoothManager?,
	private val bluetoothAdapter:BluetoothAdapter?,
	private val bluetoothLeScanner:BluetoothLeScanner?,
) : ViewModel() {

	init {
		if (bluetoothAdapter == null) {
			viewModelScope.launch {
				SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("設備不支援藍牙", SharedEvent.Color.Error))
			}
		}
	}





	lateinit var leDeviceListAdapter:DeviceListAdapter


	private val scanFilters:List<ScanFilter> = listOf(
		ScanFilter.Builder()

			.build()
	)
	private val scanSettings:ScanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
	private val scanPeriod:Long = 5000


	val scanStateFlow = MutableStateFlow(ScanState.Idle)
	val bleDeviceListStateFlow = MutableStateFlow(emptyList<BluetoothDevice>())


	fun setDeviceOnClick(itemOnClick:(BluetoothDevice) -> Unit) {
		leDeviceListAdapter = DeviceListAdapter(itemOnClick)
	}


	fun scanLeDevice() = viewModelScope.launch {
		if (scanStateFlow.value != ScanState.Scanning) {
			scanStateFlow.value = ScanState.Scanning
			bluetoothLeScanner?.startScan(scanFilters, scanSettings, leScanCallback)
			delay(scanPeriod)
			scanStateFlow.value = ScanState.Idle
			bluetoothLeScanner?.stopScan(leScanCallback)
		} else {
			scanStateFlow.value = ScanState.Idle
			bluetoothLeScanner?.stopScan(leScanCallback)
		}
	}


	private val leScanCallback = object : ScanCallback() {
		override fun onScanResult(callbackType:Int, result:ScanResult) {
			super.onScanResult(callbackType, result)
			val btDeviceList = bleDeviceListStateFlow.value.toMutableList()
			if (!btDeviceList.any { it.address == result.device.address }) {
				btDeviceList.add(result.device)
				bleDeviceListStateFlow.value = btDeviceList.toList()
			}
		}
	}




	private var bluetoothGatt:BluetoothGatt? = null


	val serviceUuid = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")!!
	val characteristicUuid = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")!!


	val bluetoothProfileStateFlow = MutableStateFlow<Int>(-1)



	fun connectBleDevice(context:Context, bleDevice:BluetoothDevice) {
		bluetoothGatt = bleDevice.connectGatt(context, true, gattCallback)
	}


	fun closeConnect() {
		bluetoothGatt?.close()
		bluetoothGatt = null
	}


	private val gattCallback = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(gatt:BluetoothGatt, status:Int, newState:Int) {
			bluetoothProfileStateFlow.value = newState
			when (newState) {

				BluetoothProfile.STATE_CONNECTED -> {
					gatt.discoverServices()
				}


				BluetoothProfile.STATE_DISCONNECTED -> {
					bluetoothGatt = null
				}
			}
		}

		override fun onServicesDiscovered(gatt:BluetoothGatt, status:Int) {

			val service = gatt.getService(serviceUuid)
			val characteristic = service?.getCharacteristic(characteristicUuid)

			if (characteristic != null) {



			}
		}
	}

}