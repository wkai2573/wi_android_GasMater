package com.wavein.gasmeter.ui.logo

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.wavein.gasmeter.R
import com.wavein.gasmeter.databinding.FragmentLogoBinding
import com.wavein.gasmeter.ui.NavViewModel
import com.wavein.gasmeter.ui.bluetooth.CommState
import com.wavein.gasmeter.ui.setting.SettingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class LogoFragment : Fragment() {


	private var _binding:FragmentLogoBinding? = null
	private val binding get() = _binding!!
	private val navVM by activityViewModels<NavViewModel>()
	private val settingVM by activityViewModels<SettingViewModel>()


	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentLogoBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				settingVM.uuidStateFlow.asStateFlow().collectLatest { uuid ->
					val detailText = "UUID: $uuid"
					binding.detailTv.text = detailText
				}
			}
		}


		val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			requireContext().packageManager.getPackageInfo(requireContext().packageName, PackageManager.PackageInfoFlags.of(0))
		} else {
			requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
		}
		val versionText = "ver ${packageInfo.versionName}"
		binding.verTv.text = versionText
	}


	override fun onResume() {
		super.onResume()
		viewLifecycleOwner.lifecycleScope.launch {
			delay(1400)
			navVM.navigate(R.id.nav_settingFragment)
		}
	}
}