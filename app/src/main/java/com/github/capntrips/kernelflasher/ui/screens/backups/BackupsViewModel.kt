package com.github.capntrips.kernelflasher.ui.screens.backups

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class BackupsViewModel(
    context: Context,
    private val _isRefreshing: MutableState<Boolean>,
    private val navController: NavController
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/BackupsState"
    }

    var backups: HashMap<String, Properties> = HashMap()
    var currentBackup: String? = null
    var wasRestored: Boolean = false

    val isRefreshing: Boolean
        get() = _isRefreshing.value

    init {
        refresh(context)
    }

    fun refresh(context: Context) {
        val externalDir = context.getExternalFilesDir(null)
        val backupsDir = File(externalDir, "backups")
        if (backupsDir.exists()) {
            val children = backupsDir.listFiles()
            if (children != null) {
                for (child in children.sortedByDescending{it.name}) {
                    if (!child.isDirectory) {
                        continue
                    }
                    val propFile = File(child, "backup.prop")
                    val inputStream = FileInputStream(propFile)
                    val props = Properties()
                    props.load(inputStream)
                    backups[child.name] = props
                }
            }
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

    fun clearCurrent() {
        currentBackup = null
        wasRestored = false
    }

    fun reboot() {
        launch {
            Shell.cmd("reboot").exec()
        }
    }

    private fun restorePartition(context: Context, image: File, partition: File) {
        if (partition.exists()) {
            val partitionSize = Shell.cmd("wc -c < $partition").exec().out[0].toUInt()
            val imageSize = Shell.cmd("wc -c < $image").exec().out[0].toUInt()
            if (partitionSize < imageSize) {
                log(context, "Partition ${partition.name} is smaller than image", shouldThrow = true)
            } else if (partitionSize == imageSize) {
                val inputStream = FileInputStream(image)
                val outputStream = SuFileOutputStream.open(partition)
                outputStream.write(inputStream.readBytes())
                inputStream.close()
                outputStream.close()
            } else {
                Shell.cmd("dd if=/dev/zero of=$partition").exec()
                Shell.cmd("dd if=$image of=$partition").exec()
            }
        } else {
            log(context, "Partition ${partition.name} was not found", shouldThrow = true)
        }
    }

    @Suppress("SameParameterValue")
    private fun restoreLogicalPartition(context: Context, source: File, partitionName: String, slotSuffix: String) {
        val sourceFile = File(source, "$partitionName.img")
        if (sourceFile.exists()) {
            val sourceFileSize = Shell.cmd("wc -c < $sourceFile").exec().out[0].toUInt()
            val lptools = File(context.filesDir, "lptools_static")
            Shell.cmd("$lptools remove ${partitionName}_kf").exec()
            if (Shell.cmd("$lptools create ${partitionName}_kf $sourceFileSize").exec().isSuccess) {
                if (Shell.cmd("$lptools unmap ${partitionName}_kf").exec().isSuccess) {
                    if (Shell.cmd("$lptools map ${partitionName}_kf").exec().isSuccess) {
                        val partition = SuFile("/dev/block/mapper/${partitionName}_kf")
                        restorePartition(context, sourceFile, partition)
                        if (!Shell.cmd("$lptools replace ${partitionName}_kf $partitionName$slotSuffix").exec().isSuccess) {
                            log(context, "Failed to replace $partitionName$slotSuffix", shouldThrow = true)
                        }
                    } else {
                        log(context, "Failed to remap ${partitionName}_kf", shouldThrow = true)
                    }
                } else {
                    log(context, "Failed to unmap ${partitionName}_kf", shouldThrow = true)
                }
            } else {
                log(context, "Failed to create ${partitionName}_kf", shouldThrow = true)
            }
        }
    }

    private fun restorePhysicalPartition(context: Context, source: File, partitionName: String, slotSuffix: String) {
        val sourceFile = File(source, "$partitionName.img")
        if (sourceFile.exists()) {
            val partition = SuFile("/dev/block/by-name/$partitionName$slotSuffix")
            restorePartition(context, sourceFile, partition)
        }
    }

    fun restore(context: Context, slotSuffix: String) {
        launch {
            val externalDir = context.getExternalFilesDir(null)
            val backupsDir = File(externalDir, "backups")
            val backupDir = File(backupsDir, currentBackup!!)
            // val props = backups.getValue(currentBackup!!)
            if (!backupDir.exists()) {
                log(context, "Backup $currentBackup does not exists", shouldThrow = true)
                return@launch
            }
            restorePhysicalPartition(context, backupDir, "boot", slotSuffix)
            restorePhysicalPartition(context, backupDir, "vbmeta", slotSuffix)
            restoreLogicalPartition(context, backupDir, "vendor_dlkm", slotSuffix)
            restorePhysicalPartition(context, backupDir, "vendor_boot", slotSuffix)
            restorePhysicalPartition(context, backupDir, "dtbo", slotSuffix)
            restorePhysicalPartition(context, backupDir, "init_boot", slotSuffix)
            restorePhysicalPartition(context, backupDir, "recovery", slotSuffix)
            log(context, "Backup restored successfully")
            wasRestored = true
        }
    }

    fun delete(context: Context, callback: () -> Unit) {
        launch {
            val externalDir = context.getExternalFilesDir(null)
            val backupsDir = File(externalDir, "backups")
            val backupDir = File(backupsDir, currentBackup!!)
            backupDir.deleteRecursively()
            backups.remove(currentBackup!!)
            withContext (Dispatchers.Main) {
                callback.invoke()
            }
        }
    }
}
