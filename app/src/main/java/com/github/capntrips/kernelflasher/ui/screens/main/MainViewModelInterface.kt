package com.github.capntrips.kernelflasher.ui.screens.main

import android.content.Context
import com.github.capntrips.kernelflasher.ui.state.device.DeviceStateInterface
import kotlinx.coroutines.flow.StateFlow

interface MainViewModelInterface {
    val isRefreshing: StateFlow<Boolean>
    val uiState: StateFlow<DeviceStateInterface>
    fun refresh(context: Context)
    fun saveDmesg(context: Context)
    fun saveLogcat(context: Context)
    fun reboot()
}
