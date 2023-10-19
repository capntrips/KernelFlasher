package com.github.capntrips.kernelflasher.common.types.partitions

import kotlinx.serialization.Serializable

@Serializable
data class Partitions(
    val boot: String? = null,
    val dtbo: String? = null,
    @Suppress("PropertyName") val init_boot: String? = null,
    val recovery: String? = null,
    @Suppress("PropertyName") val system_dlkm: String? = null,
    val vbmeta: String? = null,
    @Suppress("PropertyName") val vendor_boot: String? = null,
    @Suppress("PropertyName") val vendor_dlkm: String? = null,
    @Suppress("PropertyName") val vendor_kernel_boot: String? = null
) {
    companion object {
        fun from(sparseMap: Map<String, String>) = object {
            val map = sparseMap.withDefault { null }
            val boot by map
            val dtbo by map
            val init_boot by map
            val recovery by map
            val system_dlkm by map
            val vbmeta by map
            val vendor_boot by map
            val vendor_dlkm by map
            val vendor_kernel_boot by map
            val partitions = Partitions(boot, dtbo, init_boot, recovery, system_dlkm, vbmeta, vendor_boot, vendor_dlkm, vendor_kernel_boot)
        }.partitions
    }

    fun get(partition: String): String? {
        return when (partition) {
            "boot" -> boot
            "dtbo" -> dtbo
            "init_boot" -> init_boot
            "recovery" -> recovery
            "system_dlkm" -> system_dlkm
            "vbmeta" -> vbmeta
            "vendor_boot" -> vendor_boot
            "vendor_dlkm" -> vendor_dlkm
            "vendor_kernel_boot" -> vendor_kernel_boot
            else -> null
        }
    }
}
