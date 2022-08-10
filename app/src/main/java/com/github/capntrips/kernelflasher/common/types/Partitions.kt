package com.github.capntrips.kernelflasher.common.types

import kotlinx.serialization.Serializable

@Serializable
data class Partitions(
    val boot: String? = null,
    val vbmeta: String? = null,
    val dtbo: String? = null,
    val vendor_boot: String? = null,
    val vendor_dlkm: String? = null,
    val init_boot: String? = null,
    val recovery: String? = null
) {
    companion object {
        fun from(sparseMap: Map<String, String>) = object {
            val map = sparseMap.withDefault { null }
            val boot by map
            val vbmeta by map
            val dtbo by map
            val vendor_boot by map
            val vendor_dlkm by map
            val init_boot by map
            val recovery by map
            val partitions = Partitions(boot, vbmeta, dtbo, vendor_boot, vendor_dlkm, init_boot, recovery)
        }.partitions
    }
}
