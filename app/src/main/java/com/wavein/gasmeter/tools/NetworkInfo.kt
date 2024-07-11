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



object NetworkInfo {


	val networkStateFlow = MutableStateFlow(NetworkState.Available)
	var savedRssi:Int? = null

	enum class NetworkState { Available, Connecting, Lost }


	fun initDetectionNetwork(context:Context) {

		val isAvailable = isConnected(context)
		networkStateFlow.value = if (isAvailable) NetworkState.Available else NetworkState.Lost

		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network:Network) {
				if (networkStateFlow.value == NetworkState.Lost) {
					networkStateFlow.value = NetworkState.Connecting
				}
			}

			override fun onLost(network:Network) {
				networkStateFlow.value = NetworkState.Lost
			}

			@RequiresApi(Build.VERSION_CODES.Q)
			override fun onCapabilitiesChanged(network:Network, networkCapabilities:NetworkCapabilities) {
				val wifiInfo = networkCapabilities.transportInfo as WifiInfo?
				savedRssi = wifiInfo?.rssi
			}
		})
	}


	private fun isConnected(context:Context):Boolean {

		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

		val network = connectivityManager.activeNetwork ?: return false

		val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
		return when {

			activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

			activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
			else -> false
		}
	}


	fun getRssi(context:Context):Int {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedRssi != null) {
			return savedRssi!!
		}
		val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
		val wifiInfo = wifiManager.connectionInfo
		return wifiInfo.rssi
	}
}