package com.wavein.gasmater.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.FragmentMainBinding
import com.wavein.gasmater.tools.LanguageUtil

class MainFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMainBinding? = null
	private val binding get() = _binding!!
	private val mainVM by activityViewModels<MainViewModel>()

	// 權限
	private val permissions = arrayOf(
		Manifest.permission.BLUETOOTH_CONNECT,
		Manifest.permission.BLUETOOTH_SCAN,
		Manifest.permission.ACCESS_FINE_LOCATION
	)

	private val requestPermissionLauncher:ActivityResultLauncher<Array<String>> by lazy {
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
			if (permissionsMap.all { (permission, isGranted) -> isGranted }) {
				onPermissionsAllow()
			} else {
				onPermissionsNoAllow()
			}
		}
	}

	// 防止內存洩漏
	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMainBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// 檢查權限，沒權限則請求權限
		if (hasPermissions()) {
			onPermissionsAllow()
		} else {
			binding.requestPermissionBtn.setOnClickListener {
				requestPermissionLauncher.launch(permissions)
			}
			requestPermissionLauncher.launch(permissions)
		}
	}

	// 當權限皆允許
	private fun onPermissionsAllow() {
		binding.revokedPermissionLayout.visibility = View.GONE

		//ui
		binding.btn1.setOnClickListener {
			findNavController().navigate(R.id.action_mainFragment_to_bleDeviceFragment)
		}
		binding.btn2.setOnClickListener {
			findNavController().navigate(R.id.action_mainFragment_to_csvFragment)
		}
		binding.twFlag.setOnClickListener {
			changeLanguage("zh-rTW")
		}
		binding.jpFlag.setOnClickListener {
			changeLanguage("ja")
		}
		binding.usFlag.setOnClickListener {
			changeLanguage("en")
		}
		binding.btn6.setOnClickListener {
			findNavController().navigate(R.id.action_mainFragment_to_testFragment)
		}
	}

	// 切換語言
	private fun changeLanguage(langText:String) {
		val lang = LanguageUtil.getLocale(requireContext())
		if (lang.language == langText) return
		val newLang = langText
		LanguageUtil.setLanguage(requireContext(), newLang)
		requireActivity().recreate()
	}

	//region __________權限方法__________

	// 當權限不允許
	private fun onPermissionsNoAllow() {
		val revokedPermissions:List<String> =
			getPermissionsMap().filterValues { isGranted -> !isGranted }.map { (permission, isGranted) -> permission }
		val revokedPermissionsText = """
		缺少權限: ${
			revokedPermissions.map { p -> p.replace(".+\\.".toRegex(), "") }.joinToString(", ")
		}
		請授予這些權限，以便應用程序正常運行。
		Please grant all of them for the app to function properly.
		""".trimIndent()
		binding.revokedPermissionTv.text = revokedPermissionsText
	}

	// 是否有全部權限
	private fun hasPermissions(context:Context = requireContext(), permissions:Array<String> = this.permissions):Boolean =
		getPermissionsMap(context, permissions).all { (permission, isGranted) -> isGranted }

	// 取得權限狀態
	private fun getPermissionsMap(
		context:Context = requireContext(),
		permissions:Array<String> = this.permissions,
	):Map<String, Boolean> = permissions.associateWith {
		ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}

	//endregion
}