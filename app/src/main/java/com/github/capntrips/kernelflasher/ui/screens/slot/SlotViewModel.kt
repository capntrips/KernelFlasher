package com.github.capntrips.kernelflasher.ui.screens.slot

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.common.extensions.ByteArray.toHex
import com.github.capntrips.kernelflasher.common.extensions.ExtendedFile.inputStream
import com.github.capntrips.kernelflasher.common.extensions.ExtendedFile.outputStream
import com.github.capntrips.kernelflasher.common.types.backups.Backup
import com.github.capntrips.kernelflasher.common.types.partitions.Partitions
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

class SlotViewModel(
    context: Context,
    private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>,
    val isActive: Boolean,
    val slotSuffix: String,
    private val boot: File,
    private val _backups: MutableMap<String, Backup>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/SlotState"
    }

    private var _sha1: String? = null
    var kernelVersion: String? = null
    var hasVendorDlkm: Boolean = false
    var isVendorDlkmMapped: Boolean = false
    var isVendorDlkmMounted: Boolean = false
    private val _flashOutput: SnapshotStateList<String> = mutableStateListOf()
    private val _wasFlashSuccess: MutableState<Boolean?> = mutableStateOf(null)
    private val _backupPartitions: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private var wasSlotReset: Boolean = false
    private var flashUri: Uri? = null
    private var flashFilename: String? = null
    private val hashAlgorithm: String = "SHA-256"
    private var inInit = true
    private var _error: String? = null

    @Suppress("PrivatePropertyName")
    private val STOCK_MAGISKBOOT = "/data/adb/magisk/magiskboot"
    private var magiskboot: String = STOCK_MAGISKBOOT

    val sha1: String
        get() = _sha1!!
    val flashOutput: List<String>
        get() = _flashOutput
    val uiPrintedOutput: List<String>
        get() = _flashOutput.filter { it.startsWith("ui_print") }.map{ it.substring("ui_print".length + 1) }
    val wasFlashSuccess: Boolean?
        get() = _wasFlashSuccess.value
    val backupPartitions: MutableMap<String, Boolean>
        get() = _backupPartitions
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
        // init magiskboot
        if (!File(STOCK_MAGISKBOOT).exists()) {
            magiskboot = context.filesDir.absolutePath + File.separator + "magiskboot"
        }

        Shell.cmd("$magiskboot unpack $boot").exec()

        val ramdisk = File(context.filesDir, "ramdisk.cpio")

        var vendorDlkm = PartitionUtil.findPartitionBlockDevice(context, "vendor_dlkm", slotSuffix)
        hasVendorDlkm = vendorDlkm != null
        if (hasVendorDlkm) {
            isVendorDlkmMapped = vendorDlkm?.exists() == true
            if (isVendorDlkmMapped) {
                isVendorDlkmMounted = isPartitionMounted(vendorDlkm!!)
                if (!isVendorDlkmMounted) {
                    vendorDlkm = fileSystemManager.getFile("/dev/block/mapper/vendor_dlkm-verity")
                    isVendorDlkmMounted = isPartitionMounted(vendorDlkm)
                }
            } else {
                isVendorDlkmMounted = false
            }
        }

        val magiskboot = fileSystemManager.getFile(magiskboot)
        if (magiskboot.exists()) {
            if (ramdisk.exists()) {
                when (Shell.cmd("$magiskboot cpio ramdisk.cpio test").exec().code) {
                    0 -> _sha1 = Shell.cmd("$magiskboot sha1 $boot").exec().out.firstOrNull()
                    1 -> _sha1 = Shell.cmd("$magiskboot cpio ramdisk.cpio sha1").exec().out.firstOrNull()
                    else -> log(context, "Invalid boot.img", shouldThrow = true)
                }
            } else {
                log(context, "Invalid boot.img", shouldThrow = true)
            }
            Shell.cmd("$magiskboot cleanup").exec()
        } else {
            log(context, "magiskboot is missing", shouldThrow = true)
        }

        PartitionUtil.AvailablePartitions.forEach { partitionName ->
            _backupPartitions[partitionName] = true
        }

        kernelVersion = null
        inInit = false
    }

    // TODO: use base class for common functions
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

    // TODO: use base class for common functions
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

    @Suppress("SameParameterValue")
    private fun uiPrint(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _flashOutput.add("ui_print $message")
            _flashOutput.add("      ui_print")
        }
    }

    // TODO: use base class for common functions
    private fun addMessage(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _flashOutput.add(message)
        }
    }

    private fun clearTmp(context: Context) {
        if (flashFilename != null) {
            val zip = File(context.filesDir, flashFilename!!)
            if (zip.exists()) {
                zip.delete()
            }
        }
    }

    @Suppress("FunctionName")
    private fun _clearFlash() {
        _flashOutput.clear()
        _wasFlashSuccess.value = null
    }

    fun clearFlash(context: Context) {
        _clearFlash()
        PartitionUtil.AvailablePartitions.forEach { partitionName ->
            _backupPartitions[partitionName] = true
        }
        launch {
            clearTmp(context)
        }
    }

    // TODO: use base class for common functions
    @SuppressLint("SdCardPath")
    fun saveLog(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val logName = if (navController.currentDestination!!.route!!.contains("ak3")) {
                "ak3"
            } else if (navController.currentDestination!!.route!! == "slot{slotSuffix}/backup") {
                "backup"
            } else {
                "flash"
            }
            val log = File("/sdcard/Download/$logName-log--$now.log")
            if (navController.currentDestination!!.route!!.contains("ak3")) {
                log.writeText(flashOutput.filter { !it.matches("""progress [\d.]* [\d.]*""".toRegex()) }.joinToString("\n").replace("""ui_print (.*)\n {6}ui_print""".toRegex(), "$1"))
            } else {
                log.writeText(flashOutput.joinToString("\n"))
            }
            if (log.exists()) {
                log(context, "Saved $logName log to $log")
            } else {
                log(context, "Failed to save $log", shouldThrow = true)
            }
        }
    }

    @Suppress("FunctionName", "SameParameterValue")
    private fun _getKernel(context: Context) {
        Shell.cmd("$magiskboot unpack $boot").exec()
        val kernel = File(context.filesDir, "kernel")
        if (kernel.exists()) {
            val result = Shell.cmd("strings kernel | grep -E -m1 'Linux version.*#' | cut -d\\  -f3-").exec().out
            if (result.isNotEmpty()) {
                kernelVersion = result[0].replace("""\(.+\)""".toRegex(), "").replace("""\s+""".toRegex(), " ")
            }
        }
        Shell.cmd("$magiskboot cleanup").exec()
    }

    fun getKernel(context: Context) {
        launch {
            _getKernel(context)
        }
    }

    private fun isPartitionMounted(partition: File): Boolean {
        @Suppress("LiftReturnOrAssignment")
        if (partition.exists()) {
            val dmPath = Shell.cmd("readlink -f $partition").exec().out[0]
            val mounts = Shell.cmd("mount | grep -w $dmPath").exec().out
            return mounts.isNotEmpty()
        } else {
            return false
        }
    }

    fun unmountVendorDlkm(context: Context) {
        launch {
            val httools = File(context.filesDir, "httools_static")
            Shell.cmd("$httools umount vendor_dlkm").exec()
            refresh(context)
        }
    }

    fun mountVendorDlkm(context: Context) {
        launch {
            val httools = File(context.filesDir, "httools_static")
            Shell.cmd("$httools mount vendor_dlkm").exec()
            refresh(context)
        }
    }

    fun unmapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools_static")
            val mapperDir = "/dev/block/mapper"
            val vendorDlkm = fileSystemManager.getFile(mapperDir, "vendor_dlkm$slotSuffix")
            if (vendorDlkm.exists()) {
                val vendorDlkmVerity = fileSystemManager.getFile(mapperDir, "vendor_dlkm-verity")
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
            val lptools = File(context.filesDir, "lptools_static")
            Shell.cmd("$lptools map vendor_dlkm$slotSuffix").exec()
            refresh(context)
        }
    }

    private fun backupPartition(partition: ExtendedFile, destination: ExtendedFile): String? {
        if (partition.exists()) {
            val messageDigest = MessageDigest.getInstance(hashAlgorithm)
            partition.inputStream().use { inputStream ->
                destination.outputStream().use { outputStream ->
                    DigestOutputStream(outputStream, messageDigest).use { digestOutputStream ->
                        inputStream.copyTo(digestOutputStream)
                    }
                }
            }
            return messageDigest.digest().toHex()
        }
        return null
    }

    private fun backupPartitions(context: Context, destination: ExtendedFile): Partitions? {
        val partitions = HashMap<String, String>()
        for (partitionName in PartitionUtil.PartitionNames) {
            if (_backupPartitions[partitionName] == true) {
                val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
                if (blockDevice != null) {
                    addMessage("Saving $partitionName")
                    val hash = backupPartition(blockDevice, destination.getChildFile("$partitionName.img"))
                    if (hash != null) {
                        partitions[partitionName] = hash
                    }
                }
            }
        }
        if (partitions.isNotEmpty()) {
            return Partitions.from(partitions)
        }
        return null
    }

    private fun createBackupDir(context: Context, now: String): ExtendedFile {
        @SuppressLint("SdCardPath")
        val externalDir = fileSystemManager.getFile("/sdcard/KernelFlasher")
        if (!externalDir.exists()) {
            if (!externalDir.mkdir()) {
                log(context, "Failed to create KernelFlasher dir on /sdcard", shouldThrow = true)
            }
        }
        val backupsDir = externalDir.getChildFile("backups")
        if (!backupsDir.exists()) {
            if (!backupsDir.mkdir()) {
                log(context, "Failed to create backups dir", shouldThrow = true)
            }
        }
        val backupDir = backupsDir.getChildFile(now)
        if (backupDir.exists()) {
            log(context, "Backup $now already exists", shouldThrow = true)
        } else {
            if (!backupDir.mkdir()) {
                log(context, "Failed to create backup dir", shouldThrow = true)
            }
        }
        return backupDir
    }

    fun backup(context: Context) {
        launch {
            _clearFlash()
            val currentKernelVersion = if (kernelVersion != null) {
                kernelVersion
            } else if (isActive) {
                System.getProperty("os.version")!!
            } else {
                _getKernel(context)
                kernelVersion
            }
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val backupDir = createBackupDir(context, now)
            addMessage("Saving backup $now")
            val hashes = backupPartitions(context, backupDir)
            if (hashes == null) {
                log(context, "No partitions saved", shouldThrow = true)
            }
            val jsonFile = backupDir.getChildFile("backup.json")
            val backup = Backup(now, "raw", currentKernelVersion!!, sha1, null, hashes, hashAlgorithm)
            val indentedJson = Json { prettyPrint = true }
            jsonFile.outputStream().use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
            _backups[now] = backup
            addMessage("Backup $now saved")
            _wasFlashSuccess.value = true
        }
    }

    fun backupZip(context: Context, callback: () -> Unit) {
        launch {
            val source = context.contentResolver.openInputStream(flashUri!!)
            if (source != null) {
                _getKernel(context)
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
                val backupDir = createBackupDir(context, now)
                val jsonFile = backupDir.getChildFile("backup.json")
                val backup = Backup(now, "ak3", kernelVersion!!, null, flashFilename)
                val indentedJson = Json { prettyPrint = true }
                jsonFile.outputStream().use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
                val destination = backupDir.getChildFile(flashFilename!!)
                source.use { inputStream ->
                    destination.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                _backups[now] = backup
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

    @Suppress("FunctionName")
    private suspend fun _checkZip(context: Context, zip: File, callback: (() -> Unit)? = null) {
        if (zip.exists()) {
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                val zipFile = ZipFile(zip)
                zipFile.use { z ->
                    if (z.getEntry("anykernel.sh") == null) {
                        log(context, "Invalid AK3 zip", shouldThrow = true)
                    }
                    withContext (Dispatchers.Main) {
                        callback?.invoke()
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

    @Suppress("FunctionName")
    private fun _copyFile(context: Context, currentBackup: String, filename: String) {
        flashUri = null
        flashFilename = filename
        @SuppressLint("SdCardPath")
        val externalDir = File("/sdcard/KernelFlasher")
        val backupsDir = fileSystemManager.getFile("$externalDir/backups")
        val backupDir = backupsDir.getChildFile(currentBackup)
        if (!backupDir.exists()) {
            log(context, "Backup $currentBackup does not exists", shouldThrow = true)
        }
        val source = backupDir.getChildFile(flashFilename!!)
        val zip = File(context.filesDir, flashFilename!!)
        source.newInputStream().use { inputStream ->
            zip.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    @Suppress("FunctionName")
    private fun _copyFile(context: Context, uri: Uri) {
        flashUri = uri
        flashFilename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return@use cursor.getString(name)
        } ?: "ak3.zip"
        val source = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, flashFilename!!)
        source.use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
        }
    }

    @Suppress("FunctionName")
    private suspend fun _flashAk3(context: Context) {
        if (!isActive) {
            resetSlot()
        }
        val zip = File(context.filesDir.canonicalPath, flashFilename!!)
        _checkZip(context, zip)
        try {
            if (zip.exists()) {
                _wasFlashSuccess.value = false
                val files = File(context.filesDir.canonicalPath)
                val flashScript = File(files, "flash_ak3.sh")
                val result = Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR).build().newJob().add("F=$files Z=\"$zip\" /system/bin/sh $flashScript").to(flashOutput).exec()
                if (result.isSuccess) {
                    log(context, "Kernel flashed successfully")
                    _wasFlashSuccess.value = true
                } else {
                    log(context, "Failed to flash zip", shouldThrow = false)
                }
                clearTmp(context)
            } else {
                log(context, "AK3 zip is missing", shouldThrow = true)
            }
        } catch (e: Exception) {
            clearFlash(context)
            throw e
        } finally {
            uiPrint("")
            if (wasSlotReset) {
                resetSlot()
            }
        }
    }

    fun flashAk3(context: Context, currentBackup: String, filename: String) {
        launch {
            _clearFlash()
            _copyFile(context, currentBackup, filename)
            _flashAk3(context)
        }
    }

    fun flashAk3(context: Context, uri: Uri) {
        launch {
            _clearFlash()
            _copyFile(context, uri)
            _flashAk3(context)
        }
    }

    fun flashImage(context: Context, uri: Uri, partitionName: String) {
        launch {
            _clearFlash()
            addMessage("Copying image ...")
            _copyFile(context, uri)
            if (!isActive) {
                resetSlot()
            }
            val image = fileSystemManager.getFile(context.filesDir, flashFilename!!)
            try {
                if (image.exists()) {
                    addMessage("Copied $flashFilename")
                    _wasFlashSuccess.value = false
                    addMessage("Flashing $flashFilename to $partitionName ...")
                    val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
                    if (blockDevice != null && blockDevice.exists()) {
                        if (PartitionUtil.isPartitionLogical(context, partitionName)) {
                            PartitionUtil.flashLogicalPartition(context, image, blockDevice, partitionName, slotSuffix, hashAlgorithm) { message ->
                                addMessage(message)
                            }
                        } else {
                            PartitionUtil.flashBlockDevice(image, blockDevice, hashAlgorithm)
                        }
                    } else {
                        log(context, "Partition $partitionName was not found", shouldThrow = true)
                    }
                    addMessage("Flashed $flashFilename to $partitionName")
                    addMessage("Cleaning up ...")
                    clearTmp(context)
                    addMessage("Done.")
                    _wasFlashSuccess.value = true
                } else {
                    log(context, "Partition image is missing", shouldThrow = true)
                }
            } catch (e: Exception) {
                clearFlash(context)
                throw e
            } finally {
                addMessage("")
                if (wasSlotReset) {
                    resetSlot()
                }
            }
        }
    }
}
