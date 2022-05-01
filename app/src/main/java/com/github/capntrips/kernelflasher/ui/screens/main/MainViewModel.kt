package com.github.capntrips.kernelflasher.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsViewModel
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainViewModel(
    context: Context,
    private val navController: NavController
) : ViewModel() {
    companion object {
        const val TAG: String = "kernelflasher/MainViewModel"
    }
    val slotSuffix: String

    val slotA: SlotViewModel
    val slotB: SlotViewModel
    val backups: BackupsViewModel

    private val _isRefreshing: MutableState<Boolean> = mutableStateOf(false)
    private var _error: String? = null

    val isRefreshing: Boolean
        get() = _isRefreshing.value
    val hasError: Boolean
        get() = _error != null
    val error: String
        get() = _error ?: "Unknown Error"

    init {
        val bootA = File("/dev/block/by-name/boot_a")
        val bootB = File("/dev/block/by-name/boot_b")

        slotSuffix = Shell.cmd("getprop ro.boot.slot_suffix").exec().out[0]
        backups = BackupsViewModel(context, _isRefreshing, navController)
        slotA = SlotViewModel(context, slotSuffix == "_a", "_a", bootA, _isRefreshing, navController, backups = backups.backups)
        slotB = SlotViewModel(context, slotSuffix == "_b", "_b", bootB, _isRefreshing, navController, backups = backups.backups)
    }

    fun refresh(context: Context) {
        launch {
            slotA.refresh(context)
            slotB.refresh(context)
            backups.refresh(context)
        }
    }

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

    @Suppress("SameParameterValue")
    private fun log(context: Context, message: String, shouldThrow: Boolean = false) {
        Log.d(TAG, message)
        if (!shouldThrow) {
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } else {
            throw Exception(message)
        }
    }

    @SuppressLint("SdCardPath")
    fun saveDmesg(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val dmesg = File("/sdcard/Download/dmesg--$now")
            Shell.cmd("dmesg > $dmesg").exec()
            if (dmesg.exists()) {
                log(context, "Saved dmesg to $dmesg")
            } else {
                log(context, "Failed to save $dmesg", shouldThrow = true)
            }
        }
    }

    @SuppressLint("SdCardPath")
    fun saveLogcat(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val logcat = File("/sdcard/Download/logcat--$now")
            Shell.cmd("logcat -d > $logcat").exec()
            if (logcat.exists()) {
                log(context, "Saved logcat to $logcat")
            } else {
                log(context, "Failed to save $logcat", shouldThrow = true)
            }
        }
    }

    fun reboot() {
        launch {
            Shell.cmd("reboot").exec()
        }
    }
}
