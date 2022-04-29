package com.github.capntrips.kernelflasher.ui.state.slot

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SlotStatePreview constructor(private val _isRefreshing : MutableStateFlow<Boolean>, override val isActive: Boolean) : ViewModel(),
    SlotStateInterface {
    override val slotSuffix: String = "_a"
    override var sha1: String = "0a1b2c3d"
    override var kernelVersion: String? = "5.10.100"
    override var hasVendorDlkm: Boolean = true
    override var isVendorDlkmMounted: Boolean = true
    override var wasFlashed: Boolean = true

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isRefreshing.emit(true)
            block()
            _isRefreshing.emit(false)
        }
    }

    override fun refresh(context: Context) {
        launch {
            delay(500)
        }
    }

    override fun reboot() {}
    override fun getKernel(context: Context) {}
    override fun unmountVendorDlkm(context: Context) {}
    override fun mountVendorDlkm(context: Context) {}
    override fun unmapVendorDlkm(context: Context) {}
    override fun mapVendorDlkm(context: Context) {}
    override fun backup(context: Context, callback: () -> Unit) {}
    override fun flash(context: Context, uri: Uri) {}
}
