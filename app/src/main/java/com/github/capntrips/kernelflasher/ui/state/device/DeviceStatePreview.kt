package com.github.capntrips.kernelflasher.ui.state.device

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.ui.state.backups.BackupsState
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStatePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceStatePreview constructor(context: Context, _isRefreshing : MutableStateFlow<Boolean>, navController: NavController) : ViewModel(),
    DeviceStateInterface {
    private var _slotA: MutableStateFlow<SlotStateInterface> = MutableStateFlow(SlotStatePreview(_isRefreshing, false))
    private var _slotB: MutableStateFlow<SlotStateInterface> = MutableStateFlow(SlotStatePreview(_isRefreshing, true))
    private var _backups: MutableStateFlow<BackupsState> = MutableStateFlow(BackupsState(context, _isRefreshing, navController))
    override val slotSuffix: String = "_b"

    override val slotA: StateFlow<SlotStateInterface>
        get() = _slotA.asStateFlow()
    override val slotB: StateFlow<SlotStateInterface>
        get() = _slotB.asStateFlow()
    override val backups: StateFlow<BackupsState>
        get() = _backups.asStateFlow()

    override fun refresh(context: Context) {
        slotA.value.refresh(context)
        slotB.value.refresh(context)
    }
}
