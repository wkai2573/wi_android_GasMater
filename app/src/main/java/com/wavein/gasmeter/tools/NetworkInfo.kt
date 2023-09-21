package com.wavein.gasmeter.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


// 網路物件
object NetworkInfo {

	// 網路狀態 & rssi
	val _state = MutableStateFlow(NetworkState.Available)
	val state:StateFlow<NetworkState> = _state
	var savedRssi:Int? = null

	enum class NetworkState { Available, Connecting, Lost }

	// 註冊偵測網路改變
	fun initDetectionNetwork(context:Context) {
		//初始目前網路連線, 沒連線則執行未連線fun
		val isAvailable = isConnected(context)
		_state.value = if (isAvailable) NetworkState.Available else NetworkState.Lost
		//偵測網路改變
		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network:Network) {
				if (state.value == NetworkState.Lost) {
					_state.value = NetworkState.Connecting
				}
			}

			override fun onLost(network:Network) {
				_state.value = NetworkState.Lost
			}

			@RequiresApi(Build.VERSION_CODES.Q)
			override fun onCapabilitiesChanged(network:Network, networkCapabilities:NetworkCapabilities) {
				val wifiInfo = networkCapabilities.transportInfo as WifiInfo?
				savedRssi = wifiInfo?.rssi
			}
		})
	}

	// 是否連接網路
	private fun isConnected(context:Context):Boolean {
		// register activity with the connectivity manager service
		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		// Returns a Network object corresponding to the currently active default data network.
		val network = connectivityManager.activeNetwork ?: return false
		// Representation of the capabilities of an active network.
		val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
		return when {
			// Indicates this network uses a Wi-Fi transport, or WiFi has network connectivity
			activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
			// Indicates this network uses a Cellular transport. or Cellular has network connectivity
			activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
			else -> false
		}
	}

	// 取得網路強度rssi
	fun getRssi(context:Context):Int {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedRssi != null) {
			return savedRssi!!
		}
		val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
		val wifiInfo = wifiManager.connectionInfo
		return wifiInfo.rssi
	}
}