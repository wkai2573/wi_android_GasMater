package com.wavein.gasmeter.ui.ftp

import android.annotation.SuppressLint
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavein.gasmeter.tools.Preference
import com.wavein.gasmeter.tools.SharedEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.net.SocketException
import javax.inject.Inject

@HiltViewModel
class FtpViewModel @Inject constructor(
	private val savedStateHandle:SavedStateHandle, //導航參數(hilt注入)
) : ViewModel() {

	// 可觀察變數
	val appStateFlow = MutableStateFlow(AppState.NotChecked)

	// 變數
	@SuppressLint("StaticFieldLeak")
	var view:CoordinatorLayout? = null

	private var systemFtpState:FtpState = FtpState(
		Preference[Preference.FTP_SYSTEM_HOST, "118.163.191.31"]!!,
		Preference[Preference.FTP_SYSTEM_USERNAME, "aktwset01"]!!,
		Preference[Preference.FTP_SYSTEM_PASSWORD, "NSsetup09"]!!,
		Preference[Preference.FTP_SYSTEM_ROOT, "WaveIn/system"]!!
	)
	private var downloadFtpState:FtpState = FtpState(
		Preference[Preference.FTP_DOWNLOAD_HOST, ""]!!,
		Preference[Preference.FTP_DOWNLOAD_USERNAME, ""]!!,
		Preference[Preference.FTP_DOWNLOAD_PASSWORD, ""]!!,
		Preference[Preference.FTP_DOWNLOAD_ROOT, "WaveIn/data"]!!
	)
	private var uploadFtpState:FtpState = FtpState(
		Preference[Preference.FTP_UPLOAD_HOST, ""]!!,
		Preference[Preference.FTP_UPLOAD_USERNAME, ""]!!,
		Preference[Preference.FTP_UPLOAD_PASSWORD, ""]!!,
		Preference[Preference.FTP_UPLOAD_ROOT, "WaveIn/data"]!!
	)

	/**
	 * ftp連線到結束
	 * @param ftpState FTP連線資訊
	 * @param path 進入目錄
	 * @param ftpLoginError 當連結失敗時的處理，return true 顯示原因小吃
	 * @param ftpHandle 連線FTP中要做的事
	 */
	private fun ftpProcess(
		ftpState:FtpState,
		path:String = "",
		ftpLoginError:() -> Boolean = { true },
		ftpHandle:(FTPClient) -> Unit = {}
	) {
		CoroutineScope(Dispatchers.IO).launch {
			val ftpClient = FTPClient()
			try {
				ftpClient.connect(ftpState.host)
				if (!ftpClient.login(ftpState.username, ftpState.password)) {
					if (ftpLoginError()) SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("無法登入FTP", SharedEvent.SnackbarColor.Error, view = view))
					return@launch
				}
				ftpClient.enterLocalPassiveMode()
				if (ftpState.root.isNotEmpty() && !ftpClient.makeAndChangeDirectory(ftpState.root)) {
					if (ftpLoginError()) SharedEvent.eventFlow.emit(
						SharedEvent.ShowSnackbar("${ftpState.root} 目錄無法開啟", SharedEvent.SnackbarColor.Error, view = view)
					)
					return@launch
				}
				if (path.isNotEmpty() && !ftpClient.makeAndChangeDirectory(path)) {
					if (ftpLoginError()) SharedEvent.eventFlow.emit(
						SharedEvent.ShowSnackbar("$path 目錄無法開啟", SharedEvent.SnackbarColor.Error, view = view)
					)
					return@launch
				}
				ftpHandle(ftpClient)
				ftpClient.logout()
				ftpClient.disconnect()
			} catch (e:SocketException) {
				if (ftpLoginError()) SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("FTP連線逾時", SharedEvent.SnackbarColor.Error, view = view))
			} catch (e:java.net.UnknownHostException) {
				if (ftpLoginError()) SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("host解析錯誤", SharedEvent.SnackbarColor.Error, view = view))
			} catch (e:FTPConnectionClosedException) {
				if (ftpLoginError()) SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("FTP連線被中斷", SharedEvent.SnackbarColor.Error, view = view))
			} catch (e:IOException) {
				if (ftpLoginError()) SharedEvent.eventFlow.emit(SharedEvent.ShowDialog("FTP Error", e.message.toString()))
			}
		}
	}

	// 上傳空文件
	fun uploadEmptyFile(ftpState:FtpState, path:String, filename:String) {
		ftpProcess(ftpState, path) { ftpClient ->
			ftpClient.setFileType(FTP.ASCII_FILE_TYPE)
			val emptyInputStream = "".byteInputStream()
			ftpClient.storeFile(filename, emptyInputStream)
		}
	}

	// 上傳文件
	fun uploadEmptyFile(ftpState:FtpState, path:String, filename:String, fileDescriptor:FileDescriptor) {
		ftpProcess(ftpState, path) { ftpClient ->
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
			val inputStream = FileInputStream(fileDescriptor)
			ftpClient.storeFile(filename, inputStream)
		}
	}

	// 刪除檔案
	fun deleteFile(ftpState:FtpState, path:String, filename:String) {
		ftpProcess(ftpState, path) { ftpClient ->
			ftpClient.deleteFile(filename)
		}
	}

	// SYSTEM: 檢查產品開通
	fun checkAppActivate(uuid:String, appkey:String) {
		if (appStateFlow.value in listOf(AppState.Checking, AppState.Activated)) return
		appStateFlow.value = AppState.Checking
		if (uuid.isEmpty() || appkey.isEmpty()) {
			appStateFlow.value = AppState.Inactivated
			return
		}
		ftpProcess(systemFtpState, "key", ftpLoginError = { false }) { ftpClient ->
			if (!ftpClient.changeWorkingDirectory(appkey)) {
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
					if (ftpClient.storeFile(uuidFilename, emptyInputStream)) {
						onAppkeyVerifySuccess("產品開通成功", appkey)
					} else {
						onAppkeyVerifyFail("產品開通失敗 (無法建立uuid檔案於FTP)")
					}
				}
				// 檢查序號正確
				files[0].name == uuidFilename -> {
					Preference[Preference.APP_KEY] = appkey
					Preference[Preference.APP_ACTIVATED] = true
					appStateFlow.value = AppState.Activated
				}
				// 檢查序號錯誤，該序號已被其他裝置綁定
				else -> onAppkeyVerifyFail("此序號已被其他裝置註冊")
			}
		}
	}

	private inline fun onAppkeyVerifySuccess(msg:String, appkey:String) {
		viewModelScope.launch {
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(msg, SharedEvent.SnackbarColor.Success, view = view))
		}
		Preference[Preference.APP_KEY] = appkey
		Preference[Preference.APP_ACTIVATED] = true
		appStateFlow.value = AppState.Activated
	}

	private inline fun onAppkeyVerifyFail(msg:String) {
		viewModelScope.launch {
			SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(msg, SharedEvent.SnackbarColor.Error, view = view))
		}
		Preference[Preference.APP_KEY] = ""
		Preference[Preference.APP_ACTIVATED] = false
		appStateFlow.value = AppState.Inactivated
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

// 進入目錄，沒有則創建再進入
private fun FTPClient.makeAndChangeDirectory(directory:String):Boolean {
	val directories = directory.split("/")
	for (dir in directories) {
		if (!this.changeWorkingDirectory(dir)) {
			this.makeDirectory(dir)
			if (!this.changeWorkingDirectory(dir)) {
				return false
			}
		}
	}
	return true
}

// state
data class FtpState(val host:String = "", val username:String = "", val password:String = "", val root:String = "")
enum class AppState { NotChecked, Checking, Activated, Inactivated }
