package com.wavein.gasmeter.ui.setting

import android.annotation.SuppressLint
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.TimeUtils
import com.wavein.gasmeter.tools.rd64h.RD64H
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.util.UUID
import javax.inject.Inject

private const val uuidFilename = ".wavein.gasmeter.uuid"

private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

val SetOpMeaningMap = mapOf(
	"S16" to "表狀態",
	"S31" to "登錄母火流量",
	"S50" to "壓力遮斷判定值",
	"C41" to "中心遮斷制御",
	"C02" to "強制Session中斷",
)

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle,
) : ViewModel() {

	val uuidStateFlow = MutableStateFlow("")

	init {
		initUuid()
	}


	fun initUuid() = viewModelScope.launch {
		val uuid = readDocumentFileContent() ?: createUuidFileSaveToExternalStorage()
		if (uuid == null) {
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("無法生成UUID", SharedEvent.Color.Error))
		} else {
			uuidStateFlow.value = uuid
		}
	}


	private fun readDocumentFileContent():String? {
		runCatching {
			val documentsDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), uuidFilename)

			if (documentsDirectory.exists()) {
				val stringBuilder = StringBuilder()
				BufferedReader(FileReader(documentsDirectory)).use { reader ->
					var line:String? = reader.readLine()
					while (line != null) {
						stringBuilder.append(line).append("\n")
						line = reader.readLine()
					}
				}

				val fileContent = stringBuilder.toString().trimEnd('\n')
				return fileContent
			}
		}.onFailure {
			it.printStackTrace()
		}
		return null
	}


	private fun createUuidFileSaveToExternalStorage():String? {
		val directoryType = Environment.DIRECTORY_DOCUMENTS

		runCatching {
			val externalStorageState = Environment.getExternalStorageState()
			if (Environment.MEDIA_MOUNTED == externalStorageState) {
				val directory = Environment.getExternalStoragePublicDirectory(directoryType)
				directory.mkdirs()

				val file = File(directory, uuidFilename)
				FileOutputStream(file).use { outputStream ->
					runCatching {
						val uuid = UUID.randomUUID().toString().substring(0, 8)
						outputStream.write(uuid.toByteArray())
						return uuid
					}.onFailure {
						it.printStackTrace()
					}
				}
			}
		}.onFailure {
			it.printStackTrace()
		}
		return null
	}

	
	fun createLogFile(logRows:List<LogRow>) {
		if (logRows.isEmpty()) return
		val meterId = logRows[0].meterId

		val filename = "${meterId}_${TimeUtils.getCurrentTime("yyyyMMdd_HHmmss")}.log"
		val currentTime = TimeUtils.getCurrentTime()
		val rows:List<List<String>> = listOf(
			listOf("通信ID(表ID)", "時間", "操作", "操作碼", "原值", "新值"),
			*logRows.map {
				listOf(it.meterId, currentTime, SetOpMeaningMap[it.op] ?: "", it.op, it.oldValue, it.newValue)
			}.toTypedArray(),
		)
		val csvContent = csvWriter().writeAllAsString(rows)


		runCatching {
			val externalStorageState = Environment.getExternalStorageState()
			if (Environment.MEDIA_MOUNTED == externalStorageState) {
				val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
				documentsFolder.mkdirs()
				val logFolder = File(documentsFolder, "log")
				logFolder.mkdirs()
				val file = File(logFolder, filename)
				val outputStream = FileOutputStream(file)
				outputStream.write(BOM + csvContent.toByteArray())
			}
		}.onFailure {
			it.printStackTrace()
		}
	}

	data class LogRow(val meterId:String, val op:String, val oldValue:String = "", val newValue:String = "")

}

