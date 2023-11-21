package com.wavein.gasmeter.ui.meterwork.row

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.wavein.gasmeter.databinding.FragmentMeterAdvBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel

class MeterAdvFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterAdvBinding? = null
	private val binding get() = _binding!!
	private val csvVM by activityViewModels<CsvViewModel>()
	private val blVM by activityViewModels<BluetoothViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()
	private val advVM:MeterAdvViewModel by viewModels() // 此vm僅保留於此fragment 不全域保留

	override fun onDestroyView() {
		super.onDestroyView()
		SharedEvent.snackbarDefaultAnchorView = null
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterAdvBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// 更新小吃錨點
//		SharedEvent.snackbarDefaultAnchorView = binding.sendFab

		// 傳送按鈕
//		binding.sendFab.setOnClickListener {
//			val meterId = meterVM.selectedMeterRowFlow.value?.meterId ?: return@setOnClickListener
//			blVM.sendR87Telegram(
//				meterId = meterId,
//				r87Steps = advVM.r87StepsStateFlow.value,
//			)
//		}

		// todo 訂閱r87Steps


		// todo checkbox按鈕 ...


	}
}