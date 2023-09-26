package com.wavein.gasmeter.ui.setting

import android.annotation.SuppressLint
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavein.gasmeter.tools.SharedEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.util.UUID
import javax.inject.Inject

const val uuidFilename = ".wavein.gasmeter.uuid"

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
) : ViewModel() {

	val uuidStateFlow = MutableStateFlow("")

	init {
		initUuid()
	}

	// 初始化UUID
	private fun initUuid() = viewModelScope.launch {
		val uuid = readDocumentFileContent() ?: createUuidFileSaveToExternalStorage()
		if (uuid == null) {
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("無法生成UUID", SharedEvent.SnackbarColor.Error))
		} else {
			uuidStateFlow.value = uuid
		}
	}

	// 讀取UUID
	private fun readDocumentFileContent():String? {
		try {
			val documentsDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), uuidFilename)

			return if (documentsDirectory.exists()) {
				val reader = BufferedReader(FileReader(documentsDirectory))
				val stringBuilder = StringBuilder()
				var line:String? = reader.readLine()
				while (line != null) {
					stringBuilder.append(line).append("\n")
					line = reader.readLine()
				}
				reader.close()

				val fileContent = stringBuilder.toString().trimEnd('\n')
				fileContent
			} else {
				null // File does not exist.
			}
		} catch (e:Exception) {
			e.printStackTrace()
			return null
		}
	}

	// 產生UUID檔案並儲存至文件資料夾
	private fun createUuidFileSaveToExternalStorage():String? {
		val uuid = UUID.randomUUID().toString().substring(0, 8)
		val directoryType = Environment.DIRECTORY_DOCUMENTS

		try {
			val externalStorageState = Environment.getExternalStorageState()
			return if (Environment.MEDIA_MOUNTED == externalStorageState) {
				val directory = Environment.getExternalStoragePublicDirectory(directoryType)
				directory.mkdirs() // 確保目錄存在

				val file = File(directory, uuidFilename)
				val outputStream = FileOutputStream(file)
				outputStream.write(uuid.toByteArray())
				outputStream.close()
				uuid
			} else {
				// 外部存儲不可用，處理錯誤
				null
			}
		} catch (e:Exception) {
			e.printStackTrace()
			// 處理保存文件時出現異常
			return null
		}
	}


}

