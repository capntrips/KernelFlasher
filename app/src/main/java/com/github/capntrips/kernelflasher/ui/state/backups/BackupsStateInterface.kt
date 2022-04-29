package com.github.capntrips.kernelflasher.ui.state.backups

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.util.Properties

interface BackupsStateInterface {
    var backups: HashMap<String, Properties>
    var currentBackup: String?
    var wasRestored: Boolean
    val isRefreshing: StateFlow<Boolean>
    fun refresh(context: Context)
    fun clearCurrent()
    fun reboot()
    fun restore(context: Context, slotSuffix: String)
    fun delete(context: Context, callback: () -> Unit)
}