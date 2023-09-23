package com.wavein.gasmeter.ui.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.wavein.gasmeter.tools.SharedEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import javax.inject.Inject

@HiltViewModel
class CsvViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	//...
) : ViewModel() {

	val readFileStateFlow = MutableStateFlow(ReadFileState())
	val selectedFileStateFlow = MutableStateFlow(FileState())
	val rowsStateFlow = MutableStateFlow<List<Map<String, String>>>(emptyList())

	// 選擇本地Csv檔案
	fun selectCsv(filePickerLauncher:ActivityResultLauncher<Intent>) {
		// 預設開啟位置:下載資料夾
		val pickerInitialUri = DocumentsContract.buildDocumentUri("com.android.providers.downloads.documents", "downloads")
		val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
			addCategory(Intent.CATEGORY_OPENABLE)
			type = "text/*" // 檔案類型 ("text/csv"會無效)
			putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
		}
		filePickerLauncher.launch(intent)
	}

	// 讀取csv檔案
	fun readCsv(context:Context, result:ActivityResult) = viewModelScope.launch {
		readFileStateFlow.value = ReadFileState(ReadFileState.Type.Reading)
		if (result.resultCode == Activity.RESULT_OK) {
			kotlin.runCatching {
				val uri = result.data?.data!!
				val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
				val inputStream = FileInputStream(fileDescriptor!!.fileDescriptor)
				val rows:List<Map<String, String>> = csvReader().readAllWithHeader(inputStream)
				readFileStateFlow.value = ReadFileState(ReadFileState.Type.Idle)
				rowsStateFlow.value = rows
				setFileState(context, uri)
			}.onFailure {
//				readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, it.message)
				SharedEvent._eventFlow.emit(SharedEvent.ShowSnackbar(it.message ?: "", SharedEvent.SnackbarColor.Error))
			}
		} else {
			readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, result.resultCode.toString())
		}
	}

	@SuppressLint("Range")
	private fun setFileState(context:Context, uri:Uri) {
		val contentResolver:ContentResolver = context.contentResolver
		val cursor:Cursor? = contentResolver.query(uri, null, null, null, null)
		val filePath = uri.path ?: ""
		var fileName = ""
		var size = 0L
		cursor?.use {
			if (it.moveToFirst()) {
				fileName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
				size = it.getLong(it.getColumnIndex(OpenableColumns.SIZE))
				it.close()
			}
		}
		val fileState = FileState(uri = uri, path = filePath, name = fileName, size = size)
		selectedFileStateFlow.value = fileState
	}
}

data class FileState(val uri:Uri? = null, val path:String = "", val name:String = "", val size:Long = 0L) {
	val isOpened get() = uri != null && path.isNotEmpty()
	private val extension:String
		get() {
			if (!name.contains(".")) return ""
			return name.substring(name.lastIndexOf(".") + 1)
		}
	val isCsv get() = extension.uppercase() == "CSV".uppercase()
}

data class ReadFileState(val type:Type = Type.Idle, val message:String? = null) {
	enum class Type { Idle, Reading, ReadFailed }
}

// Uri 轉 FileDescriptor
fun Uri.toFileDescriptor(context:Context):FileDescriptor? =
	context.contentResolver.openFileDescriptor(this, "r")?.fileDescriptor