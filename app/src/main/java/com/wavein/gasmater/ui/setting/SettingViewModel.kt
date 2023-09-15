package com.wavein.gasmater.ui.setting

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
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavein.gasmater.tools.SharedEvent
import com.wavein.gasmater.ui.csv.ReadFileState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
) : ViewModel() {

	// CSV檔案__________________________________________________

	val readFileStateFlow = MutableStateFlow<ReadFileState>(ReadFileState())
	val rowsStateFlow = MutableStateFlow<List<Map<String, String>>>(emptyList())

	// 選擇並讀取csv資料
	fun selectReadCsv(filePickerLauncher:ActivityResultLauncher<Intent>) {
		// 預設開啟位置:下載資料夾
		val pickerInitialUri = DocumentsContract.buildDocumentUri("com.android.providers.downloads.documents", "downloads")
		val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
			addCategory(Intent.CATEGORY_OPENABLE)
			type = "*/*" // 檔案類型 ("text/csv"會無效)
			putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
		}
		filePickerLauncher.launch(intent)
	}


}

