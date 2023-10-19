package com.github.capntrips.kernelflasher.common

import android.content.Context
import com.github.capntrips.kernelflasher.common.extensions.ByteArray.toHex
import com.github.capntrips.kernelflasher.common.types.partitions.FstabEntry
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.serialization.json.Json
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest

object PartitionUtil {
    val PartitionNames = listOf(
        "boot",
        "dtbo",
        "init_boot",
        "recovery",
        "system_dlkm",
        "vbmeta",
        "vendor_boot",
        "vendor_dlkm",
        "vendor_kernel_boot"
    )

    val AvailablePartitions = mutableListOf<String>()

    private var fileSystemManager: FileSystemManager? = null
    private var bootParent: File? = null

    fun init(context: Context, fileSystemManager: FileSystemManager) {
        this.fileSystemManager = fileSystemManager
        val fstabEntry = findPartitionFstabEntry(context, "boot")
        if (fstabEntry != null) {
            bootParent = File(fstabEntry.blkDevice).parentFile
        }
        val activeSlotSuffix = Shell.cmd("getprop ro.boot.slot_suffix").exec().out[0]
        for (partitionName in PartitionNames) {
            val blockDevice = findPartitionBlockDevice(context, partitionName, activeSlotSuffix)
            if (blockDevice != null && blockDevice.exists()) {
                AvailablePartitions.add(partitionName)
            }
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

    fun findPartitionBlockDevice(context: Context, partitionName: String, slotSuffix: String): ExtendedFile? {
        var blockDevice: ExtendedFile? = null
        val fstabEntry = findPartitionFstabEntry(context, partitionName)
        if (fstabEntry != null) {
            if (fstabEntry.fsMgrFlags?.logical == true) {
                if (fstabEntry.logicalPartitionName == "$partitionName$slotSuffix") {
                    blockDevice = fileSystemManager!!.getFile(fstabEntry.blkDevice)
                }
            } else {
                blockDevice = fileSystemManager!!.getFile(fstabEntry.blkDevice)
                if (blockDevice.name != "$partitionName$slotSuffix") {
                    blockDevice = fileSystemManager!!.getFile(blockDevice.parentFile, "$partitionName$slotSuffix")
                }
            }
        }
        if (blockDevice == null || !blockDevice.exists()) {
            val siblingDevice = if (bootParent != null) fileSystemManager!!.getFile(bootParent!!, "$partitionName$slotSuffix") else null
            val physicalDevice = fileSystemManager!!.getFile("/dev/block/by-name/$partitionName$slotSuffix")
            val logicalDevice = fileSystemManager!!.getFile("/dev/block/mapper/$partitionName$slotSuffix")
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

    fun flashBlockDevice(image: ExtendedFile, blockDevice: ExtendedFile, hashAlgorithm: String): String {
        val partitionSize = Shell.cmd("wc -c < $blockDevice").exec().out[0].toUInt()
        val imageSize = Shell.cmd("wc -c < $image").exec().out[0].toUInt()
        if (partitionSize < imageSize) {
            throw Error("Partition ${blockDevice.name} is smaller than image")
        }
        if (partitionSize > imageSize) {
            Shell.cmd("dd bs=4096 if=/dev/zero of=$blockDevice").exec()
        }
        val messageDigest = MessageDigest.getInstance(hashAlgorithm)
        image.newInputStream().use { inputStream ->
            blockDevice.newOutputStream().use { outputStream ->
                DigestOutputStream(outputStream, messageDigest).use { digestOutputStream ->
                    inputStream.copyTo(digestOutputStream)
                }
            }
        }
        return messageDigest.digest().toHex()
    }

    @Suppress("SameParameterValue")
    fun flashLogicalPartition(context: Context, image: ExtendedFile, blockDevice: ExtendedFile, partitionName: String, slotSuffix: String, hashAlgorithm: String, addMessage: (message: String) -> Unit): String {
        val sourceFileSize = Shell.cmd("wc -c < $image").exec().out[0].toUInt()
        val lptools = File(context.filesDir, "lptools_static")
        Shell.cmd("$lptools remove ${partitionName}_kf").exec()
        if (Shell.cmd("$lptools create ${partitionName}_kf $sourceFileSize").exec().isSuccess) {
            if (Shell.cmd("$lptools unmap ${partitionName}_kf").exec().isSuccess) {
                if (Shell.cmd("$lptools map ${partitionName}_kf").exec().isSuccess) {
                    val temporaryBlockDevice = fileSystemManager!!.getFile("/dev/block/mapper/${partitionName}_kf")
                    val hash = flashBlockDevice(image, temporaryBlockDevice, hashAlgorithm)
                    if (Shell.cmd("$lptools replace ${partitionName}_kf $partitionName$slotSuffix").exec().isSuccess) {
                        return hash
                    } else {
                        throw Error("Replacing $partitionName$slotSuffix failed")
                    }
                } else {
                    throw Error("Remapping ${partitionName}_kf failed")
                }
            } else {
                throw Error("Unmapping ${partitionName}_kf failed")
            }
        } else {
            addMessage.invoke("Creating ${partitionName}_kf failed. Attempting to resize $partitionName$slotSuffix ...")
            val httools = File(context.filesDir, "httools_static")
            if (Shell.cmd("$httools umount $partitionName").exec().isSuccess) {
                val verityBlockDevice = blockDevice.parentFile!!.getChildFile("${partitionName}-verity")
                if (verityBlockDevice.exists()) {
                    if (!Shell.cmd("$lptools unmap ${partitionName}-verity").exec().isSuccess) {
                        throw Error("Unmapping ${partitionName}-verity failed")
                    }
                }
                if (Shell.cmd("$lptools unmap $partitionName$slotSuffix").exec().isSuccess) {
                    if (Shell.cmd("$lptools resize $partitionName$slotSuffix \$(wc -c < $image)").exec().isSuccess) {
                        if (Shell.cmd("$lptools map $partitionName$slotSuffix").exec().isSuccess) {
                            val hash = flashBlockDevice(image, blockDevice, hashAlgorithm)
                            if (Shell.cmd("$httools mount $partitionName").exec().isSuccess) {
                                return hash
                            } else {
                                throw Error("Mounting $partitionName failed")
                            }
                        } else {
                            throw Error("Remapping $partitionName$slotSuffix failed")
                        }
                    } else {
                        throw Error("Resizing $partitionName$slotSuffix failed")
                    }
                } else {
                    throw Error("Unmapping $partitionName$slotSuffix failed")
                }
            } else {
                throw Error("Unmounting $partitionName failed")
            }
        }
    }
}
