package com.github.capntrips.kernelflasher.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsViewModel
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel
import com.github.capntrips.kernelflasher.ui.state.device.DeviceState
import com.github.capntrips.kernelflasher.ui.state.device.DeviceStateInterface
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainViewModel constructor(context: Context, private val navController: NavController) : ViewModel(), MainViewModelInterface {
    companion object {
        const val TAG: String = "kernelflasher/MainViewModel"
    }
    private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private lateinit var _uiState: MutableStateFlow<DeviceStateInterface>
    private var _error: String? = null

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()
    override val uiState: StateFlow<DeviceStateInterface>
        get() = _uiState.asStateFlow()
    val hasError: Boolean
        get() = _error != null
    val error: String
        get() = _error ?: "Unknown Error"

    init {
        try {
            _uiState = MutableStateFlow(DeviceState(context, _isRefreshing, navController))
        } catch (e: Exception) {
            _error = e.message
        }
    }

    override fun refresh(context: Context) {
        launch {
            try {
                uiState.value.refresh(context)
            } catch (e: Exception) {
                _error = e.message
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.emit(true)
            try {
                block()
            } catch (e: Exception) {
                withContext (Dispatchers.Main) {
                    navController.navigate("error/${e.message}") {
                        popUpTo("main")
                    }
                }
            }
            _isRefreshing.emit(false)
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
    override fun saveDmesg(context: Context) {
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
    override fun saveLogcat(context: Context) {
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

    override fun reboot() {
        Shell.cmd("reboot").exec()
    }

    fun toSlotViewModelA(): SlotViewModel {
        return SlotViewModel(_uiState.value.slotA)
    }

    fun toSlotViewModelB(): SlotViewModel {
        return SlotViewModel(_uiState.value.slotB)
    }

    fun toBackupsViewModel(): BackupsViewModel {
        return BackupsViewModel(_uiState.value.backups)
    }
}
