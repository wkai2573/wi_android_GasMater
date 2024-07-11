package com.wavein.gasmeter.ui.meterwork

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.wavein.gasmeter.R
import com.wavein.gasmeter.data.model.MeterRow
import com.wavein.gasmeter.databinding.FragmentMeterBaseBinding
import com.wavein.gasmeter.tools.SharedEvent
import com.wavein.gasmeter.tools.TimeUtils
import com.wavein.gasmeter.tools.rd64h.info.D05mInfo
import com.wavein.gasmeter.tools.rd64h.info.D87D02Info
import com.wavein.gasmeter.tools.rd64h.info.D87D16Info
import com.wavein.gasmeter.tools.rd64h.info.D87D23Info
import com.wavein.gasmeter.tools.rd64h.info.D87D24Info
import com.wavein.gasmeter.tools.rd64h.info.D87D31Info
import com.wavein.gasmeter.tools.rd64h.info.D87D41Info
import com.wavein.gasmeter.tools.rd64h.info.D87D42Info
import com.wavein.gasmeter.tools.rd64h.info.D87D50Info
import com.wavein.gasmeter.tools.rd64h.info.D87D51Info
import com.wavein.gasmeter.tools.rd64h.info.D87D57Info
import com.wavein.gasmeter.tools.rd64h.info.D87D58Info
import com.wavein.gasmeter.tools.rd64h.info.D87D59Info
import com.wavein.gasmeter.tools.rd64h.info.MetaInfo
import com.wavein.gasmeter.ui.NavViewModel
import com.wavein.gasmeter.ui.bluetooth.BluetoothViewModel
import com.wavein.gasmeter.ui.bluetooth.BtDialogFragment
import com.wavein.gasmeter.ui.bluetooth.CommEndEvent
import com.wavein.gasmeter.ui.bluetooth.CommState
import com.wavein.gasmeter.ui.bluetooth.ConnectEvent
import com.wavein.gasmeter.ui.ftp.FtpViewModel
import com.wavein.gasmeter.ui.loading.Tip
import com.wavein.gasmeter.ui.meterwork.groups.MeterGroupsFragment
import com.wavein.gasmeter.ui.meterwork.list.MeterListFragment
import com.wavein.gasmeter.ui.meterwork.row.MeterRowFragment
import com.wavein.gasmeter.ui.setting.CsvViewModel
import com.wavein.gasmeter.ui.setting.SettingViewModel
import com.wavein.gasmeter.ui.setting.SettingViewModel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeterBaseFragment : Fragment() {


	private var _binding:FragmentMeterBaseBinding? = null
	private val binding get() = _binding!!
	private val navVM by activityViewModels<NavViewModel>()
	private val blVM by activityViewModels<BluetoothViewModel>()
	private val meterVM by activityViewModels<MeterViewModel>()
	private val csvVM by activityViewModels<CsvViewModel>()
	private val settingVM by activityViewModels<SettingViewModel>()
	private val ftpVM by activityViewModels<FtpViewModel>()

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onCreateView(inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?):View {
		_binding = FragmentMeterBaseBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view:View, savedInstanceState:Bundle?) {
		super.onViewCreated(view, savedInstanceState)


		binding.noCsvTipTv.visibility = if (meterVM.meterRowsStateFlow.value.isEmpty()) View.VISIBLE else View.GONE


		val meterPageAdapter = MeterPageAdapter(this)
		binding.pager.adapter = meterPageAdapter
		binding.pager.isUserInputEnabled = false


		TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
			when (position) {
				0 -> tab.text = "群組列表"
				1 -> tab.text = "群組瓦斯表"
				2 -> tab.text = "瓦斯表"
				else -> tab.text = "UNKNOWN: $position"
			}
		}.attach()


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterGroupStateFlow.collectLatest { meterGroup ->
					if (meterGroup == null) changeTab(0)
					binding.tabLayout.getTabAt(1)?.let { tab ->
						setTabView(meterGroup, tab)
					}
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				meterVM.selectedMeterRowFlow.asStateFlow().collectLatest { meterRow ->
					binding.tabLayout.getTabAt(2)?.let { tab ->
						setTabView(meterRow, tab)
					}
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				navVM.meterBaseChangeTabStateFlow.asStateFlow().collectLatest { tabIndex ->
					if (tabIndex == -1) return@collectLatest
					delay(1)
					changeTab(tabIndex, false)
					navVM.meterBaseChangeTabStateFlow.value = -1
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				navVM.meterBaseBackKeyClickSharedFlow.asSharedFlow().collectLatest { smoothScroll ->
					if (binding.pager.currentItem == 2 && navVM.meterRowPageBackDestinationIsSearch) {
						navVM.navigate(R.id.nav_meterSearchFragment)
					} else {
						val toTabIndex = binding.pager.currentItem - 1
						if (toTabIndex >= 0) {
							changeTab(toTabIndex, smoothScroll)
						} else {
							navVM.navigate(R.id.nav_settingFragment)
						}
					}
				}
			}
		}


		initConnSubscription()
	}


	private fun setTabView(it:Any?, tab:TabLayout.Tab) {
		if (it == null) {
			tab.view.isEnabled = false
			tab.customView = TextView(requireContext()).apply {
				gravity = Gravity.CENTER
				setTextColor(Color.LTGRAY)
				text = tab.text
			}
		} else {
			tab.view.isEnabled = true
			tab.customView = null
		}
	}


	fun changeTab(tabIndex:Int, smoothScroll:Boolean = true) {
		binding.pager.setCurrentItem(tabIndex, smoothScroll)
	}




	private fun initConnSubscription() {


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commTextStateFlow.asStateFlow().collectLatest {
					Log.i("@@@通信狀態", it.title)
					SharedEvent.loadingFlow.value = when (it.title) {
						"未連結設備", "通信完畢" -> Tip()
						"設備已連結" -> Tip("設備已連結，準備通信")
						else -> it.copy()
					}
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.connectEventFlow.asSharedFlow().collectLatest { event ->
					when (event) {
						ConnectEvent.Connecting -> {}
						ConnectEvent.Connected -> {}
						ConnectEvent.ConnectionFailed -> {
							SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar("設備連結失敗", SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
						}

						ConnectEvent.Listening -> {}
						ConnectEvent.ConnectionLost -> {}

						is ConnectEvent.BytesSent -> {}
						is ConnectEvent.BytesReceived -> {}
						else -> {}
					}
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commStateFlow.asStateFlow().collectLatest { state ->
					when (state) {
						CommState.NotConnected -> {}
						CommState.Communicating, CommState.Connecting -> {}
						CommState.ReadyCommunicate -> {
							onConnected?.invoke()
							onConnected = null
						}

						else -> {}
					}
				}
			}
		}


		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				blVM.commEndSharedEvent.asSharedFlow().collectLatest { event ->
					SharedEvent.catching {
						when (event) {
							is CommEndEvent.Success -> {
								val message = if (event.commResult.containsKey("success")) {
									event.commResult["success"].toString()
								} else {
									"通信成功"
								}
								SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(message, SharedEvent.Color.Success, Snackbar.LENGTH_INDEFINITE))
								SharedEvent.eventFlow.emit(SharedEvent.PlayEffect())
								updateCsvRowsByCommResult(event.commResult)
							}

							is CommEndEvent.Error -> {
								val message = if (event.commResult.containsKey("error")) {
									event.commResult["error"].toString()
								} else {
									event.commResult.toString()
								}
								SharedEvent.eventFlow.emit(SharedEvent.ShowSnackbar(message, SharedEvent.Color.Error, Snackbar.LENGTH_INDEFINITE))
								SharedEvent.eventFlow.emit(SharedEvent.PlayEffect())
								updateCsvRowsByCommResult(event.commResult)
							}

							else -> {}
						}
					}
				}
			}
		}
	}


	private var onBluetoothOn:(() -> Unit)? = null
	private var onConnected:(() -> Unit)? = null
	private var onConnectionFailed:(() -> Unit)? = null


	private val bluetoothRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			this.onBluetoothOn?.invoke()
			this.onBluetoothOn = null
		}
		this.onBluetoothOn = null
	}


	fun checkBluetoothOn(onConnected:() -> Unit) {
		this.onBluetoothOn = { checkReadyCommunicate(onConnected) }
		if (!blVM.isBluetoothOn()) {
			blVM.checkBluetoothOn(bluetoothRequestLauncher)
		} else {
			onBluetoothOn?.invoke()
			onBluetoothOn = null
		}
	}


	private fun checkReadyCommunicate(onConnected:() -> Unit) {
		when (blVM.commStateFlow.value) {
			CommState.NotConnected -> autoConnectDevice(onConnected)
			CommState.ReadyCommunicate -> onConnected.invoke()
			CommState.Connecting -> {}
			CommState.Communicating -> {}
			else -> {}
		}
	}


	private fun autoConnectDevice(onConnected:() -> Unit) {
		if (blVM.autoConnectDeviceStateFlow.value != null) {
			this.onConnectionFailed = { BtDialogFragment.open(requireContext()) }
			this.onConnected = onConnected
			blVM.connectDevice()
		} else {
			this.onConnected = onConnected
			BtDialogFragment.open(requireContext())
		}
	}


	private suspend fun updateCsvRowsByCommResult(commResult:Map<String, Any>) {
		SharedEvent.catching {
			Log.i("@@@通信結果 ", commResult.toString())
			val metaInfo = commResult["meta"] as MetaInfo

			var newCsvRows = meterVM.meterRowsStateFlow.value
			when (metaInfo.op) {
				"R80" -> {

					if (commResult.containsKey("D05m")) {
						val d05mList = (commResult["D05m"] as D05mInfo).list
						newCsvRows = newCsvRows.map { meterRow ->
							val d05Info = d05mList.find { it.meterId == meterRow.meterId } ?: return@map meterRow
							val batteryVoltageDropAlarm = try {
								d05Info.alarmInfoDetail["A8"]!!["b4"]!! ||
										d05Info.alarmInfoDetail["A4"]!!["b4"]!! ||
										d05Info.alarmInfoDetail["A4"]!!["b3"]!!
							} catch (e:Exception) {
								null
							}
							meterRow.copy(
								isManualMeterDegree = false,
								meterDegree = d05Info.meterDegree,
								meterReadTime = TimeUtils.getCurrentTime(),
								alarmInfo1 = d05Info.alarmInfo1,
								batteryVoltageDropAlarm = batteryVoltageDropAlarm,
								innerPipeLeakageAlarm = d05Info.alarmInfoDetail["A5"]?.get("b1"),
								shutoff = d05Info.alarmInfoDetail["A1"]?.get("b1")?.not(),
								electricFieldStrength = d05Info.electricFieldStrength,
							)
						}
					}
				}

				"R87" -> {
					val logRows = mutableListOf<LogRow>()
					val meterId = metaInfo.meterIds[0]
					newCsvRows = newCsvRows.map { meterRow ->
						var newMeterRow = meterRow
						if (meterRow.meterId != meterId) return@map newMeterRow

						if (commResult.containsKey("D87D23")) {
							val info = commResult["D87D23"] as D87D23Info
							newMeterRow = newMeterRow.copy(
								shutdownHistory1 = info.shutdownHistory1,
								shutdownHistory2 = info.shutdownHistory2,
								shutdownHistory3 = info.shutdownHistory3,
								shutdownHistory4 = info.shutdownHistory4,
								shutdownHistory5 = info.shutdownHistory5,
							)
						}

						if (commResult.containsKey("D87D24")) {
							val info = commResult["D87D24"] as D87D24Info
							newMeterRow = newMeterRow.copy(
								alarmInfo1 = info.alarmInfo1,
								alarmInfo2 = info.alarmInfo2,
								registerFuseFlowRate1 = info.registerFuseFlowRate1,
								registerFuseFlowRate2 = info.registerFuseFlowRate2,
							)
						}

						if (commResult.containsKey("D87D16")) {
							val info = commResult["D87D16"] as D87D16Info
							newMeterRow = newMeterRow.copy(meterStatus = info.meterStatus)
							if (metaInfo.r87Steps?.any { it.op == "S16" } == true) {
								logRows.add(
									LogRow(meterId = meterId, op = "S16", oldValue = meterRow.meterStatus ?: "未查詢", newValue = newMeterRow.meterStatus ?: "")
								)
							}
						}

						if (commResult.containsKey("D87D57")) {
							val info = commResult["D87D57"] as D87D57Info
							newMeterRow = newMeterRow.copy(hourlyUsage = info.hourlyUsage)
						}

						if (commResult.containsKey("D87D58")) {
							val info = commResult["D87D58"] as D87D58Info
							newMeterRow = newMeterRow.copy(maximumUsage = info.maximumUsage, maximumUsageTime = info.maximumUsageTime)
						}

						if (commResult.containsKey("D87D59")) {
							val info = commResult["D87D59"] as D87D59Info
							newMeterRow = newMeterRow.copy(oneDayMaximumUsage = info.oneDayMaximumUsage, oneDayMaximumUsageDate = info.oneDayMaximumUsageDate)
						}

						if (commResult.containsKey("D87D31")) {
							val info = commResult["D87D31"] as D87D31Info
							newMeterRow = newMeterRow.copy(registerFuseFlowRate1 = info.registerFuseFlowRate1, registerFuseFlowRate2 = info.registerFuseFlowRate2)
							if (metaInfo.r87Steps?.any { it.op == "S31" } == true) {
								logRows.add(
									LogRow(
										meterId = meterId, op = "S31", oldValue = meterRow.registerFuseFlowRate1 ?: "未查詢",
										newValue = newMeterRow.registerFuseFlowRate1 ?: ""
									)
								)
								logRows.add(
									LogRow(
										meterId = meterId, op = "S31", oldValue = meterRow.registerFuseFlowRate2 ?: "未查詢",
										newValue = newMeterRow.registerFuseFlowRate2 ?: ""
									)
								)
							}
						}

						if (commResult.containsKey("D87D50")) {
							val info = commResult["D87D50"] as D87D50Info
							newMeterRow = newMeterRow.copy(pressureShutOffJudgmentValue = info.pressureShutOffJudgmentValue)
							if (metaInfo.r87Steps?.any { it.op == "S50" } == true) {
								logRows.add(
									LogRow(
										meterId = meterId, op = "S50", oldValue = meterRow.pressureShutOffJudgmentValue ?: "未查詢",
										newValue = newMeterRow.pressureShutOffJudgmentValue ?: ""
									)
								)
							}
						}

						if (commResult.containsKey("D87D51")) {
							val info = commResult["D87D51"] as D87D51Info
							newMeterRow = newMeterRow.copy(pressureValue = info.pressureValue)
						}

						if (commResult.containsKey("D87D41")) {
							val info = commResult["D87D41"] as D87D41Info
							newMeterRow = newMeterRow.copy(alarmInfo1 = info.alarmInfo1)
							if (metaInfo.r87Steps?.any { it.op == "C41" } == true) {
								logRows.add(
									LogRow(meterId = meterId, op = "C41", oldValue = meterRow.alarmInfo1 ?: "未查詢", newValue = newMeterRow.alarmInfo1 ?: "")
								)
							}
						}

						if (commResult.containsKey("D87D42")) {
							val info = commResult["D87D42"] as D87D42Info
							newMeterRow = newMeterRow.copy(alarmInfo1 = info.alarmInfo1)
							if (metaInfo.r87Steps?.any { it.op == "C42" } == true) {
								logRows.add(
									LogRow(meterId = meterId, op = "C42", oldValue = meterRow.alarmInfo1 ?: "未查詢", newValue = newMeterRow.alarmInfo1 ?: "")
								)
							}
						}

						if (commResult.containsKey("D87D02")) {
							val info = commResult["D87D02"] as D87D02Info
							if (metaInfo.r87Steps?.any { it.op == "C02" } == true) {
								logRows.add(LogRow(meterId = meterId, op = "C02", oldValue = "", newValue = ""))
							}
						}


						newMeterRow
					}


					if (logRows.isNotEmpty()) {
						settingVM.createLogFile(logRows)
					}
					ftpVM.uploadLog()
				}
			}


			csvVM.updateSaveCsv(newCsvRows, meterVM)
		}
	}


	fun updateCsvRowManual(newMeterRow:MeterRow, _origMeterId:String? = null) {
		val origMeterId = _origMeterId ?: newMeterRow.meterId
		val newCsvRows = meterVM.meterRowsStateFlow.value.map { meterRow ->
			if (meterRow.group == newMeterRow.group && meterRow.meterId == origMeterId) {
				newMeterRow
			} else {
				meterRow
			}
		}
		csvVM.updateSaveCsv(newCsvRows, meterVM)
	}

}



class MeterPageAdapter(fragment:Fragment) : FragmentStateAdapter(fragment) {

	override fun getItemCount():Int = 3

	override fun createFragment(position:Int):Fragment {
		val fragment = when (position) {
			0 -> MeterGroupsFragment()
			1 -> MeterListFragment()
			2 -> MeterRowFragment()
			else -> MeterGroupsFragment()
		}
		return fragment
	}
}
