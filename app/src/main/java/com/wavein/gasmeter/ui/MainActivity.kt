package com.wavein.gasmeter.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.ActivityMainBinding
import com.wavein.gasmeter.tools.AppManager
import com.wavein.gasmeter.tools.LanguageUtils
import com.wavein.gasmeter.tools.NetworkInfo
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.VibrationAndSoundUtil
import com.wavein.gasmeter.tools.allowInfiniteLines
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.ftp.FtpViewModel
import com.wavein.gasmeter.ui.loading.LoadingDialogFragment
import com.wavein.gasmeter.ui.setting.CsvViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {


	private lateinit var appBarConfiguration:AppBarConfiguration
	private lateinit var navController:NavController
	private lateinit var vibrationAndSoundUtil:VibrationAndSoundUtil


	private lateinit var binding:ActivityMainBinding
	private val navVM by viewModels<NavViewModel>()
	private val blVM by viewModels<BluetoothViewModel>()
	private val ftpVM by viewModels<FtpViewModel>()
	private val csvVM by viewModels<CsvViewModel>()


	private var showSystemAreaClickCountdown = 5
	private var loadingDialog:LoadingDialogFragment? = null


	override fun attachBaseContext(newBase:Context?) {
		val context = newBase?.let {
			LanguageUtils.wrap(newBase, LanguageUtils.getLocale(newBase))
		}
		super.attachBaseContext(context)
	}

	override fun onCreate(savedInstanceState:Bundle?) {
		super.onCreate(savedInstanceState)


		AppManager.initAll(application)
		vibrationAndSoundUtil = VibrationAndSoundUtil(this)


		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)


		setSupportActionBar(binding.toolbar)


		val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
		navController = navHostFragment.navController
		navController.addOnDestinationChangedListener { _, destination, _ ->

			binding.navView.visibility = when (destination.id) {
				R.id.nav_logoFragment, R.id.nav_nccFragment -> View.GONE
				else -> View.VISIBLE
			}
			when (destination.id) {
				R.id.nav_logoFragment -> {}
				else -> setBackPressedDispatcherAppToBack()
			}

			if (destination.id == R.id.nav_settingFragment) {
				showSystemAreaClickCountdown -= 1
				if (!ftpVM.systemAreaOpenedStateFlow.value && showSystemAreaClickCountdown == 0) {
					lifecycleScope.launch {
						SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("系統設定已顯示"))
						ftpVM.systemAreaOpenedStateFlow.value = true
					}
				}
			} else {
				showSystemAreaClickCountdown = 5
			}

			if (destination.id == R.id.nav_meterBaseFragment || destination.id == R.id.nav_meterSearchFragment) {
				if (blVM.autoConnectDeviceStateFlow.value == null || !csvVM.selectedFileStateFlow.value.isOpened) {
					lifecycleScope.launch {
						SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("請先選擇 設備 & CSV檔案", SharedEvent.Color.Error))
						binding.navView.selectedItemId = R.id.nav_settingFragment
						delay(100)
						binding.navView.selectedItemId = R.id.nav_settingFragment
					}
				}
			}
		}
		appBarConfiguration = AppBarConfiguration(
			topLevelDestinationIds = setOf(

				R.id.nav_logoFragment,
				R.id.nav_settingFragment,
				R.id.nav_meterBaseFragment,
				R.id.nav_meterSearchFragment,
				R.id.nav_nccFragment,
			)
		)
		setupActionBarWithNavController(navController, appBarConfiguration)
		binding.navView.setupWithNavController(navController)


		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				NetworkInfo.networkStateFlow.asStateFlow().collectLatest { state ->
					when (state) {
						NetworkInfo.NetworkState.Available -> {}
						NetworkInfo.NetworkState.Connecting -> {
							SharedEvent.eventFlow.emit(
								SharedEvent.ShowSnackbar("網路已連線", SharedEvent.Color.Success, Snackbar.LENGTH_SHORT)
							)
							NetworkInfo.networkStateFlow.value = NetworkInfo.NetworkState.Available
						}

						NetworkInfo.NetworkState.Lost -> {
							SharedEvent.eventFlow.emit(
								SharedEvent.ShowSnackbar("網路已斷線", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE)
							)
						}
					}
				}
			}
		}


		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				SharedEvent.loadingFlow.asStateFlow().collectLatest {
					if (it.title.isNotEmpty()) {
						if (loadingDialog == null) {
							loadingDialog = LoadingDialogFragment.open(this@MainActivity)
						}
					} else {
						loadingDialog?.dismiss()
						loadingDialog = null
					}
				}
			}
		}


		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				SharedEvent.eventFlow.asSharedFlow().collectLatest { event ->
					when (event) {

						is SharedEvent.ShowSnackbar -> {
							val view = event.view ?: binding.coordinator
							val snackbar = when (event.color) {
								SharedEvent.Color.Normal -> {
									Snackbar.make(view, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#362F2F"))
										.setTextColor(Color.parseColor("#EDE0DF"))
										.setActionTextColor(Color.parseColor("#FFB3B5"))
								}

								SharedEvent.Color.Error -> {
									Snackbar.make(view, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#FFDAD6"))
										.setTextColor(Color.parseColor("#BA1A1A"))
										.setActionTextColor(Color.parseColor("#504EC8"))
								}

								SharedEvent.Color.Success -> {
									Snackbar.make(view, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#D0FF9A"))
										.setTextColor(Color.parseColor("#004705"))
										.setActionTextColor(Color.parseColor("#504EC8"))
								}

								SharedEvent.Color.Info -> {
									Snackbar.make(view, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#DEE0FF"))
										.setTextColor(Color.parseColor("#4456B6"))
										.setActionTextColor(Color.parseColor("#504EC8"))
								}
							}
							if (event.duration == Snackbar.LENGTH_INDEFINITE) {
								snackbar.setAction("close") { snackbar.dismiss() }
							}
							snackbar.anchorView = event.anchorView ?: binding.navView
							snackbar.allowInfiniteLines()
							snackbar.show()
						}

						is SharedEvent.ShowDialog -> {
							val builder = MaterialAlertDialogBuilder(this@MainActivity)
								.setTitle(event.title)
								.setMessage(event.message)
								.setPositiveButton(event.positiveButton.first, event.positiveButton.second)
								.setOnDismissListener(event.onDissmiss)
							event.negativeButton?.let {
								builder.setNegativeButton(it.first, it.second)
							}
							event.neutralButton?.let {
								builder.setNeutralButton(it.first, it.second)
							}
							builder.create().apply {
								setOnShowListener {
									val btnPositive = getButton(Dialog.BUTTON_POSITIVE)
									btnPositive.textSize = 24f
									val btnNegative = getButton(Dialog.BUTTON_NEGATIVE)
									btnNegative.textSize = 24f
								}
								show()
							}
						}

						is SharedEvent.ShowDialogB -> {
							event.alertDialog.show()
						}

						is SharedEvent.PlayEffect -> {
							vibrationAndSoundUtil.vibrateAndPlaySound(event.vibrate, event.sound)
						}
					}
				}
			}
		}


		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				navVM.navigateSharedFlow.collectLatest {
					binding.navView.selectedItemId = it
				}
			}
		}
	}

	override fun onDestroy() {
		vibrationAndSoundUtil.releaseResources()
		super.onDestroy()
	}


	private var backPressedTime:Long = 0
	private val backPressedInterval = 2000
	private fun setBackPressedDispatcherAppToBack() {
		val callback = object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				when (navController.currentDestination?.id) {
					R.id.nav_settingFragment -> {
						if (System.currentTimeMillis() - backPressedTime < backPressedInterval) {
							this@MainActivity.finish()
						} else {
							Toast.makeText(this@MainActivity, "再按一次返回鍵退出", Toast.LENGTH_SHORT).show()
							backPressedTime = System.currentTimeMillis()
						}
					}

					R.id.nav_meterBaseFragment -> {
						navVM.meterBaseOnBackKeyClick(true)
					}

					R.id.nav_meterSearchFragment -> {
						navVM.navigate(R.id.nav_settingFragment)
					}

					R.id.nav_nccFragment -> {
						navVM.navigate(R.id.nav_settingFragment)
					}

					else -> {
						moveTaskToBack(true)

					}
				}
			}
		}
		this.onBackPressedDispatcher.addCallback(this, callback)
	}

}