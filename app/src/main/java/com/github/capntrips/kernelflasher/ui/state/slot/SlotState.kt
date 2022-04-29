package com.github.capntrips.kernelflasher.ui.state.slot

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

class SlotState(
    context: Context,
    override val isActive: Boolean,
    override val slotSuffix: String,
    private val boot: File,
    private val _isRefreshing : MutableStateFlow<Boolean>,
    private val navController: NavController,
    private val isImage: Boolean = false,
    private val backups: HashMap<String, Properties>? = null
) : ViewModel(),
    SlotStateInterface {
    companion object {
        const val TAG: String = "kernelflasher/SlotState"
    }

    override lateinit var sha1: String
    override var kernelVersion: String? = null
    override var hasVendorDlkm: Boolean = false
    override var isVendorDlkmMounted: Boolean = false
    override var wasFlashed: Boolean = false
    private var wasSlotReset: Boolean = false

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()

    init {
        refresh(context)
    }

    override fun refresh(context: Context) {
        Shell.cmd("/data/adb/magisk/magiskboot unpack $boot").exec()

        val ramdisk = File(context.filesDir, "ramdisk.cpio")

        val mapperDir = "/dev/block/mapper"
        val vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
        hasVendorDlkm = vendorDlkm.exists()
        if (hasVendorDlkm) {
            val dmPath = Shell.cmd("readlink -f $vendorDlkm").exec().out[0]
            var mounts = Shell.cmd("mount | grep $dmPath").exec().out
            if (mounts.isNotEmpty()) {
                isVendorDlkmMounted = true
            } else {
                mounts = Shell.cmd("mount | grep vendor_dlkm-verity").exec().out
                isVendorDlkmMounted = mounts.isNotEmpty()
            }
        } else {
            isVendorDlkmMounted = false
        }

        if (!isImage || ramdisk.exists()) {
            when (Shell.cmd("/data/adb/magisk/magiskboot cpio ramdisk.cpio test").exec().code) {
                0 -> sha1 = Shell.cmd("/data/adb/magisk/magiskboot sha1 $boot").exec().out[0]
                1 -> sha1 = Shell.cmd("/data/adb/magisk/magiskboot cpio ramdisk.cpio sha1").exec().out[0]
                else -> log(context, "Invalid boot.img", shouldThrow = true)
            }
        } else {
            log(context, "Invalid boot.img", shouldThrow = true)
        }
        kernelVersion = null
        Shell.cmd("/data/adb/magisk/magiskboot cleanup").exec()
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
            if (wasSlotReset) {
                resetSlot()
            }
            throw Exception(message)
        }
    }

    override fun reboot() {
        Shell.cmd("reboot").exec()
    }

    @Suppress("FunctionName", "SameParameterValue")
    private fun _getKernel(context: Context) {
        Shell.cmd("/data/adb/magisk/magiskboot unpack $boot").exec()
        val kernel = File(context.filesDir, "kernel")
        if (kernel.exists()) {
            val result = Shell.cmd("strings kernel | grep -E -m1 'Linux version.*#' | cut -d\\  -f3").exec().out
            if (result.isNotEmpty()) {
                kernelVersion = result[0]
            }
        }
        Shell.cmd("/data/adb/magisk/magiskboot cleanup").exec()
    }

    override fun getKernel(context: Context) {
        launch {
            _getKernel(context)
        }
    }

    override fun unmountVendorDlkm(context: Context) {
        launch {
            val mapperDir = "/dev/block/mapper"
            val vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
            val dmPath = Shell.cmd("readlink -f $vendorDlkm").exec().out[0]
            var mounts = Shell.cmd("mount | grep $dmPath").exec().out
            if (mounts.isNotEmpty()) {
                Shell.cmd("umount $dmPath").exec()
            } else {
                mounts = Shell.cmd("mount | grep vendor_dlkm-verity").exec().out
                if (mounts.isNotEmpty()) {
                    Shell.cmd("umount /vendor_dlkm-verity").exec()
                }
            }
            refresh(context)
        }
    }

    override fun mountVendorDlkm(context: Context) {
        launch {
            val mapperDir = "/dev/block/mapper"
            val vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
            val dmPath = Shell.cmd("readlink -f $vendorDlkm").exec().out[0]
            Shell.cmd("mount -t ext4 -o ro,barrier=1 $dmPath /vendor_dlkm").exec()
            refresh(context)
        }
    }

    override fun unmapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools")
            Shell.cmd("$lptools unmap vendor_dlkm$slotSuffix").exec()
            refresh(context)
        }
    }

    override fun mapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools")
            Shell.cmd("$lptools map vendor_dlkm$slotSuffix").exec()
            refresh(context)
        }
    }

    private fun backupPartition(context: Context, partition: File, destination: File, isOptional: Boolean = false) {
        if (partition.exists()) {
            val inputStream = SuFileInputStream.open(partition)
            val outputStream = FileOutputStream(destination)
            outputStream.write(inputStream.readBytes())
            inputStream.close()
            outputStream.close()
        } else if (!isOptional) {
            log(context, "Partition ${partition.name} was not found", shouldThrow = true)
        }
    }

    @Suppress("SameParameterValue")
    private fun backupLogicalPartition(context: Context, partitionName: String, destination: File, isOptional: Boolean = false) {
        val partition = SuFile("/dev/block/mapper/$partitionName$slotSuffix")
        val destinationFile = File(destination, "$partitionName.img")
        backupPartition(context, partition, destinationFile, isOptional)
    }

    private fun backupPhysicalPartition(context: Context, partitionName: String, destination: File, isOptional: Boolean = false) {
        val partition = SuFile(boot.parentFile!!, "$partitionName$slotSuffix")
        val destinationFile = File(destination, "$partitionName.img")
        backupPartition(context, partition, destinationFile, isOptional)
    }

    override fun backup(context: Context, callback: () -> Unit) {
        launch {
            val props = Properties()
            props.setProperty("sha1", sha1)
            if (kernelVersion != null) {
                props.setProperty("kernel", kernelVersion)
            } else if (isActive) {
                props.setProperty("kernel", System.getProperty("os.version")!!)
            } else {
                _getKernel(context)
                props.setProperty("kernel", kernelVersion!!)
            }
            val externalDir = context.getExternalFilesDir(null)
            val backupsDir = File(externalDir, "backups")
            if (!backupsDir.exists()) {
                if (!backupsDir.mkdir()) {
                    log(context, "Failed to create backups dir", shouldThrow = true)
                }
            }
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val backupDir = File(backupsDir, now)
            if (backupDir.exists()) {
                log(context, "Backup $now already exists", shouldThrow = true)
                return@launch
            } else {
                if (!backupDir.mkdir()) {
                    log(context, "Failed to create backup dir", shouldThrow = true)
                    return@launch
                }
            }
            val propFile = File(backupDir, "backup.prop")
            @Suppress("BlockingMethodInNonBlockingContext")
            props.store(propFile.outputStream(), props.getProperty("kernel"))
            backupPhysicalPartition(context, "boot", backupDir)
            backupLogicalPartition(context, "vendor_dlkm", backupDir, true)
            backupPhysicalPartition(context, "vendor_boot", backupDir)
            backupPhysicalPartition(context, "dtbo", backupDir)
            backups?.put(now, props)
            withContext (Dispatchers.Main) {
                callback.invoke()
            }
        }
    }

    private fun resetSlot() {
        val activeSlotSuffix = Shell.cmd("getprop ro.boot.slot_suffix").exec().out[0]
        val newSlot = if (activeSlotSuffix == "_a") "_b" else "_a"
        Shell.cmd("magisk resetprop -n ro.boot.slot_suffix $newSlot").exec()
        wasSlotReset = !wasSlotReset
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun flash(context: Context, uri: Uri) {
        launch {
            if (!isActive) {
                resetSlot()
            }
            val source = context.contentResolver.openInputStream(uri)
            val zip = File(context.filesDir, "ak.zip")
            source.use { inputStream ->
                zip.outputStream().use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
            }
            if (zip.exists()) {
                val akHome = File(context.filesDir, "akhome")
                akHome.mkdir()
                if (akHome.exists()) {
                    val updateBinary = File(akHome, "update-binary")
                    Shell.cmd("unzip -p $zip META-INF/com/google/android/update-binary > $akHome/update-binary").exec()
                    if (updateBinary.exists()) {
                        val result = Shell.cmd("AKHOME=$akHome \$SHELL $akHome/update-binary 3 1 $zip").exec()
                        if (result.isSuccess) {
                            log(context, "Kernel flashed successfully")
                            withContext (Dispatchers.Main) {
                                wasFlashed = true
                            }
                        } else {
                            log(context, "Failed to flash zip", shouldThrow = true)
                        }
                    } else {
                        log(context, "Failed to extract update-binary", shouldThrow = true)
                    }
                } else {
                    log(context, "Could not create temporary directory", shouldThrow = true)
                }
                zip.delete()
            } else {
                log(context, "No zip provided", shouldThrow = true)
            }
            if (wasSlotReset) {
                resetSlot()
            }
        }
    }
}
