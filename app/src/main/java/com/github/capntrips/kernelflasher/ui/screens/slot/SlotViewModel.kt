package com.github.capntrips.kernelflasher.ui.screens.slot

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.common.extensions.ByteArray.toHex
import com.github.capntrips.kernelflasher.common.types.backups.Backup
import com.github.capntrips.kernelflasher.common.types.partitions.Partitions
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
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
    @Suppress("unused") private val fileSystemManager: FileSystemManager,
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

    @Suppress("PropertyName")
    private var _sha1: String? = null
    var kernelVersion: String? = null
    var hasVendorDlkm: Boolean = false
    var isVendorDlkmMapped: Boolean = false
    var isVendorDlkmMounted: Boolean = false
    @Suppress("PropertyName")
    private val _flashOutput: SnapshotStateList<String> = mutableStateListOf()
    private val _wasFlashSuccess: MutableState<Boolean?> = mutableStateOf(null)
    private var wasSlotReset: Boolean = false
    private var isFlashing: Boolean = false
    private var flashUri: Uri? = null
    private var flashFilename: String? = null
    private val hashAlgorithm: String = "SHA-256"
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

        var vendorDlkm = PartitionUtil.findPartitionBlockDevice(context, "vendor_dlkm", slotSuffix)
        hasVendorDlkm = vendorDlkm != null
        if (hasVendorDlkm) {
            isVendorDlkmMapped = vendorDlkm?.exists() == true
            if (isVendorDlkmMapped) {
                isVendorDlkmMounted = isPartitionMounted(vendorDlkm!!)
                if (!isVendorDlkmMounted) {
                    vendorDlkm = SuFile("/dev/block/mapper/vendor_dlkm-verity")
                    isVendorDlkmMounted = isPartitionMounted(vendorDlkm)
                }
            } else {
                isVendorDlkmMounted = false
            }
        }

        val magiskboot = SuFile("/data/adb/magisk/magiskboot")
        if (magiskboot.exists()) {
            if (ramdisk.exists()) {
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
        val zip = File(context.filesDir, flashFilename!!)
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
            val log = File("/sdcard/Download/ak3-log--$now.log")
            log.writeText(flashOutput.filter { !it.matches("""progress [\d.]* [\d.]*""".toRegex()) }.joinToString("\n").replace("""ui_print (.*)\n {6}ui_print""".toRegex(), "$1"))
            if (log.exists()) {
                log(context, "Saved AK3 log to $log")
            } else {
                log(context, "Failed to save $log", shouldThrow = true)
            }
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

    private fun isPartitionMounted(partition: File): Boolean {
        @Suppress("LiftReturnOrAssignment")
        if (partition.exists()) {
            val dmPath = Shell.cmd("readlink -f $partition").exec().out[0]
            val mounts = Shell.cmd("mount | grep $dmPath").exec().out
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
            val lptools = File(context.filesDir, "lptools_static")
            Shell.cmd("$lptools map vendor_dlkm$slotSuffix").exec()
            refresh(context)
        }
    }

    private fun backupPartition(partition: SuFile, destination: File): String? {
        if (partition.exists()) {
            val messageDigest = MessageDigest.getInstance(hashAlgorithm)
            SuFileInputStream.open(partition).use { inputStream ->
                SuFileOutputStream.open(destination).use { outputStream ->
                    DigestOutputStream(outputStream, messageDigest).use { digestOutputStream ->
                        inputStream.copyTo(digestOutputStream)
                    }
                }
            }
            return messageDigest.digest().toHex()
        }
        return null
    }

    private fun backupPartitions(context: Context, destination: File): Partitions {
        val partitions = HashMap<String, String>()
        for (partitionName in PartitionUtil.PartitionNames) {
            val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
            if (blockDevice != null) {
                val hash = backupPartition(blockDevice, File(destination, "$partitionName.img"))
                if (hash != null) {
                    partitions[partitionName] = hash
                }
            }
        }
        return Partitions.from(partitions)
    }

    private fun createBackupDir(context: Context, now: String): File {
        @SuppressLint("SdCardPath")
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
        val backupDir = SuFile(backupsDir, now)
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
            val hashes = backupPartitions(context, backupDir)
            val jsonFile = File(backupDir, "backup.json")
            val backup = Backup(now, "raw", currentKernelVersion!!, sha1, null, hashes, hashAlgorithm)
            val indentedJson = Json { prettyPrint = true }
            @Suppress("BlockingMethodInNonBlockingContext")
            SuFileOutputStream.open(jsonFile).use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
            _backups[now] = backup
            withContext (Dispatchers.Main) {
                callback.invoke()
            }
        }
    }

    fun backupZip(context: Context, callback: () -> Unit) {
        launch {
            @Suppress("BlockingMethodInNonBlockingContext")
            val source = context.contentResolver.openInputStream(flashUri!!)
            if (source != null) {
                _getKernel(context)
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
                val backupDir = createBackupDir(context, now)
                val jsonFile = File(backupDir, "backup.json")
                val backup = Backup(now, "ak3", kernelVersion!!, null, flashFilename)
                val indentedJson = Json { prettyPrint = true }
                @Suppress("BlockingMethodInNonBlockingContext")
                SuFileOutputStream.open(jsonFile).use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
                val destination = File(backupDir, flashFilename!!)
                source.use { inputStream ->
                    @Suppress("BlockingMethodInNonBlockingContext")
                    SuFileOutputStream.open(destination).use { outputStream ->
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
    suspend fun _checkZip(context: Context, zip: File, callback: () -> Unit) {
        if (zip.exists()) {
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
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

    fun checkZip(context: Context, currentBackup: String, filename: String, callback: () -> Unit) {
        launch {
            flashUri = null
            flashFilename = filename
            @SuppressLint("SdCardPath")
            val externalDir = File("/sdcard/KernelFlasher")
            val backupsDir = fileSystemManager.getFile("$externalDir/backups")
            val backupDir = backupsDir.getChildFile(currentBackup)
            if (!backupDir.exists()) {
                log(context, "Backup $currentBackup does not exists", shouldThrow = true)
                return@launch
            }
            val source = backupDir.getChildFile(flashFilename!!)
            val zip = File(context.filesDir, flashFilename!!)
            @Suppress("BlockingMethodInNonBlockingContext")
            source.newInputStream().use { inputStream ->
                @Suppress("BlockingMethodInNonBlockingContext")
                zip.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            _checkZip(context, zip, callback)
        }
    }

    fun checkZip(context: Context, uri: Uri, callback: () -> Unit) {
        launch {
            flashUri = uri
            flashFilename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                return@use cursor.getString(name)
            } ?: "ak3.zip"
            @Suppress("BlockingMethodInNonBlockingContext")
            val source = context.contentResolver.openInputStream(uri)
            val zip = File(context.filesDir, flashFilename!!)
            source.use { inputStream ->
                zip.outputStream().use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
            }
            _checkZip(context, zip, callback)
        }
    }

    fun flash(context: Context) {
        if (!isFlashing) {
            isFlashing = true
            launch {
                if (!isActive) {
                    resetSlot()
                }
                val zip = File(context.filesDir, flashFilename!!)
                val akHome = File(context.filesDir, "akhome")
                try {
                    if (zip.exists()) {
                        akHome.mkdir()
                        _wasFlashSuccess.value = false
                        if (akHome.exists()) {
                            val updateBinary = File(akHome, "update-binary")
                            Shell.cmd("unzip -p \"$zip\" META-INF/com/google/android/update-binary > $akHome/update-binary").exec()
                            if (updateBinary.exists()) {
                                val result = Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR).build().newJob().add("AKHOME=$akHome \$SHELL $akHome/update-binary 3 1 \"$zip\"").to(flashOutput).exec()
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
                        clearTmp(context)
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
