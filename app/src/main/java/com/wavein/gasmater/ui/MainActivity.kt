package com.wavein.gasmater.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

	private lateinit var appBarConfiguration:AppBarConfiguration

	// binding & viewModel
	private lateinit var binding:ActivityMainBinding

	override fun onCreate(savedInstanceState:Bundle?) {
		super.onCreate(savedInstanceState)

		// ui
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// 上方bar
		// setSupportActionBar(binding.toolbar)

		// 導航處理
		val navController = findNavController(R.id.nav_host_fragment_content_main)
		appBarConfiguration = AppBarConfiguration(navController.graph)
		// setupActionBarWithNavController(navController, appBarConfiguration)
	}

	override fun onSupportNavigateUp():Boolean {
		val navController = findNavController(R.id.nav_host_fragment_content_main)
		return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
	}
}