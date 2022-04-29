package com.github.capntrips.kernelflasher.ui.screens.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.ui.state.device.DeviceStateInterface
import com.github.capntrips.kernelflasher.ui.state.device.DeviceStatePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModelPreview constructor(context: Context, navController: NavController) : ViewModel(), MainViewModelInterface {
    private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var _uiState: MutableStateFlow<DeviceStateInterface> = MutableStateFlow(
        DeviceStatePreview(context, _isRefreshing, navController)
    )

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()
    override val uiState: StateFlow<DeviceStateInterface>
        get() = _uiState.asStateFlow()

    override fun refresh(context: Context) {}
    override fun saveDmesg(context: Context) {}
    override fun saveLogcat(context: Context) {}
    override fun reboot() {}
}
