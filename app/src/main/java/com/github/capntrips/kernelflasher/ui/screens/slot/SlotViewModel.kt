package com.github.capntrips.kernelflasher.ui.screens.slot

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.zip.ZipFile

class SlotViewModel(
    context: Context,
    val isActive: Boolean,
    val slotSuffix: String,
    private val boot: File,
    private val _isRefreshing: MutableState<Boolean>,
    private val navController: NavController,
    private val isImage: Boolean = false,
    private val backups: HashMap<String, Properties>? = null
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/SlotState"
    }

    var _sha1: String? = null
    var kernelVersion: String? = null
    var hasVendorDlkm: Boolean = false
    var isVendorDlkmMounted: Boolean = false
    @Suppress("PropertyName")
    private val _flashOutput: SnapshotStateList<String> = mutableStateListOf()
    private val _wasFlashSuccess: MutableState<Boolean?> = mutableStateOf(null)
    private var wasSlotReset: Boolean = false
    private var isFlashing: Boolean = false
    private var inInit = true
    private var _error: String? = null

    val sha1: String
        get() = _sha1!!
    val flashOutput: List<String>
        get() = _flashOutput
    val wasFlashSuccess: Boolean?
        get() = _wasFlashSuccess.value
    val isRefreshing: Boolean
        get() = _isRefreshing.value
    val hasError: Boolean
        get() = _error != null
    val error: String
        get() = _error!!

    init {
        refresh(context)
    }

    fun refresh(context: Context) {
        Shell.cmd("/data/adb/magisk/magiskboot unpack $boot").exec()

        val ramdisk = File(context.filesDir, "ramdisk.cpio")

        val mapperDir = "/dev/block/mapper"
        var vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
        hasVendorDlkm = vendorDlkm.exists()
        if (hasVendorDlkm) {
            isVendorDlkmMounted = isPartitionMounted(vendorDlkm)
            if (!isVendorDlkmMounted) {
                vendorDlkm = SuFile(mapperDir, "vendor_dlkm-verity")
                isVendorDlkmMounted = isPartitionMounted(vendorDlkm)
            }
        } else {
            isVendorDlkmMounted = false
        }

        val magiskboot = SuFile("/data/adb/magisk/magiskboot")
        if (magiskboot.exists()) {
            if (!isImage || ramdisk.exists()) {
                when (Shell.cmd("/data/adb/magisk/magiskboot cpio ramdisk.cpio test").exec().code) {
                    0 -> _sha1 = Shell.cmd("/data/adb/magisk/magiskboot sha1 $boot").exec().out[0]
                    1 -> _sha1 = Shell.cmd("/data/adb/magisk/magiskboot cpio ramdisk.cpio sha1").exec().out[0]
                    else -> log(context, "Invalid boot.img", shouldThrow = true)
                }
            } else {
                log(context, "Invalid boot.img", shouldThrow = true)
            }
            Shell.cmd("/data/adb/magisk/magiskboot cleanup").exec()
        } else {
            log(context, "magiskboot is missing", shouldThrow = true)
        }

        kernelVersion = null
        inInit = false
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
            if (inInit) {
                _error = message
            } else {
                throw Exception(message)
            }
        }
    }

    private fun clearTmp(context: Context) {
        val zip = File(context.filesDir, "ak3.zip")
        val akHome = File(context.filesDir, "akhome")
        if (zip.exists()) {
            zip.delete()
        }
        if (akHome.exists()) {
            Shell.cmd("rm -r $akHome").exec()
        }
    }

    @Suppress("FunctionName")
    private fun _clearFlash() {
        _flashOutput.clear()
        _wasFlashSuccess.value = null
    }

    fun clearFlash(context: Context) {
        _clearFlash()
        launch {
            clearTmp(context)
        }
    }

    @SuppressLint("SdCardPath")
    fun saveLog(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val log = SuFile("/sdcard/Download/ak3-log--$now.log")
            log.writeText(flashOutput.filter { !it.matches("""progress [\d.]* [\d.]*""".toRegex()) }.joinToString("\n").replace("""ui_print (.*)\n {6}ui_print""".toRegex(), "$1"))
            if (log.exists()) {
                log(context, "Saved AK3 log to $log")
            } else {
                log(context, "Failed to save $log", shouldThrow = true)
            }
        }
    }

    fun reboot(context: Context? = null, clear: Boolean = false) {
        if (clear) {
            _clearFlash()
        }
        launch {
            if (clear) {
                clearTmp(context!!)
            }
            Shell.cmd("reboot").exec()
        }
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

    fun getKernel(context: Context) {
        launch {
            _getKernel(context)
        }
    }

    fun isPartitionMounted(partition: File): Boolean {
        @Suppress("LiftReturnOrAssignment")
        if (partition.exists()) {
            val dmPath = Shell.cmd("readlink -f $partition").exec().out[0]
            val mounts = Shell.cmd("mount | grep $dmPath").exec().out
            return mounts.isNotEmpty();
        } else {
            return false;
        }
    }

    fun unmountPartition(partition: File) {
        val dmPath = Shell.cmd("readlink -f $partition").exec().out[0]
        Shell.cmd("umount $dmPath").exec()
    }

    fun unmountVendorDlkm(context: Context) {
        launch {
            val mapperDir = "/dev/block/mapper"
            var vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
            if (vendorDlkm.exists()) {
                if (isPartitionMounted(vendorDlkm)) {
                    unmountPartition(vendorDlkm)
                } else {
                    vendorDlkm = SuFile(mapperDir, "vendor_dlkm-verity")
                    if (isPartitionMounted(vendorDlkm)) {
                        unmountPartition(vendorDlkm)
                    }
                }
            }
            refresh(context)
        }
    }

    fun mountVendorDlkm(context: Context) {
        launch {
            val mapperDir = "/dev/block/mapper"
            val vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
            val dmPath = Shell.cmd("readlink -f $vendorDlkm").exec().out[0]
            Shell.cmd("mount -t ext4 -o ro,barrier=1 $dmPath /vendor_dlkm").exec()
            refresh(context)
        }
    }

    fun unmapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools")
            val mapperDir = "/dev/block/mapper"
            val vendorDlkm = SuFile(mapperDir, "vendor_dlkm$slotSuffix")
            if (vendorDlkm.exists()) {
                val vendorDlkmVerity = SuFile(mapperDir, "vendor_dlkm-verity")
                if (vendorDlkmVerity.exists()) {
                    Shell.cmd("$lptools unmap vendor_dlkm-verity").exec()
                } else {
                    Shell.cmd("$lptools unmap vendor_dlkm$slotSuffix").exec()
                }
            }
            refresh(context)
        }
    }

    fun mapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools")
            Shell.cmd("$lptools map vendor_dlkm$slotSuffix").exec()
            refresh(context)
        }
    }

    private fun backupPartition(context: Context, partition: File, destination: File, isOptional: Boolean = false) {
        if (partition.exists()) {
            SuFileInputStream.open(partition).use { inputStream ->
                FileOutputStream(destination).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
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

    private fun createBackupDir(context: Context, now: String): File {
        val externalDir = context.getExternalFilesDir(null)
        val backupsDir = File(externalDir, "backups")
        if (!backupsDir.exists()) {
            if (!backupsDir.mkdir()) {
                log(context, "Failed to create backups dir", shouldThrow = true)
            }
        }
        val backupDir = File(backupsDir, now)
        if (backupDir.exists()) {
            log(context, "Backup $now already exists", shouldThrow = true)
        } else {
            if (!backupDir.mkdir()) {
                log(context, "Failed to create backup dir", shouldThrow = true)
            }
        }
        return backupDir
    }

    fun backup(context: Context, callback: () -> Unit) {
        launch {
            val props = Properties()
            props.setProperty("type", "raw")
            props.setProperty("sha1", sha1)
            if (kernelVersion != null) {
                props.setProperty("kernel", kernelVersion)
            } else if (isActive) {
                props.setProperty("kernel", System.getProperty("os.version")!!)
            } else {
                _getKernel(context)
                props.setProperty("kernel", kernelVersion!!)
            }
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val backupDir = createBackupDir(context, now)
            val propFile = File(backupDir, "backup.prop")
            @Suppress("BlockingMethodInNonBlockingContext")
            props.store(propFile.outputStream(), props.getProperty("kernel"))
            backupPhysicalPartition(context, "boot", backupDir)
            backupLogicalPartition(context, "vendor_dlkm", backupDir, true)
            backupPhysicalPartition(context, "vendor_boot", backupDir)
            backupPhysicalPartition(context, "dtbo", backupDir)
            backupPhysicalPartition(context, "vbmeta", backupDir)
            backups?.put(now, props)
            withContext (Dispatchers.Main) {
                callback.invoke()
            }
        }
    }

    fun backupZip(context: Context, callback: () -> Unit) {
        launch {
            val zip = File(context.filesDir, "ak3.zip")
            if (zip.exists()) {
                val props = Properties()
                props.setProperty("type", "ak3")
                _getKernel(context)
                props.setProperty("kernel", kernelVersion!!)
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
                val backupDir = createBackupDir(context, now)
                val propFile = File(backupDir, "backup.prop")
                @Suppress("BlockingMethodInNonBlockingContext")
                props.store(propFile.outputStream(), props.getProperty("kernel"))
                val destination = File(backupDir, "ak3.zip")
                zip.inputStream().use { inputStream ->
                    destination.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                backups?.put(now, props)
                withContext (Dispatchers.Main) {
                    callback.invoke()
                }
            } else {
                log(context, "AK3 zip is missing", shouldThrow = true)
            }
        }
    }

    private fun resetSlot() {
        val activeSlotSuffix = Shell.cmd("getprop ro.boot.slot_suffix").exec().out[0]
        val newSlot = if (activeSlotSuffix == "_a") "_b" else "_a"
        Shell.cmd("magisk resetprop -n ro.boot.slot_suffix $newSlot").exec()
        wasSlotReset = !wasSlotReset
    }

    @Suppress("BlockingMethodInNonBlockingContext", "FunctionName")
    suspend fun _checkZip(context: Context, zip: File, callback: () -> Unit) {
        if (zip.exists()) {
            try {
                val zipFile = ZipFile(zip)
                zipFile.use { z ->
                    if (z.getEntry("anykernel.sh") == null) {
                        log(context, "Invalid AK3 zip", shouldThrow = true)
                    }
                    withContext (Dispatchers.Main) {
                        callback.invoke()
                    }
                }
            } catch (e: Exception) {
                zip.delete()
                throw e
            }
        } else {
            log(context, "Failed to save zip", shouldThrow = true)
        }
    }

    fun checkZip(context: Context, currentBackup: String, callback: () -> Unit) {
        launch {
            val externalDir = context.getExternalFilesDir(null)
            val backupsDir = File(externalDir, "backups")
            val backupDir = File(backupsDir, currentBackup)
            val source = File(backupDir, "ak3.zip")
            val zip = File(context.filesDir, "ak3.zip")
            source.inputStream().use { inputStream ->
                zip.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            _checkZip(context, zip, callback)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun checkZip(context: Context, uri: Uri, callback: () -> Unit) {
        launch {
            val source = context.contentResolver.openInputStream(uri)
            val zip = File(context.filesDir, "ak3.zip")
            source.use { inputStream ->
                zip.outputStream().use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
            }
            _checkZip(context, zip, callback)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun flash(context: Context) {
        if (!isFlashing) {
            isFlashing = true
            launch {
                if (!isActive) {
                    resetSlot()
                }
                val zip = File(context.filesDir, "ak3.zip")
                val akHome = File(context.filesDir, "akhome")
                try {
                    if (zip.exists()) {
                        akHome.mkdir()
                        _wasFlashSuccess.value = false
                        if (akHome.exists()) {
                            val updateBinary = File(akHome, "update-binary")
                            Shell.cmd("unzip -p $zip META-INF/com/google/android/update-binary > $akHome/update-binary").exec()
                            if (updateBinary.exists()) {
                                val result = Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR).build().newJob().add("AKHOME=$akHome \$SHELL $akHome/update-binary 3 1 $zip").to(flashOutput).exec()
                                if (result.isSuccess) {
                                    log(context, "Kernel flashed successfully")
                                    _wasFlashSuccess.value = true
                                } else {
                                    log(context, "Failed to flash zip", shouldThrow = false)
                                }
                            } else {
                                log(context, "Failed to extract update-binary", shouldThrow = true)
                            }
                        } else {
                            log(context, "Failed to create temporary folder", shouldThrow = true)
                        }
                    } else {
                        log(context, "AK3 zip is missing", shouldThrow = true)
                    }
                } catch (e: Exception) {
                    clearFlash(context)
                    throw e
                } finally {
                    _flashOutput.add("ui_print ")
                    _flashOutput.add("      ui_print")
                    isFlashing = false
                    if (wasSlotReset) {
                        resetSlot()
                    }
                }
            }
        } else {
            Log.d(TAG, "Already flashing")
        }
    }
}
