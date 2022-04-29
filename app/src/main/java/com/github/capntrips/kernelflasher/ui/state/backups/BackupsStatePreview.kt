package com.github.capntrips.kernelflasher.ui.state.backups

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Properties

class BackupsStatePreview constructor(
    private val _isRefreshing : MutableStateFlow<Boolean>
) : ViewModel(),
    BackupsStateInterface {
    override var backups: HashMap<String, Properties> = HashMap()
    override var currentBackup: String? = "2022-01-01--00-00-00"
    override var wasRestored: Boolean = false

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()

    init {
        val props = Properties()
        props.setProperty("sha1", "0a1b2c3d")
        props.setProperty("kernel", "5.10.100")
        backups[currentBackup!!] = props
    }

    override fun refresh(context: Context) {}
    override fun clearCurrent() {}
    override fun reboot() {}
    override fun restore(context: Context, slotSuffix: String) {}
    override fun delete(context: Context, callback: () -> Unit) {}
}