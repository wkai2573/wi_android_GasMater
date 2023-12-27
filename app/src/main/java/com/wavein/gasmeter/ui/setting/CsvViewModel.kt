package com.wavein.gasmeter.ui.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.data.model.toCsvRows
import com.wavein.gasmeter.data.model.toMeterGroups
import com.wavein.gasmeter.data.model.toMeterRows
import com.wavein.gasmeter.tools.FileUtils
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import javax.inject.Inject

private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

@HiltViewModel
class CsvViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	//...
) : ViewModel() {

	private val readFileStateFlow = MutableStateFlow(ReadFileState())
	val selectedFileStateFlow = MutableStateFlow(FileState())

	// 讀取csv檔案 by picker
	fun readCsvByPicker(context:Context, result:ActivityResult, meterVM:MeterViewModel) = viewModelScope.launch {
		readFileStateFlow.value = ReadFileState(ReadFileState.Type.Reading)
		if (result.resultCode == Activity.RESULT_OK) {
			kotlin.runCatching {
				val uri = result.data?.data!!
				readCsv(context, uri, meterVM)
			}.onFailure {
				it.printStackTrace()
				readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, it.message)
				SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(it.message ?: "", SharedEvent.Color.Error))
			}
		} else {
			readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, result.resultCode.toString())
		}
	}

	// 讀取csv檔案
	fun readCsv(context:Context, uri:Uri, meterVM:MeterViewModel, specifiedFilename:String = "") {
		val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
		val inputStream = FileInputStream(parcelFileDescriptor!!.fileDescriptor)
		val csvRows:List<Map<String, String>> = csvReader().readAllWithHeader(inputStream)
		parcelFileDescriptor.close()
		readFileStateFlow.value = ReadFileState(ReadFileState.Type.Idle)
		// 檢查CSV資料合法
		val meterRows = csvRows.toMeterRows()
		var message = checkRowsLegal(meterRows)
		if (message == "ok") {
			meterVM.meterRowsStateFlow.value = csvRows.toMeterRows()
			meterVM.setSelectedMeterGroup(null)
			setFileState(context, uri, specifiedFilename)
		} else {
			message += "\n請確認CSV檔案資料"
			viewModelScope.launch {
				SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(message, SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
			}
		}
	}

	// 檢查CSV資料合法
	//  1. 抄錶順路 & 客戶編號 & 表ID 不可重複
	//  2. 單群最多45台瓦斯表
	private fun checkRowsLegal(meterRows:List<MeterRow>):String {
		val duplicateQueue = meterRows.groupingBy { it.queue }.eachCount().filter { it.value > 1 }.keys
		if (duplicateQueue.isNotEmpty())
			return "抄錶順路重複: ${duplicateQueue.joinToString()}"

		val duplicateCustId = meterRows.groupingBy { it.custId }.eachCount().filter { it.value > 1 }.keys
		if (duplicateCustId.isNotEmpty())
			return "客戶編號重複: ${duplicateCustId.joinToString()}"

		val duplicateMeterId = meterRows.groupingBy { it.meterId }.eachCount().filter { it.value > 1 }.keys
		if (duplicateMeterId.isNotEmpty())
			return "表ID重複: ${duplicateMeterId.joinToString()}"

		val exceed45Groups = meterRows.groupBy { it.group }.filter { it.value.size > 45 }.keys
		if (exceed45Groups.isNotEmpty())
			return "超過45台瓦斯表群組: ${exceed45Groups.joinToString()}"

		return "ok"
	}

	// 設為選擇的檔案
	@SuppressLint("Range")
	private fun setFileState(context:Context, uri:Uri, specifiedFilename:String = "") {
		val contentResolver:ContentResolver = context.contentResolver
		val filepath = uri.path ?: ""
		var filename = specifiedFilename
		if (filename.isEmpty()) {
			filename = FileUtils.getFilename(context, uri) ?: ""
		}
		val fileState = FileState(uri = uri, path = filepath, name = filename)
		selectedFileStateFlow.value = fileState
	}

	// 更新ui並儲存csv
	fun updateSaveCsv(newCsvRows:List<MeterRow>, meterVM:MeterViewModel) {
		// 更新stateFlow
		val nowMeterGroup = meterVM.selectedMeterGroupStateFlow.value
		val nowMeterRow = meterVM.selectedMeterRowFlow.value
		meterVM.meterRowsStateFlow.value = newCsvRows
		meterVM.setSelectedMeterGroup(
			newCsvRows.toMeterGroups()
				.find { it.group == nowMeterGroup?.group })
		meterVM.selectedMeterRowFlow.value = meterVM.selectedMeterGroupStateFlow.value?.meterRows
			?.find { it.queue == nowMeterRow?.queue }
		// 儲存本地csv檔案
		saveCsv(meterVM)
	}

	// 儲存csv
	private fun saveCsv(meterVM:MeterViewModel) {
		val fileState = selectedFileStateFlow.value
		val csvRows = meterVM.meterRowsStateFlow.value.toCsvRows()

		// 轉成沒有key的csvRows:List<Map<string, string>>
		val header = csvRows.firstOrNull()?.keys?.toList() ?: return
		val rowsWithoutKey = listOf(header) + csvRows.map { row -> row.values.toList() }
		val csvContent = csvWriter().writeAllAsString(rowsWithoutKey)
		writeFile(fileState.relativePath, csvContent)
	}

	// 寫入檔案
	private fun writeFile(relativePath:String, content:String) = viewModelScope.launch {
		// 分解路徑 -> dirs & filename
		val relativeFile = File(relativePath)
		val dirs = relativeFile.parent?.split(File.separator) ?: emptyList()
		val filename = relativeFile.name
		if (filename.isEmpty()) {
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("缺少檔名", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
		}
		// 進入該目錄 & 寫入檔案
		withContext(Dispatchers.IO) {
			SharedEvent.catching {
				val externalStorageState = Environment.getExternalStorageState()
				if (Environment.MEDIA_MOUNTED == externalStorageState) {
					val rootDirectory = Environment.getExternalStorageDirectory()
					var directory = rootDirectory
					for (dir in dirs) {
						directory = File(directory, dir)
						directory.mkdirs()
					}
					val file = File(directory, filename)
					val outputStream = FileOutputStream(file)
					// 寫入的字碼格式為 utf8 with bom
					outputStream.use { it ->
						it.write(bom + content.toByteArray())
					}
				}
			}
		}
	}
}

// 檔案資訊
data class FileState(val uri:Uri? = null, val path:String = "", val name:String = "") {
	val isOpened get() = uri != null && path.isNotEmpty()
	val extension:String
		get() {
			if (!name.contains(".")) return ""
			return name.substring(name.lastIndexOf(".") + 1)
		}
	val nameWithoutExtension:String
		get() {
			if (!name.contains(".")) return name
			return name.substring(0, name.lastIndexOf("."))
		}
	val isCsv get() = extension.uppercase() == "CSV".uppercase()

	// 取得外部儲存空間的相對路徑
	val relativePath:String
		get() {
			// path有可能是以下2種
			// 文件系統路徑 "/storage/emulated/0/Download/OuO_EN_utf8.csv"
			// 文檔樹URI "/document/primary:Download/OuO_EN_utf8.csv"
			return path.removePrefix("/storage/emulated/0/").removePrefix("/document/primary:")
		}
}

// 檔案讀取中狀態
data class ReadFileState(val type:Type = Type.Idle, val message:String? = null) {
	enum class Type { Idle, Reading, ReadFailed }
}

// Uri 轉 FileDescriptor
fun Uri.toFileDescriptor(context:Context):FileDescriptor? =
	context.contentResolver.openFileDescriptor(this, "r")?.fileDescriptor