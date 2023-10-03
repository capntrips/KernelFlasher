package com.github.capntrips.kernelflasher.ui.screens.reboot

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RebootViewModel(
    @Suppress("UNUSED_PARAMETER") ignoredContext: Context,
    @Suppress("unused") private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/RebootState"
    }

    val isRefreshing: Boolean
        get() = _isRefreshing.value

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                block()
            } catch (e: Exception) {
                withContext (Dispatchers.Main) {
                    Log.e(TAG, e.message, e)
                    navController.navigate("error/${e.message}") {
                        popUpTo("main")
                    }
                }
            }
            _isRefreshing.value = false
        }
    }

    private fun reboot(destination: String = "") {
        launch {
            // https://github.com/topjohnwu/Magisk/blob/v25.2/app/src/main/java/com/topjohnwu/magisk/ktx/XSU.kt#L11-L15
            if (destination == "recovery") {
                // https://github.com/topjohnwu/Magisk/pull/5637
                Shell.cmd("/system/bin/input keyevent 26").submit()
            }
            Shell.cmd("/system/bin/svc power reboot $destination || /system/bin/reboot $destination").submit()
        }
    }

    fun rebootSystem() {
        reboot()
    }

    fun rebootUserspace() {
        reboot("userspace")
    }

    fun rebootRecovery() {
        reboot("recovery")
    }

    fun rebootBootloader() {
        reboot("bootloader")
    }

    fun rebootDownload() {
        reboot("download")
    }

    fun rebootEdl() {
        reboot("edl")
    }
}
