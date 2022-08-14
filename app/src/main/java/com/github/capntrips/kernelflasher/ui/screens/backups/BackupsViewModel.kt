package com.github.capntrips.kernelflasher.ui.screens.backups

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.common.extensions.ExtendedFile.readText
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.common.types.backups.Backup
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class BackupsViewModel(
    context: Context,
    private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>,
    private val _backups: MutableMap<String, Backup>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/BackupsState"
    }

    var currentBackup: String? = null
    var wasRestored: Boolean = false
    @Suppress("PropertyName")
    @Deprecated("Backup migration will be removed in the first stable release")
    private var _needsMigration: MutableState<Boolean> = mutableStateOf(false)

    val isRefreshing: Boolean
        get() = _isRefreshing.value
    val backups: Map<String, Backup>
        get() = _backups
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Backup migration will be removed in the first stable release")
    val needsMigration: Boolean
        get() = _needsMigration.value

    init {
        refresh(context)
    }

    fun refresh(context: Context) {
        val oldDir = context.getExternalFilesDir(null)
        val oldBackupsDir = File(oldDir, "backups")
        @Deprecated("Backup migration will be removed in the first stable release")
        _needsMigration.value = oldBackupsDir.exists() && oldBackupsDir.listFiles()?.size!! > 0
        @SuppressLint("SdCardPath")
        val externalDir = File("/sdcard/KernelFlasher")
        val backupsDir = fileSystemManager.getFile("$externalDir/backups")
        if (backupsDir.exists()) {
            val children = backupsDir.listFiles()
            if (children != null) {
                for (child in children.sortedByDescending{it.name}) {
                    if (!child.isDirectory) {
                        continue
                    }
                    val jsonFile = child.getChildFile("backup.json")
                    if (jsonFile.exists()) {
                        _backups[child.name] = Json.decodeFromString(jsonFile.readText())
                    }
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

    private fun restorePartition(context: Context, image: ExtendedFile, blockDevice: ExtendedFile) {
        val partitionSize = Shell.cmd("wc -c < $blockDevice").exec().out[0].toUInt()
        val imageSize = Shell.cmd("wc -c < $image").exec().out[0].toUInt()
        if (partitionSize < imageSize) {
            log(context, "Partition ${blockDevice.name} is smaller than image", shouldThrow = true)
        } else if (partitionSize == imageSize) {
            image.newInputStream().use { inputStream ->
                blockDevice.newOutputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } else {
            Shell.cmd("dd bs=4096 if=/dev/zero of=$blockDevice").exec()
            Shell.cmd("dd bs=4096 if=$image of=$blockDevice").exec()
        }
    }

    @Suppress("SameParameterValue")
    private fun restoreLogicalPartition(context: Context, image: ExtendedFile, blockDevice: ExtendedFile, partitionName: String, slotSuffix: String) {
        val sourceFileSize = Shell.cmd("wc -c < $image").exec().out[0].toUInt()
        val lptools = File(context.filesDir, "lptools_static")
        Shell.cmd("$lptools remove ${partitionName}_kf").exec()
        if (Shell.cmd("$lptools create ${partitionName}_kf $sourceFileSize").exec().isSuccess) {
            if (Shell.cmd("$lptools unmap ${partitionName}_kf").exec().isSuccess) {
                if (Shell.cmd("$lptools map ${partitionName}_kf").exec().isSuccess) {
                    val temporaryBlockDevice = fileSystemManager.getFile("/dev/block/mapper/${partitionName}_kf")
                    restorePartition(context, image, temporaryBlockDevice)
                    if (!Shell.cmd("$lptools replace ${partitionName}_kf $partitionName$slotSuffix").exec().isSuccess) {
                        log(context, "Replacing $partitionName$slotSuffix failed", shouldThrow = true)
                    }
                } else {
                    log(context, "Remapping ${partitionName}_kf failed", shouldThrow = true)
                }
            } else {
                log(context, "Unmapping ${partitionName}_kf failed", shouldThrow = true)
            }
        } else {
            // TODO: add log for restore operations
            // ui_print(context, "Creating ${partitionName}_kf failed. Attempting to resize $partitionName$slotSuffix...")
            val httools = File(context.filesDir, "httools_static")
            if (Shell.cmd("$httools umount $partitionName").exec().isSuccess) {
                val verityBlockDevice = blockDevice.parentFile!!.getChildFile("${partitionName}-verity")
                if (verityBlockDevice.exists()) {
                    if (!Shell.cmd("$lptools unmap ${partitionName}-verity").exec().isSuccess) {
                        log(context, "Unmapping ${partitionName}-verity failed", shouldThrow = true)
                    }
                }
                if (Shell.cmd("$lptools unmap $partitionName$slotSuffix").exec().isSuccess) {
                    if (Shell.cmd("$lptools resize $partitionName$slotSuffix \$(wc -c < $image)").exec().isSuccess) {
                        if (Shell.cmd("$lptools map $partitionName$slotSuffix").exec().isSuccess) {
                            restorePartition(context, image, blockDevice)
                            if (!Shell.cmd("$httools mount $partitionName").exec().isSuccess) {
                                log(context, "Mounting $partitionName failed", shouldThrow = true)
                            }
                        } else {
                            log(context, "Remapping $partitionName$slotSuffix failed", shouldThrow = true)
                        }
                    } else {
                        log(context, "Resizing $partitionName$slotSuffix failed", shouldThrow = true)
                    }
                } else {
                    log(context, "Unmapping $partitionName$slotSuffix failed", shouldThrow = true)
                }
            } else {
                log(context, "Unmounting $partitionName failed", shouldThrow = true)
            }
        }
    }

    private fun restorePartitions(context: Context, source: ExtendedFile, slotSuffix: String) {
        for (partitionName in PartitionUtil.PartitionNames) {
            val image = source.getChildFile("$partitionName.img")
            if (image.exists()) {
                val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
                if (blockDevice != null && blockDevice.exists()) {
                    if (PartitionUtil.isPartitionLogical(context, partitionName)) {
                        restoreLogicalPartition(context, image, blockDevice, partitionName, slotSuffix)
                    } else {
                        restorePartition(context, image, blockDevice)
                    }
                } else {
                    log(context, "Partition $partitionName was not found", shouldThrow = true)
                }
            }
        }
    }

    fun restore(context: Context, slotSuffix: String) {
        launch {
            @SuppressLint("SdCardPath")
            val externalDir = File("/sdcard/KernelFlasher")
            val backupsDir = fileSystemManager.getFile("$externalDir/backups")
            val backupDir = backupsDir.getChildFile(currentBackup!!)
            if (!backupDir.exists()) {
                log(context, "Backup $currentBackup does not exists", shouldThrow = true)
                return@launch
            }
            restorePartitions(context, backupDir, slotSuffix)
            log(context, "Backup restored successfully")
            wasRestored = true
        }
    }

    fun delete(context: Context, callback: () -> Unit) {
        launch {
            @SuppressLint("SdCardPath")
            val externalDir = File("/sdcard/KernelFlasher")
            val backupsDir = fileSystemManager.getFile("$externalDir/backups")
            val backupDir = backupsDir.getChildFile(currentBackup!!)
            if (!backupDir.exists()) {
                log(context, "Backup $currentBackup does not exists", shouldThrow = true)
                return@launch
            }
            backupDir.deleteRecursively()
            _backups.remove(currentBackup!!)
            withContext(Dispatchers.Main) {
                callback.invoke()
            }
        }
    }

    @SuppressLint("SdCardPath")
    @Deprecated("Backup migration will be removed in the first stable release")
    fun migrate(context: Context) {
        launch {
            val externalDir = SuFile("/sdcard/KernelFlasher")
            if (!externalDir.exists()) {
                if (!externalDir.mkdir()) {
                    log(context, "Failed to create KernelFlasher dir on /sdcard", shouldThrow = true)
                }
            }
            val backupsDir = SuFile(externalDir, "backups")
            if (!backupsDir.exists()) {
                if (!backupsDir.mkdir()) {
                    log(context, "Failed to create backups dir", shouldThrow = true)
                }
            }
            val oldDir = context.getExternalFilesDir(null)
            val oldBackupsDir = File(oldDir, "backups")
            if (oldBackupsDir.exists()) {
                val indentedJson = Json { prettyPrint = true }
                val children = oldBackupsDir.listFiles()
                if (children != null) {
                    for (child in children.sortedByDescending{it.name}) {
                        if (!child.isDirectory) {
                            child.delete()
                            continue
                        }
                        val propFile = File(child, "backup.prop")
                        @Suppress("BlockingMethodInNonBlockingContext")
                        val inputStream = FileInputStream(propFile)
                        val props = Properties()
                        @Suppress("BlockingMethodInNonBlockingContext")
                        props.load(inputStream)

                        val name = child.name
                        val type = props.getProperty("type", "raw")
                        val kernelVersion = props.getProperty("kernel")
                        val bootSha1 = if (type == "raw") props.getProperty("sha1") else null
                        val filename = if (type == "ak3") "ak3.zip" else null
                        propFile.delete()

                        val dest = SuFile(backupsDir, child.name)
                        @Suppress("BlockingMethodInNonBlockingContext")
                        Shell.cmd("mv $child $dest").exec()
                        if (!dest.exists()) {
                            throw Error("Too slow")
                        }
                        val jsonFile = File(dest, "backup.json")
                        val backup = Backup(name, type, kernelVersion, bootSha1, filename)
                        @Suppress("BlockingMethodInNonBlockingContext")
                        SuFileOutputStream.open(jsonFile).use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
                        _backups[name] = backup
                    }
                }
                oldBackupsDir.delete()
            }
            refresh(context)
        }
    }
}
