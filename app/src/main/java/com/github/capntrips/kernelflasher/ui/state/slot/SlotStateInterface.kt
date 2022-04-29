package com.github.capntrips.kernelflasher.ui.state.slot

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface SlotStateInterface {
    val isActive: Boolean
    val slotSuffix: String
    var sha1: String
    var kernelVersion: String?
    var hasVendorDlkm: Boolean
    var isVendorDlkmMounted: Boolean
    var wasFlashed: Boolean
    val isRefreshing: StateFlow<Boolean>
    fun refresh(context: Context)
    fun reboot()
    fun getKernel(context: Context)
    fun unmountVendorDlkm(context: Context)
    fun mountVendorDlkm(context: Context)
    fun unmapVendorDlkm(context: Context)
    fun mapVendorDlkm(context: Context)
    fun backup(context: Context, callback: () -> Unit)
    fun flash(context: Context, uri: Uri)
}
