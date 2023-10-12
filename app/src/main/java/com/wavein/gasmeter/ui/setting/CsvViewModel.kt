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
import com.wavein.gasmeter.data.model.toMeterRows
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

@HiltViewModel
class CsvViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	//...
) : ViewModel() {

	val readFileStateFlow = MutableStateFlow(ReadFileState())
	val selectedFileStateFlow = MutableStateFlow(FileState())

	// 檔案選取器
	fun openFilePicker(filePickerLauncher:ActivityResultLauncher<Intent>) {
		val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
			addCategory(Intent.CATEGORY_OPENABLE)
			type = "text/*" // 檔案類型 ("text/csv"會無效)
		}
		filePickerLauncher.launch(intent)
	}

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
		meterVM.meterRowsStateFlow.value = csvRows.toMeterRows()
		meterVM.setSelectedMeterGroup(null)
		setFileState(context, uri, specifiedFilename)
	}

	// 設為選擇的檔案
	@SuppressLint("Range")
	private fun setFileState(context:Context, uri:Uri, specifiedFilename:String = "") {
		val contentResolver:ContentResolver = context.contentResolver
		val cursor:Cursor? = contentResolver.query(uri, null, null, null, null)
		val filepath = uri.path ?: ""
		var filename = specifiedFilename
		var size = 0L
		if (filename.isEmpty()) {
			cursor?.use {
				if (it.moveToFirst()) {
					filename = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
					size = it.getLong(it.getColumnIndex(OpenableColumns.SIZE))
					it.close()
				}
			}
		}
		val fileState = FileState(uri = uri, path = filepath, name = filename, size = size)
		selectedFileStateFlow.value = fileState
	}

	// 儲存選擇的檔案(本地)
	fun saveCsv(meterVM:MeterViewModel) {
		val fileState = selectedFileStateFlow.value
		val csvRows = meterVM.meterRowsStateFlow.value.toCsvRows()

		// 轉成沒有key的csvRows:List<Map<string, string>>
		val header = csvRows.firstOrNull()?.keys?.toList() ?: return
		val rowsWithoutKey = listOf(header) + csvRows.map { row -> row.values.toList() }
		val csvContent = csvWriter().writeAllAsString(rowsWithoutKey)
		writeFile(fileState.relativePath, csvContent)
	}

	// 寫入檔案
	private fun writeFile(relativePath:String, content:String) {
		// 分解路徑 -> dirs & filename
		val relativeFile = File(relativePath)
		val dirs = relativeFile.parent?.split(File.separator) ?: emptyList()
		val filename = relativeFile.name
		if (filename.isEmpty()) {
			viewModelScope.launch {
				SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("缺少檔名", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
			}
			return
		}
		// 進入該目錄 & 寫入檔案
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
			try {
				// 寫入的字碼格式為 utf8 with bom
				outputStream.write(bom + content.toByteArray())
			} catch (e:IOException) {
				e.printStackTrace()
			} finally {
				outputStream.close()
			}
		}
	}
}

// 檔案資訊
data class FileState(val uri:Uri? = null, val path:String = "", val name:String = "", val size:Long = 0L) {
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