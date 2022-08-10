package com.github.capntrips.kernelflasher.common.types

import kotlinx.serialization.Serializable

@Serializable
data class FstabEntry(
    val blkDevice: String,
    val mountPoint: String,
    val fsType: String,
    val logicalPartitionName: String? = null,
    val avb: Boolean = false,
    val vbmetaPartition: String? = null,
    val avbKeys: String? = null,
    val fsMgrFlags: FsMgrFlags? = null
)
