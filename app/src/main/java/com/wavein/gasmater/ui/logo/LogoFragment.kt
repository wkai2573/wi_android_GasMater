package com.wavein.gasmater.ui.logo

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
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

		viewLifecycleOwner.lifecycleScope.launch {
			delay(500)
			findNavController().navigate(R.id.nav_settingFragment)
		}
	}
}