package com.github.capntrips.kernelflasher.common.extensions

import com.topjohnwu.superuser.nio.ExtendedFile
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.Charset

object ExtendedFile {
    private fun ExtendedFile.reader(charset: Charset = Charsets.UTF_8): InputStreamReader = inputStream().reader(charset)

    private fun ExtendedFile.writeBytes(array: kotlin.ByteArray): Unit = outputStream().use { it.write(array) }

    fun ExtendedFile.readText(charset: Charset = Charsets.UTF_8): String = reader(charset).use { it.readText() }

    @Suppress("unused")
    fun ExtendedFile.writeText(text: String, charset: Charset = Charsets.UTF_8): Unit = writeBytes(text.toByteArray(charset))

    fun ExtendedFile.inputStream(): InputStream = newInputStream()

    fun ExtendedFile.outputStream(): OutputStream = newOutputStream()
}
