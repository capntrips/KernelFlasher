package com.github.capntrips.kernelflasher.ui.state.device

import android.content.Context
import com.github.capntrips.kernelflasher.ui.state.backups.BackupsState
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import kotlinx.coroutines.flow.StateFlow

interface DeviceStateInterface {
    val slotSuffix: String
    val slotA: StateFlow<SlotStateInterface>
    val slotB: StateFlow<SlotStateInterface>
    val backups: StateFlow<BackupsState>
    fun refresh(context: Context)
}
