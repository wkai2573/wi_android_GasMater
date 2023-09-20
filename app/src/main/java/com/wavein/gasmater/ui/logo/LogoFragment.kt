package com.wavein.gasmater.ui.logo

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.wavein.gasmater.R
import com.wavein.gasmater.databinding.FragmentLogoBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class LogoFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentLogoBinding? = null
	private val binding get() = _binding!!

	// 防止內存洩漏
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

		// tv: ver
		val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			requireContext().packageManager.getPackageInfo(requireContext().packageName, PackageManager.PackageInfoFlags.of(0))
		} else {
			requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
		}
		val versionText = "ver ${packageInfo.versionName}"
		binding.verTv.text = versionText

		// tv: 產品詳細
		binding.detailTv.text = ""

		// 延遲後跳轉
		viewLifecycleOwner.lifecycleScope.launch {
			delay(1400)
			// findNavController().navigate(R.id.nav_settingFragment)
			findNavController().navigate(R.id.nav_nccFragment)
		}
	}
}