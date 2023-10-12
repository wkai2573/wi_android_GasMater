package com.wavein.gasmeter.ui.meterwork.row

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.data.model.toMeterGroups
import com.wavein.gasmeter.databinding.FragmentMeterInfoBinding
import com.wavein.gasmeter.tools.RD64H
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeterInfoFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentMeterInfoBinding? = null
	private val binding get() = _binding!!
	private val blVM by activityViewModels<BluetoothViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()

	// 實例
	private val meterBaseFragment:MeterBaseFragment get() = ((parentFragment as MeterRowFragment).parentFragment as MeterBaseFragment)

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterInfoBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 訂閱選擇的meterRow
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterRowFlow.asStateFlow().collectLatest {
					it?.let {
						binding.fieldCustId.setValue(it.custId)
						binding.fieldCustName.setValue(it.custName)
						binding.fieldCustAddr.setValue(it.custAddr)
						binding.fieldMeterId.setValue(it.meterId)
						binding.fieldMeterDegree.setValue("${it.meterDegree ?: "未抄表"}", if (it.meterDegree == null) Color.RED else Color.BLACK)
						binding.fieldMeterReadTime.setValue("${it.meterReadTime}")
						binding.fieldAlarm1.setValue(booleanRender(it.batteryVoltageDropAlarm))
						binding.fieldAlarm2.setValue(booleanRender(it.innerPipeLeakageAlarm))
						binding.fieldAlarm3.setValue(booleanRender(it.shutoff))
						binding.fieldShutoffTime.setValue("${it.shutoffTime}")
					}
				}
			}
		}

		// 個別抄表按鈕
		binding.meterDegreeReadBtn.setOnClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("個別抄表")
				.setMessage("是否進行個別抄表")
				.setNegativeButton("取消") { dialog, which ->
					dialog.dismiss()
				}
				.setPositiveButton("確定") { dialog, which ->
					dialog.dismiss()
					val meterRow = meterVM.selectedMeterRowFlow.value ?: return@setPositiveButton
					meterBaseFragment.checkBluetoothOn { blVM.sendR80Telegram(listOf(meterRow.meterId)) }
				}
				.show()
		}
	}

	private fun booleanRender(value:Boolean?) = when (value) {
		null -> ""
		true -> "是"
		false -> "否"
	}

	// todo 測試抄表成功
	fun mockRead() {
		val d05m = RD64H.D05mInfo().apply {
			list = mutableListOf(
				RD64H.D05Info(text = "90N100000002306003D05000012889@BB@C@H@             "),
				RD64H.D05Info(text = "90N100000002306004D05000012716@@B@B@I@             "),
			)
		}
		val result:Map<String, Any> = mapOf(
			"D70" to RD64H.D70Info(text = "ZA00000000000000D70"),
			"D05" to d05m.list[0],
			"D05m" to d05m
		)
		val d05mList = (result["D05m"] as RD64H.D05mInfo).list

		val nowMeterGroup = meterVM.selectedMeterGroupStateFlow.value
		val nowMeterRow = meterVM.selectedMeterRowFlow.value

		val newCsvRows = meterVM.meterRowsStateFlow.value.map { meterRow ->
			val d05Info = d05mList.find { it.meterId == meterRow.meterId }
			if (d05Info != null) {
				meterRow.copy(meterDegree = d05Info.meterDegree)
			} else {
				meterRow
			}
		}
		meterVM.meterRowsStateFlow.value = newCsvRows
		meterVM.setSelectedMeterGroup(newCsvRows.toMeterGroups()
			.find { it.group == nowMeterGroup?.group })
		meterVM.selectedMeterRowFlow.value = meterVM.selectedMeterGroupStateFlow.value?.meterRows
			?.find { it.queue == nowMeterRow?.queue }
	}

}