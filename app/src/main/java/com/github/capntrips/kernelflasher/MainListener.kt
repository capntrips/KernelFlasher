package com.github.capntrips.kernelflasher

internal class MainListener constructor(private val callback: () -> Unit) {
    fun resume() {
        callback.invoke()
    }
}
