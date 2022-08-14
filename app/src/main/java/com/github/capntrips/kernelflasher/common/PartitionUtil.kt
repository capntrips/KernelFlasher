package com.github.capntrips.kernelflasher.common

import android.content.Context
import com.github.capntrips.kernelflasher.common.types.partitions.FstabEntry
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

object PartitionUtil {
    val PartitionNames = listOf(
        "boot",
        "vbmeta",
        "dtbo",
        "vendor_boot",
        "vendor_dlkm",
        "init_boot",
        "recovery"
    )

    private var fileSystemManager: FileSystemManager? = null
    private var bootDevice: File? = null

    fun init(context: Context, fileSystemManager: FileSystemManager) {
        this.fileSystemManager = fileSystemManager
        val fstabEntry = findPartitionFstabEntry(context, "boot")
        if (fstabEntry != null) {
            bootDevice = File(fstabEntry.blkDevice).parentFile
        }
    }

    private fun findPartitionFstabEntry(context: Context, partitionName: String): FstabEntry? {
        val httools = File(context.filesDir, "httools_static")
        val result = Shell.cmd("$httools dump $partitionName").exec().out
        if (result.isNotEmpty()) {
            return Json.decodeFromString<FstabEntry>(result[0])
        }
        return null
    }

    fun isPartitionLogical(context: Context, partitionName: String): Boolean {
        return findPartitionFstabEntry(context, partitionName)?.fsMgrFlags?.logical == true
    }

    fun findPartitionBlockDevice(context: Context, partitionName: String, slotSuffix: String): SuFile? {
        var blockDevice: SuFile? = null
        val fstabEntry = findPartitionFstabEntry(context, partitionName)
        if (fstabEntry != null) {
            blockDevice = SuFile(fstabEntry.blkDevice)
        }
        if (blockDevice == null) {
            val siblingDevice = if (bootDevice != null) SuFile(bootDevice!!, partitionName) else null
            val physicalDevice = SuFile("/dev/block/by-name/$partitionName$slotSuffix")
            val logicalDevice = SuFile("/dev/block/mapper/$partitionName$slotSuffix")
            if (siblingDevice?.exists() == true) {
                blockDevice = physicalDevice
            } else if (physicalDevice.exists()) {
                blockDevice = physicalDevice
            } else if (logicalDevice.exists()) {
                blockDevice = logicalDevice
            }
        }
        return blockDevice
    }

    @Suppress("unused")
    fun partitionAvb(context: Context, partitionName: String): String {
        val httools = File(context.filesDir, "httools_static")
        val result = Shell.cmd("$httools avb $partitionName").exec().out
        return if (result.isNotEmpty()) result[0] else ""
    }
}
