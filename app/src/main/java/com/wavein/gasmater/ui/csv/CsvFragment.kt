package com.wavein.gasmater.ui.csv

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.wavein.gasmater.databinding.FragmentCsvBinding
import com.wavein.gasmater.ui.main.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.FileInputStream

class CsvFragment : Fragment() {

	// binding & viewModel
	private var _binding:FragmentCsvBinding? = null
	private val binding get() = _binding!!
	private val csvVM by activityViewModels<CsvViewModel>()
	private val mainVM by activityViewModels<MainViewModel>()

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentCsvBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// ui
		binding.readCsvBtn.setOnClickListener {
			csvVM.selectReadCsv(filePickerLauncher)
		}
		binding.writeCsvBtn.setOnClickListener {
			//...
		}

		// 訂閱readFileState
		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				csvVM.readFileStateFlow.collectLatest { readFileState ->
					when(readFileState.type) {
						ReadFileState.Type.Idle -> {

						}
						ReadFileState.Type.Reading -> {

						}
						ReadFileState.Type.ReadFailed -> {
							Toast.makeText(requireActivity(), readFileState.message, Toast.LENGTH_SHORT).show()
						}
					}
				}
			}
		}
	}

	// 當選擇檔案
	private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		csvVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.Reading)
		if (result.resultCode == Activity.RESULT_OK) {
			kotlin.runCatching {
				val uri = result.data?.data ?: return@registerForActivityResult
				val fileDescriptor = requireContext().contentResolver.openFileDescriptor(uri, "r") ?: return@registerForActivityResult
				val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
				val rows:List<Map<String, String>> = csvReader().readAllWithHeader(inputStream)
				csvVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.Idle)
				csvVM.rowsStateFlow.value = rows
			}.onFailure {
				csvVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, it.message)
			}
		} else {
			csvVM.readFileStateFlow.value = ReadFileState(ReadFileState.Type.ReadFailed, result.resultCode.toString())
		}
	}

}
