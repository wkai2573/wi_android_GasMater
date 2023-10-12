package com.wavein.gasmeter.ui.ftp

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.view.View
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.TimeUtil
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel
import com.wavein.gasmeter.ui.setting.FileState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class FtpViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
) : ViewModel() {

	// 可觀察變數
	var systemAreaOpenedStateFlow = MutableStateFlow(false)
	val appStateFlow = MutableStateFlow(AppState.NotChecked)
	val ftpConnStateFlow = MutableStateFlow<FtpConnState>(FtpConnState.Idle)

	// 變數
	@SuppressLint("StaticFieldLeak")
	var snackbarView:View? = null
	var snackbarAnchorView:View? = null

	var systemFtpInfo:FtpInfo = FtpInfo(
		FtpEnum.System,
		Preference[Preference.FTP_SYSTEM_HOST, "118.163.191.31"]!!,
		Preference[Preference.FTP_SYSTEM_USERNAME, "aktwset01"]!!,
		Preference[Preference.FTP_SYSTEM_PASSWORD, "NSsetup09"]!!,
		Preference[Preference.FTP_SYSTEM_ROOT, "WaveIn/system"]!!
	)
	var downloadFtpInfo:FtpInfo = FtpInfo(
		FtpEnum.Download,
		Preference[Preference.FTP_DOWNLOAD_HOST, "118.163.191.31"]!!, //todo 前三個要改回""
		Preference[Preference.FTP_DOWNLOAD_USERNAME, "aktwset01"]!!,
		Preference[Preference.FTP_DOWNLOAD_PASSWORD, "NSsetup09"]!!,
		Preference[Preference.FTP_DOWNLOAD_ROOT, "WaveIn/download"]!!
	)
	var uploadFtpInfo:FtpInfo = FtpInfo(
		FtpEnum.Upload,
		Preference[Preference.FTP_UPLOAD_HOST, "118.163.191.31"]!!, //todo 前三個要改回""
		Preference[Preference.FTP_UPLOAD_USERNAME, "aktwset01"]!!,
		Preference[Preference.FTP_UPLOAD_PASSWORD, "NSsetup09"]!!,
		Preference[Preference.FTP_UPLOAD_ROOT, "WaveIn/upload"]!!
	)

	fun saveFtpInfo(ftpInfo:FtpInfo) {
		val mFtpInfo = when (ftpInfo.ftpEnum) {
			FtpEnum.System -> {
				Preference[Preference.FTP_SYSTEM_HOST] = ftpInfo.host
				Preference[Preference.FTP_SYSTEM_USERNAME] = ftpInfo.username
				Preference[Preference.FTP_SYSTEM_PASSWORD] = ftpInfo.password
				Preference[Preference.FTP_SYSTEM_ROOT] = ftpInfo.root
				systemFtpInfo
			}

			FtpEnum.Download -> {
				Preference[Preference.FTP_DOWNLOAD_HOST] = ftpInfo.host
				Preference[Preference.FTP_DOWNLOAD_USERNAME] = ftpInfo.username
				Preference[Preference.FTP_DOWNLOAD_PASSWORD] = ftpInfo.password
				Preference[Preference.FTP_DOWNLOAD_ROOT] = ftpInfo.root
				downloadFtpInfo
			}

			FtpEnum.Upload -> {
				Preference[Preference.FTP_UPLOAD_HOST] = ftpInfo.host
				Preference[Preference.FTP_UPLOAD_USERNAME] = ftpInfo.username
				Preference[Preference.FTP_UPLOAD_PASSWORD] = ftpInfo.password
				Preference[Preference.FTP_UPLOAD_ROOT] = ftpInfo.root
				uploadFtpInfo
			}
		}
		mFtpInfo.apply {
			host = ftpInfo.host
			username = ftpInfo.username
			password = ftpInfo.password
			root = ftpInfo.root
		}
	}

	// 編碼
	private val LOCAL_CHARSET = Charsets.UTF_8
	private val SERVER_CHARSET = Charsets.ISO_8859_1
	private fun encode(text:String):String = String(text.toByteArray(LOCAL_CHARSET), SERVER_CHARSET)
	private fun decode(text:String):String = String(text.toByteArray(SERVER_CHARSET), LOCAL_CHARSET)

	/**
	 * ftp連線到結束
	 * @param ftpInfo FTP連線資訊
	 * @param path 進入目錄
	 * @param autoDisconnect 自動中斷FTP，若連線中有延遲處理則需要設為false，並自行呼叫disconnect(ftpClient)
	 * @param ftpLoginError 當連結失敗時的處理，return true 顯示原因小吃(預設)
	 * @param ftpHandle 連線FTP中要做的事
	 */
	private fun ftpProcess(
		ftpInfo:FtpInfo,
		path:String = "",
		autoDisconnect:Boolean = true,
		ftpLoginError:() -> Boolean = { true },
		ftpHandle:(ftpClient:FTPClient) -> Unit = {}
	) {
		CoroutineScope(Dispatchers.IO).launch {
			val ftpClient = FTPClient()
			try {
				ftpConnStateFlow.value = FtpConnState.Connecting("正在連線FTP")
				ftpClient.connect(ftpInfo.host)
				ftpConnStateFlow.value = FtpConnState.Connecting("FTP登入中")
				if (!ftpClient.login(ftpInfo.username, ftpInfo.password)) {
					if (ftpLoginError()) showSnack("無法登入FTP")
					return@launch
				}
				ftpConnStateFlow.value = FtpConnState.Connected
				ftpClient.enterLocalPassiveMode()
				if (ftpInfo.root.isNotEmpty() && !ftpClient.changeWorkingDirectory(encode(ftpInfo.root))) {
					if (ftpLoginError()) showSnack("${ftpInfo.root} 目錄無法開啟")
					return@launch
				}
				if (path.isNotEmpty() && !ftpClient.makeAndChangeDirectory(encode(path))) {
					if (ftpLoginError()) showSnack("$path 目錄無法開啟")
					return@launch
				}
				ftpHandle(ftpClient)
			} catch (e:Exception) {
				if (ftpLoginError()) {
					if (e.message?.contains("Network is unreachable") == true) {
						showSnack("未連結網路")
					} else {
						showSnack("[FTP Error]\n${e.message}")
					}
				}
			} finally {
				if (autoDisconnect) disconnect(ftpClient)
			}
		}
	}

	private fun disconnect(ftpClient:FTPClient) {
		kotlin.runCatching { ftpClient.logout() }
		kotlin.runCatching { ftpClient.disconnect() }
		ftpConnStateFlow.value = FtpConnState.Idle
	}

	// ==Dialog==
	// 測試FTP
	fun testFtp(ftpInfo:FtpInfo) {
		ftpProcess(ftpInfo, "") { ftpClient ->
			showSnack("連線成功", SharedEvent.Color.Success)
		}
	}

	// ==SYSTEM==
	// 檢查產品開通
	fun checkAppActivate(uuid:String, appkey:String) {
		appStateFlow.value = AppState.Checking
		if (uuid.isEmpty() || appkey.isEmpty()) {
			appStateFlow.value = AppState.Inactivated
			return
		}
		ftpProcess(systemFtpInfo, "key",
			ftpLoginError = {
				appStateFlow.value = AppState.Inactivated
				return@ftpProcess true
			},
			ftpHandle = { ftpClient ->
				if (!ftpClient.changeWorkingDirectory(encode(appkey))) {
					onAppkeyVerifyFail("序號錯誤")
					return@ftpProcess
				}
				val uuidFilename = "$uuid.uuid"
				val files = ftpClient.listFiles()
				when {
					// 序號未被註冊，該序號綁定此裝置
					files.isEmpty() -> {
						ftpClient.setFileType(FTP.ASCII_FILE_TYPE)
						val emptyInputStream = "".byteInputStream()
						if (ftpClient.storeFile(encode(uuidFilename), emptyInputStream)) {
							onAppkeyVerifySuccess("產品開通成功", appkey)
						} else {
							onAppkeyVerifyFail("產品開通失敗 (無法建立uuid檔案於FTP)")
						}
					}
					// 檢查序號正確
					decode(files[0].name) == uuidFilename -> {
						Preference[Preference.APP_KEY] = appkey
						Preference[Preference.APP_ACTIVATED] = true
						appStateFlow.value = AppState.Activated
					}
					// 檢查序號錯誤，該序號已被其他裝置綁定
					else -> onAppkeyVerifyFail("此序號已被其他裝置註冊")
				}
			})
	}

	private fun onAppkeyVerifySuccess(msg:String, appkey:String) {
		showSnack(msg, SharedEvent.Color.Success)
		Preference[Preference.APP_KEY] = appkey
		Preference[Preference.APP_ACTIVATED] = true
		appStateFlow.value = AppState.Activated
	}

	private fun onAppkeyVerifyFail(msg:String) {
		showSnack(msg)
		Preference[Preference.APP_KEY] = ""
		Preference[Preference.APP_ACTIVATED] = false
		appStateFlow.value = AppState.Inactivated
	}

	private fun showSnack(msg:String, snackbarColor:SharedEvent.Color = SharedEvent.Color.Error) {
		viewModelScope.launch {
			SharedEvent.eventFlow.emit(
				SharedEvent.ShowSnackbar(
					msg, snackbarColor,
					duration = if (snackbarColor == SharedEvent.Color.Error) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_SHORT,
					view = snackbarView, anchorView = snackbarAnchorView
				)
			)
		}
	}

	// ==UPLOAD==
	fun uploadFile(context:Context, fileState:FileState) {
		val path = Preference[Preference.APP_KEY, ""]!!
		if (path.isEmpty() || !fileState.isOpened) return

		ftpProcess(uploadFtpInfo, path) { ftpClient ->
			val filenameWithTime = "${fileState.nameWithoutExtension}_${TimeUtil.getCurrentTime("yyyyMMddHH")}.${fileState.extension}"
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
			context.contentResolver.openFileDescriptor(fileState.uri!!, "r").use { parcelFileDescriptor ->
				val fileDescriptor = parcelFileDescriptor?.fileDescriptor
				val inputStream = FileInputStream(fileDescriptor)
				ftpClient.storeFile(encode(filenameWithTime), inputStream)
				showSnack("上傳成功, FTP目錄: $path/$filenameWithTime", SharedEvent.Color.Success)
			}
		}
	}

	// ==DOWNLOAD==
	fun downloadFileOpenFolder(context:Context, csvVM:CsvViewModel, meterVM:MeterViewModel) {
		ftpProcess(downloadFtpInfo, "") { ftpClient ->
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
			val fileArray = ftpClient.listFiles()
				.map { ftpFile -> decode(ftpFile.name) }
				.filter { filename -> filename.endsWith(".csv", true) }
				.toTypedArray()
			if (fileArray.isEmpty()) {
				showSnack("沒有找到任何.csv檔案")
				return@ftpProcess
			}
			viewModelScope.launch {
				val builder = MaterialAlertDialogBuilder(context)
					.setTitle("選擇檔案")
					.setItems(fileArray) { dialogInterface, index ->
						val filename = fileArray[index]
						downloadFile(context, csvVM, meterVM, filename)
						dialogInterface.dismiss()
					}
					.create()
				SharedEvent.eventFlow.emit(SharedEvent.ShowDialogB(builder))
			}
		}
	}

	private fun downloadFile(context:Context, csvVM:CsvViewModel, meterVM:MeterViewModel, filename:String) {
		ftpProcess(downloadFtpInfo, "") { ftpClient ->
			val rootDirectory = Environment.getExternalStorageDirectory()
			// 下載到Download資料夾
			 val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
			// 下載到"外部空間根目錄/NXU Gas Meter"
			// val directory = File(rootDirectory, "NXU Gas Meter")
			directory.mkdirs()

			val localFile = File(directory, filename)
			val outputStream = FileOutputStream(localFile)
			val success = ftpClient.retrieveFile(encode(filename), outputStream)
			outputStream.close()
			if (success) {
				showSnack("\"$filename\" 已保存於 \"/${directory.toRelativeString(rootDirectory)}\"", SharedEvent.Color.Success)
				csvVM.readCsv(context, Uri.fromFile(localFile), meterVM, filename)
			} else {
				showSnack("下載失敗")
			}
		}
	}

	//__________ 以下參考 __________

	// 上傳空文件
	fun uploadEmptyFile(ftpInfo:FtpInfo, path:String, filename:String) {
		ftpProcess(ftpInfo, path) { ftpClient ->
			ftpClient.setFileType(FTP.ASCII_FILE_TYPE)
			val emptyInputStream = "".byteInputStream()
			ftpClient.storeFile(filename, emptyInputStream)
		}
	}

	// 上傳文件
	fun uploadFile_r(ftpInfo:FtpInfo, path:String, filename:String, fileDescriptor:FileDescriptor) {
		ftpProcess(ftpInfo, path) { ftpClient ->
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
			val inputStream = FileInputStream(fileDescriptor)
			ftpClient.storeFile(encode(filename), inputStream)
		}
	}

	// 刪除檔案
	fun deleteFile(ftpInfo:FtpInfo, path:String, filename:String) {
		ftpProcess(ftpInfo, path) { ftpClient ->
			ftpClient.deleteFile(filename)
		}
	}

	// todo 問chatGPT用
	fun uploadFileToFtp(filename:String, host:String, username:String, password:String, directory:String, fileDescriptor:FileDescriptor) {
		val ftpClient = FTPClient()
		ftpClient.connect(host)
		ftpClient.login(username, password)
		ftpClient.enterLocalPassiveMode()
		ftpClient.setFileType(FTP.ASCII_FILE_TYPE)
		ftpClient.changeWorkingDirectory(directory)

		ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
		val inputStream = FileInputStream(fileDescriptor)
		ftpClient.storeFile(filename, inputStream)
		inputStream.close()

		ftpClient.logout()
		ftpClient.disconnect()
	}

}

// 進入目錄，沒有則創建再進入 (進入根的時候不要用)
private fun FTPClient.makeAndChangeDirectory(directory:String):Boolean {
	val directories = directory.split("/")
	for (dir in directories) {
		if (dir.isEmpty()) continue
		if (!this.changeWorkingDirectory(dir)) {
			this.makeDirectory(dir)
			if (!this.changeWorkingDirectory(dir)) {
				return false
			}
		}
	}
	return true
}

// class
data class FtpInfo(val ftpEnum:FtpEnum, var host:String = "", var username:String = "", var password:String = "", var root:String = "")
enum class FtpEnum { System, Download, Upload }

enum class AppState { NotChecked, Checking, Activated, Inactivated }

sealed class FtpConnState {
	object Idle : FtpConnState()
	data class Connecting(val msg:String) : FtpConnState()
	object Connected : FtpConnState()
	// data class Error(val msg:String) : FtpConnState()
}