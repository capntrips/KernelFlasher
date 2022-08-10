package com.github.capntrips.kernelflasher.common.types

import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val name: String,
    val type: String,
    val kernelVersion: String,
    val bootSha1: String? = null,
    val filename: String? = null,
    val hashes: Partitions? = null,
    val hashAlgorithm: String? = null
)
