package com.wavein.gasmeter.tools

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract.Directory
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object FileUtils {

	// 取得檔名 by uri, 必須是"文檔樹URI"才會成功取得
	@SuppressLint("Range")
	fun getFilename(context:Context, uri:Uri):String? {
		val cursor:Cursor? = context.contentResolver.query(uri, null, null, null, null)
		cursor?.use {
			if (it.moveToFirst()) {
				val filename = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
				it.close()
				return filename
			}
		}
		return null
	}

	// 讀取檔案內容
	fun readFileContent(
		context:Context,
		uri:Uri,
		maxSizeBytes:Long? = null,
		validExtensions:List<String>? = null,
	):String {
		if (validExtensions != null) {
			val filename = getFilename(context, uri) ?: throw Exception("檔名錯誤")
			if (validExtensions.all { !filename.endsWith(it) }) {
				throw Exception("僅能選擇 ${validExtensions.joinToString()} 檔案")
			}
		}

		return context.contentResolver.openInputStream(uri).use { inputStream ->
			if (maxSizeBytes != null) {
				val fileSize = inputStream?.available()?.toLong()
				if (fileSize == null || fileSize > maxSizeBytes) {
					throw Exception("檔案過大")
				}
			}

			val stringBuilder = StringBuilder()
			val bufferedReader = BufferedReader(InputStreamReader(inputStream))
			var line:String?
			while (bufferedReader.readLine().also { line = it } != null) {
				stringBuilder.append(line)
			}

			val fileContent = stringBuilder.toString().let {
				// 移除BOM
				if (it.startsWith("\uFEFF"))
					it.substring(1)
				else
					it
			}

			fileContent
		}
	}
}