package com.wavein.gasmater.tools

import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.experimental.xor

private const val TAG = "MY_APP_DEBUG_TAG"

// Defines several constants used when transmitting messages between the
// service and the UI.
const val MESSAGE_READ:Int = 0
const val MESSAGE_WRITE:Int = 1
const val MESSAGE_TOAST:Int = 2
// ... (Add other message types here as needed.)

class MyBluetoothService(
	private val handler:Handler, // handler that gets info from Bluetooth service
) {

	inner class ConnectedThread(private val mmSocket:BluetoothSocket) : Thread() {

		private val mmInStream:InputStream = mmSocket.inputStream
		private val mmOutStream:OutputStream = mmSocket.outputStream
		private val mmBuffer:ByteArray = ByteArray(1024) // mmBuffer store for the stream

		override fun run() {

//			while (true) {
//				val buffer = ByteArray(45)
//				Log.i("@@@", "read start")
//				while (true) {
//					val ret = dataReceive(buffer)
//					Log.i("@@@", "read: $ret | ${buffer.joinToString { it.toString() }}")
//					if (ret == 0) break
//				}
//
//				// Send the obtained bytes to the UI activity.
//				val readMsg = handler.obtainMessage(MESSAGE_READ, -1, -1, buffer)
//				readMsg.sendToTarget()
//			}


			var numBytes:Int // bytes returned from read()

			// Keep listening to the InputStream until an exception occurs.
			while (true) {
				// Read from the InputStream.
				numBytes = try {
					mmInStream.read(mmBuffer)
				} catch (e:IOException) {
					Log.d(TAG, "Input stream was disconnected", e)
					break
				}

				// Send the obtained bytes to the UI activity.
				val readMsg = handler.obtainMessage(MESSAGE_READ, numBytes, -1, mmBuffer)
				readMsg.sendToTarget()
			}
		}

		private fun dataReceive(dest:ByteArray):Int {
			val c = ByteArray(1)
			val bcc:Byte
			var destsz = 0

			while (true) {
				if (_inbyte(c) == 0) {
					Log.i("@@@", "c[0]: ${c[0]}")
					when (c[0]) {
						STX -> destsz = 0
						ETX -> {}
						else -> {}
					}
					dest[destsz] = c[0]
					if (destsz > 1 && dest[destsz - 1] == ETX) {
						bcc = getBcc(dest)
						return if (bcc == dest[destsz]) 0 else -2 //BBC error
					}
					destsz += 1
				} else {
					return -1
				}
			}
		}

		private fun _inbyte(c:ByteArray):Int {
			kotlin.runCatching {
				if (mmInStream.read(c, 0, 1) <= 0) {
					return -1
				}
			}.onFailure {
				return -1
			}
			return 0
		}

		// 電文參數
		private val STX = 0x02.toByte()
		private val ETX = 0x03.toByte()
		private fun getBcc(bytes:ByteArray):Byte { // 取得BCC(傳送bytes的最後驗證碼)
			var bcc:Byte = 0
			for (byte in bytes) bcc = bcc xor byte
			return bcc
		}

		// Call this from the main activity to send data to the remote device.
		fun write(bytes:ByteArray) {
			try {
				mmOutStream.write(bytes)
			} catch (e:IOException) {
				Log.e(TAG, "Error occurred when sending data", e)

				// Send a failure message back to the activity.
				val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
				val bundle = Bundle().apply {
					putString("toast", "Couldn't send data to the other device")
				}
				writeErrorMsg.data = bundle
				handler.sendMessage(writeErrorMsg)
				return
			}

			// Share the sent message with the UI activity.
			val writtenMsg = handler.obtainMessage(MESSAGE_WRITE, -1, -1, bytes)
			writtenMsg.sendToTarget()
		}

		// Call this method from the main activity to shut down the connection.
		fun cancel() {
			try {
				mmSocket.close()
			} catch (e:IOException) {
				Log.e(TAG, "Could not close the connect socket", e)
			}
		}
	}
}