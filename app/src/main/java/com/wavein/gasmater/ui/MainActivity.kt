package com.wavein.gasmater.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.ActivityMainBinding
import com.wavein.gasmater.tools.AppManager
import com.wavein.gasmater.tools.LanguageUtil
import com.wavein.gasmater.tools.NetworkInfo
import com.wavein.gasmater.tools.SharedEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

	private lateinit var appBarConfiguration:AppBarConfiguration

	// binding & viewModel
	private lateinit var binding:ActivityMainBinding

	// 切換語言
	override fun attachBaseContext(newBase:Context?) {
		val context = newBase?.let {
			LanguageUtil.wrap(newBase, LanguageUtil.getLocale(newBase))
		}
		super.attachBaseContext(context)
	}

	override fun onCreate(savedInstanceState:Bundle?) {
		super.onCreate(savedInstanceState)

		// 初始化APP
		AppManager.initAll(application)

		// ui
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// 上方bar
		setSupportActionBar(binding.toolbar)

		// 導航處理
		val navController = findNavController(R.id.nav_host_fragment_activity_main)
		val appBarConfiguration = AppBarConfiguration(
			setOf(
				R.id.nav_logoFragment,
				R.id.nav_settingFragment,
				R.id.nav_meterBaseFragment,

				R.id.nav_csvFragment,
				R.id.nav_testFragment,
			)
		)
		setupActionBarWithNavController(navController, appBarConfiguration)
		binding.navView.setupWithNavController(navController)


		// 訂閱斷線處理
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				NetworkInfo.state.collectLatest { state ->
					when (state) {
						NetworkInfo.NetworkState.Available -> {}
						NetworkInfo.NetworkState.Connecting -> {
							SharedEvent._eventFlow.emit(
								SharedEvent.ShowSnackbar(
									"網路已連線", SharedEvent.SnackbarColor.Success, Snackbar.LENGTH_SHORT
								)
							)
							NetworkInfo._state.value = NetworkInfo.NetworkState.Available
						}

						NetworkInfo.NetworkState.Lost -> {
							SharedEvent._eventFlow.emit(
								SharedEvent.ShowSnackbar(
									"網路已斷線", SharedEvent.SnackbarColor.Error, Snackbar.LENGTH_INDEFINITE
								)
							)
						}
					}
				}
			}
		}

		// 訂閱ui事件
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				SharedEvent.eventFlow.collectLatest { event ->
					when (event) {
						// 小吃
						is SharedEvent.ShowSnackbar -> {
							val snackbar = when (event.color) {
								SharedEvent.SnackbarColor.Normal -> {
									Snackbar.make(binding.root, event.message, event.duration)
								}

								SharedEvent.SnackbarColor.Error -> {
									Snackbar.make(binding.root, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#FFDAD6"))
										.setTextColor(Color.parseColor("#BA1A1A"))
								}

								SharedEvent.SnackbarColor.Success -> {
									Snackbar.make(binding.root, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#D0FF9A"))
										.setTextColor(Color.parseColor("#004705"))
								}

								SharedEvent.SnackbarColor.Info -> {
									Snackbar.make(binding.root, event.message, event.duration)
										.setBackgroundTint(Color.parseColor("#DEE0FF"))
										.setTextColor(Color.parseColor("#4456B6"))
								}
							}
							snackbar.show()
						}
						// 訊息對話框
						is SharedEvent.ShowDialog -> {
							val builder = MaterialAlertDialogBuilder(this@MainActivity, R.style.MyAlertDialogTheme)
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
							val alertDialog = builder.create()
							alertDialog.setOnShowListener {
								val btnPositive:Button = alertDialog.getButton(Dialog.BUTTON_POSITIVE)
								btnPositive.textSize = 24f
								val btnNegative:Button = alertDialog.getButton(Dialog.BUTTON_NEGATIVE)
								btnNegative.textSize = 24f
							}
							alertDialog.show()
						}
					}
				}
			}
		}
	}
}