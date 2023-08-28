package com.wavein.gasmater.ui.csv

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class ReadFileState(val type:Type = Type.Idle, val message:String? = null) {
	enum class Type { Idle, Reading, ReadFailed }
}

@HiltViewModel
class CsvViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
	// 注入實例
	//...
) : ViewModel() {


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