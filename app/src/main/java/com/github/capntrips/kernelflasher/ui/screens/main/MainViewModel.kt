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
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.common.types.backups.Backup
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsViewModel
import com.github.capntrips.kernelflasher.ui.screens.reboot.RebootViewModel
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel
import com.github.capntrips.kernelflasher.ui.screens.updates.UpdatesViewModel
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@ExperimentalSerializationApi
class MainViewModel(
    context: Context,
    fileSystemManager: FileSystemManager,
    private val navController: NavController
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/MainViewModel"
    }
    val slotSuffix: String

    val kernelVersion: String
    val slotA: SlotViewModel
    val slotB: SlotViewModel
    val backups: BackupsViewModel
    val updates: UpdatesViewModel
    val reboot: RebootViewModel
    val hasRamoops: Boolean

    private val _isRefreshing: MutableState<Boolean> = mutableStateOf(true)
    private var _error: String? = null
    private var _backups: MutableMap<String, Backup> = mutableMapOf()

    val isRefreshing: Boolean
        get() = _isRefreshing.value
    val hasError: Boolean
        get() = _error != null
    val error: String
        get() = _error!!

    init {
        PartitionUtil.init(context, fileSystemManager)
        val bootA = PartitionUtil.findPartitionBlockDevice(context, "boot", "_a")!!
        val bootB = PartitionUtil.findPartitionBlockDevice(context, "boot", "_b")!!
        val initBootA = PartitionUtil.findPartitionBlockDevice(context, "init_boot", "_a")
        val initBootB = PartitionUtil.findPartitionBlockDevice(context, "init_boot", "_b")
        kernelVersion = Shell.cmd("echo $(uname -r) $(uname -v)").exec().out[0]
        slotSuffix = Shell.cmd("getprop ro.boot.slot_suffix").exec().out[0]
        backups = BackupsViewModel(context, fileSystemManager, navController, _isRefreshing, _backups)
        updates = UpdatesViewModel(context, fileSystemManager, navController, _isRefreshing)
        reboot = RebootViewModel(context, fileSystemManager, navController, _isRefreshing)
        slotA = SlotViewModel(context, fileSystemManager, navController, _isRefreshing, slotSuffix == "_a", "_a", bootA, initBootA, _backups)
        if (slotA.hasError) {
            _error = slotA.error
        }
        slotB = SlotViewModel(context, fileSystemManager, navController, _isRefreshing, slotSuffix == "_b", "_b", bootB, initBootB, _backups)
        if (slotB.hasError) {
            _error = slotB.error
        }

        hasRamoops = fileSystemManager.getFile("/sys/fs/pstore/console-ramoops-0").exists()
        _isRefreshing.value = false
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
            viewModelScope.launch(Dispatchers.Main) {
                _isRefreshing.value = true
            }
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
            viewModelScope.launch(Dispatchers.Main) {
                _isRefreshing.value = false
            }
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

    fun saveRamoops(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            @SuppressLint("SdCardPath")
            val ramoops = File("/sdcard/Download/console-ramoops--$now.log")
            Shell.cmd("cp /sys/fs/pstore/console-ramoops-0 $ramoops").exec()
            if (ramoops.exists()) {
                log(context, "Saved ramoops to $ramoops")
            } else {
                log(context, "Failed to save $ramoops", shouldThrow = true)
            }
        }
    }

    fun saveDmesg(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            @SuppressLint("SdCardPath")
            val dmesg = File("/sdcard/Download/dmesg--$now.log")
            Shell.cmd("dmesg > $dmesg").exec()
            if (dmesg.exists()) {
                log(context, "Saved dmesg to $dmesg")
            } else {
                log(context, "Failed to save $dmesg", shouldThrow = true)
            }
        }
    }

    fun saveLogcat(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            @SuppressLint("SdCardPath")
            val logcat = File("/sdcard/Download/logcat--$now.log")
            Shell.cmd("logcat -d > $logcat").exec()
            if (logcat.exists()) {
                log(context, "Saved logcat to $logcat")
            } else {
                log(context, "Failed to save $logcat", shouldThrow = true)
            }
        }
    }
}
