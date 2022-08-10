package com.github.capntrips.kernelflasher.common.extensions

import kotlin.ByteArray

object ByteArray {
    fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
}
