package com.github.capntrips.kernelflasher.ui.state.device

import android.content.Context
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.ui.state.backups.BackupsState
import com.github.capntrips.kernelflasher.ui.state.slot.SlotState
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class DeviceState constructor(context: Context, _isRefreshing : MutableStateFlow<Boolean>, navController: NavController) :
    DeviceStateInterface {
    private var _slotA: MutableStateFlow<SlotStateInterface>
    private var _slotB: MutableStateFlow<SlotStateInterface>
    private var _backups: MutableStateFlow<BackupsState>
    override val slotSuffix: String

    override val backups: StateFlow<BackupsState>
        get() = _backups.asStateFlow()
    override val slotA: StateFlow<SlotStateInterface>
        get() = _slotA.asStateFlow()
    override val slotB: StateFlow<SlotStateInterface>
        get() = _slotB.asStateFlow()

    init {
        val bootA = File("/dev/block/by-name/boot_a")
        val bootB = File("/dev/block/by-name/boot_b")

        slotSuffix = Shell.cmd("getprop ro.boot.slot_suffix").exec().out[0]
        _backups = MutableStateFlow(BackupsState(context, _isRefreshing, navController))
        _slotA = MutableStateFlow(SlotState(context, slotSuffix == "_a", "_a", bootA, _isRefreshing, navController, backups = backups.value.backups))
        _slotB = MutableStateFlow(SlotState(context, slotSuffix == "_b", "_b", bootB, _isRefreshing, navController, backups = backups.value.backups))
    }

    override fun refresh(context: Context) {
        slotA.value.refresh(context)
        slotB.value.refresh(context)
        backups.value.refresh(context)
    }
}
