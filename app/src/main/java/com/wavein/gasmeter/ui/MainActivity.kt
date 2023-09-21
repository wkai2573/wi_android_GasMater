package com.wavein.gasmeter.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.ActivityMainBinding
import com.wavein.gasmeter.tools.AppManager
import com.wavein.gasmeter.tools.LanguageUtil
import com.wavein.gasmeter.tools.NetworkInfo
import com.wavein.gasmeter.tools.SharedEvent
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

		// 下方navBar
		val navController = findNavController(R.id.nav_host_fragment_activity_main)
		navController.addOnDestinationChangedListener { _, destination, _ ->
			binding.navView.visibility = when (destination.id) {
				R.id.nav_logoFragment, R.id.nav_nccFragment -> View.GONE
				else -> View.VISIBLE
			}
			when (destination.id) {
				R.id.nav_logoFragment -> {}
				else -> setBackPressedDispatcherAppToBack()
			}
		}
		appBarConfiguration = AppBarConfiguration(
			topLevelDestinationIds = setOf(
				// 可用的fragment, bottomNav顯示的項目在menu.xml設定
				R.id.nav_logoFragment,
				R.id.nav_settingFragment,
				R.id.nav_meterBaseFragment,
				R.id.nav_testFragment,
				R.id.nav_nccFragment,
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
								SharedEvent.ShowSnackbar("網路已連線", SharedEvent.SnackbarColor.Success, Snackbar.LENGTH_SHORT)
							)
							NetworkInfo._state.value = NetworkInfo.NetworkState.Available
						}

						NetworkInfo.NetworkState.Lost -> {
							SharedEvent._eventFlow.emit(
								SharedEvent.ShowSnackbar("網路已斷線", SharedEvent.SnackbarColor.Error, Snackbar.LENGTH_SHORT)
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
							displaySnackBarWithBottomMargin(snackbar, marginBottom = 200)
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

	// 小吃margin, https://stackoverflow.com/questions/36588881/snackbar-behind-navigation-bar
	private fun displaySnackBarWithBottomMargin(snackbar:Snackbar, sideMargin:Int = 0, marginBottom:Int = 0) {
		val snackBarView = snackbar.view
		val params = snackBarView.layoutParams as CoordinatorLayout.LayoutParams
		params.setMargins(params.leftMargin + sideMargin, params.topMargin, params.rightMargin + sideMargin, params.bottomMargin + marginBottom)
		snackBarView.layoutParams = params
		snackbar.show()
	}

	// 按back縮小app
	private fun setBackPressedDispatcherAppToBack() {
		val callback = object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				moveTaskToBack(true) // 縮小app
				// this.onBackPressed() // 預設的返回操作 (會回logo頁)
			}
		}
		this.onBackPressedDispatcher.addCallback(this, callback)
	}

}