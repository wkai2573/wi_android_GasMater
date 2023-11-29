package com.wavein.gasmeter.ui.meterwork.row

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wavein.gasmeter.databinding.FragmentMeterInfoBinding
import com.wavein.gasmeter.databinding.InputLayoutBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.TimeUtil
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.meterwork.MeterBaseFragment
import com.wavein.gasmeter.ui.meterwork.MeterViewModel
import com.wavein.gasmeter.ui.setting.CsvViewModel
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
						binding.fieldMeterId.setValue(it.meterId)
						binding.meterDegreeReadLayout.visibility = if (it.degreeRead) View.GONE else View.VISIBLE
						val meterDegreeText = if (it.meterDegree == null) "未抄表" else
							("${it.meterDegree} ${if (it.isManualMeterDegree == true) " (人工輸入)" else ""}")
						binding.fieldMeterDegree.setValue(meterDegreeText, if (it.meterDegree == null) Color.RED else Color.BLACK)
						binding.fieldLastMeterDegree.setValue(it.lastMeterDegree?.toString() ?: "")
						binding.fieldDegreeUsed.setValue(it.degreeUsed?.toString() ?: "", if (it.degreeNegative) Color.RED else Color.BLACK)
						binding.fieldMeterReadTime.setValue(it.meterReadTime ?: "")
						binding.fieldLastMeterReadTime.setValue(it.lastMeterReadTime ?: "")
						binding.fieldAlarm1.setValue(booleanRender(it.batteryVoltageDropAlarm))
						binding.fieldAlarm2.setValue(booleanRender(it.innerPipeLeakageAlarm))
						binding.fieldAlarm3.setValue(booleanRender(it.shutoff))
						binding.fieldCustId.setValue(it.custId)
						binding.fieldCustName.setValue(it.custName)
						binding.fieldCustAddr.setValue(it.custAddr)
						binding.fieldRemark.setValue(it.remark ?: "")
						binding.electricFieldStrengthTv.text = it.electricFieldStrength ?: ""
						binding.electricFieldStrengthProgressBar.progress = it.electricFieldStrength?.toInt() ?: 0
					}
				}
			}
		}

		// 個別抄表按鈕
		binding.meterDegreeReadBtn.setOnClickListener {
			if (meterVM.selectedMeterRowFlow.value?.degreeRead == true) {
				lifecycleScope.launch {
					SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("此表已經完成抄表", SharedEvent.Color.Info))
				}
				return@setOnClickListener
			}
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("個別抄表")
				.setMessage("準備進行個別抄表\n耗時約45秒") // 1+2+1+37+3.5
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

		// 手動抄表按鈕
		binding.manualMeterDegreeBtn.setOnClickListener {
			val inputLayoutBinding = InputLayoutBinding.inflate(LayoutInflater.from(requireContext()))
			val inputLayout = inputLayoutBinding.textInput.apply {
				hint = "抄表值"
				editText?.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
			}

			MaterialAlertDialogBuilder(requireContext())
				.setTitle("人工輸入抄表值")
				.setView(inputLayout)
				.setNegativeButton("取消") { dialog, which ->
					dialog.dismiss()
				}
				.setPositiveButton("確定", null)
				.create()
				.apply {
					setOnShowListener {
						inputLayout.editText?.requestFocus()
						// 覆寫確定按鈕
						getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
							try {
								val newDegree = inputLayout.editText?.text.toString().toFloat()
								val newMeterRow = meterVM.selectedMeterRowFlow.value?.copy(
									isManualMeterDegree = true,
									meterDegree = newDegree,
									meterReadTime = TimeUtil.getCurrentTime(),
								)!!
								meterBaseFragment.updateCsvRowManual(newMeterRow)
								this.dismiss()
							} catch (e:Exception) {
								inputLayout.error = "格式不符"
							}
						}
					}
					show()
				}
		}

		// 編輯表ID按鈕
		binding.meterIdBtn.setOnClickListener {
			val inputLayoutBinding = InputLayoutBinding.inflate(LayoutInflater.from(requireContext()))
			val inputLayout = inputLayoutBinding.textInput.apply {
				hint = "通信ID"
				editText?.setText(meterVM.selectedMeterRowFlow.value?.meterId ?: "")
			}

			MaterialAlertDialogBuilder(requireContext())
				.setTitle("編輯通信ID")
				.setView(inputLayout)
				.setNegativeButton("取消") { dialog, which -> dialog.dismiss() }
				.setPositiveButton("確定") { dialog, which ->
					dialog.dismiss()
					val newMeterId = inputLayout.editText?.text.toString()
					// 檢查ID重複
					val existedRow = meterVM.meterRowsStateFlow.value.find { it.meterId == newMeterId }
					if (existedRow != null) {
						lifecycleScope.launch {
							SharedEvent.eventFlow.emit(
								SharedEvent.ShowSnackbar(
									"通信ID重複\n此通信ID已存在於群組號: ${existedRow.group}", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE
								)
							)
						}
						return@setPositiveButton
					} else {
						val oldMeterId = meterVM.selectedMeterRowFlow.value?.meterId
						val newMeterRow = meterVM.selectedMeterRowFlow.value?.copy(meterId = newMeterId) ?: return@setPositiveButton
						meterBaseFragment.updateCsvRowManual(newMeterRow, oldMeterId)
					}
				}
				.create()
				.apply {
					setOnShowListener { inputLayout.editText?.requestFocus() }
					show()
				}
		}

		// 編輯備註按鈕
		binding.remarkBtn.setOnClickListener {
			val inputLayoutBinding = InputLayoutBinding.inflate(LayoutInflater.from(requireContext()))
			val inputLayout = inputLayoutBinding.textInput.apply {
				hint = "備註"
				editText?.setText(meterVM.selectedMeterRowFlow.value?.remark ?: "")
			}

			MaterialAlertDialogBuilder(requireContext())
				.setTitle("編輯備註")
				.setView(inputLayout)
				.setNegativeButton("取消") { dialog, which ->
					dialog.dismiss()
				}
				.setPositiveButton("確定") { dialog, which ->
					dialog.dismiss()
					val newMeterRow = meterVM.selectedMeterRowFlow.value?.copy(
						remark = inputLayout.editText?.text.toString()
					) ?: return@setPositiveButton
					meterBaseFragment.updateCsvRowManual(newMeterRow)
				}
				.create()
				.apply {
					setOnShowListener {
						inputLayout.editText?.requestFocus()
					}
					show()
				}
		}
	}

	private fun booleanRender(value:Boolean?) = when (value) {
		null -> ""
		true -> "是"
		false -> "否"
	}

}