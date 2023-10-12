package com.wavein.gasmeter.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.ActivityMainBinding
import com.wavein.gasmeter.databinding.DialogLoadingBinding
import com.wavein.gasmeter.tools.AppManager
import com.wavein.gasmeter.tools.LanguageUtil
import com.wavein.gasmeter.tools.NetworkInfo
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.allowInfiniteLines
import com.wavein.gasmeter.ui.ftp.FtpViewModel
import com.wavein.gasmeter.ui.loading.LoadingDialogFragment
import com.wavein.gasmeter.ui.setting.CsvViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

	private lateinit var appBarConfiguration:AppBarConfiguration

	// binding & viewModel
	private lateinit var binding:ActivityMainBinding
	private val ftpVM by viewModels<FtpViewModel>()
	private val csvVM by viewModels<CsvViewModel>()

	// 變數
	private var showSystemAreaClickCountdown = 5
	private var loadingDialog:LoadingDialogFragment? = null

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
		val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
		val navController = navHostFragment.navController
		navController.addOnDestinationChangedListener { _, destination, _ ->
			// 隱藏顯示 navBar
			binding.navView.visibility = when (destination.id) {
				R.id.nav_logoFragment, R.id.nav_nccFragment -> View.GONE
				else -> View.VISIBLE
			}
			when (destination.id) {
				R.id.nav_logoFragment -> {}
				else -> setBackPressedDispatcherAppToBack()
			}
			// 連點5次設定 顯示系統設定
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
		}
		appBarConfiguration = AppBarConfiguration(
			topLevelDestinationIds = setOf(
				// 可用的fragment, bottomNav顯示的項目在menu.xml設定
				R.id.nav_logoFragment,
				R.id.nav_settingFragment,
				R.id.nav_meterBaseFragment,
				// R.id.nav_testFragment,
				R.id.nav_nccFragment,
			)
		)
		setupActionBarWithNavController(navController, appBarConfiguration)
		binding.navView.setupWithNavController(navController)

		// 訂閱斷線處理
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

		// 訂閱ui事件
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				SharedEvent.eventFlow.asSharedFlow().collectLatest { event ->
					when (event) {
						// 小吃
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
						// 訊息對話框
						is SharedEvent.ShowDialog -> {
							val builder = MaterialAlertDialogBuilder(this@MainActivity/*, R.style.MyAlertDialogTheme*/)
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
						// 訊息對話框 by builder
						is SharedEvent.ShowDialogB -> {
							event.builder.show()
						}

						else -> {}
					}
				}
			}
		}

		// 訂閱全域loading
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				SharedEvent.loadingFlow.asStateFlow().collectLatest { message ->
					if (message.isNotEmpty()) {
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