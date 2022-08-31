package com.github.capntrips.kernelflasher.ui.screens.updates

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.room.Room
import com.github.capntrips.kernelflasher.common.types.room.AppDatabase
import com.github.capntrips.kernelflasher.common.types.room.updates.Update
import com.github.capntrips.kernelflasher.common.types.room.updates.UpdateSerializer
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.name

class UpdatesViewModel(
    context: Context,
    @Suppress("unused") private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>
) : ViewModel() {
    companion object {
        @Suppress("unused")
        const val TAG: String = "KernelFlasher/UpdatesState"
        val lastUpdatedFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val client = OkHttpClient()
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "kernel-flasher").build()
    private val updateDao = db.updateDao()
    private val _updates: SnapshotStateList<Update> = mutableStateListOf()

    var currentUpdate: Update? = null
    var changelog: String? = null

    val updates: List<Update>
        get() = _updates
    val isRefreshing: Boolean
        get() = _isRefreshing.value

    init {
        launch {
            val updates = updateDao.getAll()
            viewModelScope.launch(Dispatchers.Main) {
                _updates.addAll(updates)
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            viewModelScope.launch(Dispatchers.Main) {
                _isRefreshing.value = true
            }
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
            viewModelScope.launch(Dispatchers.Main) {
                _isRefreshing.value = false
            }
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
            throw Exception(message)
        }
    }

    fun clearCurrent() {
        currentUpdate = null
        changelog = null
    }

    @ExperimentalSerializationApi
    @Suppress("BlockingMethodInNonBlockingContext")
    fun add(url: String, callback: (updateId: Int) -> Unit) {
        launch {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                val update: Update = Json.decodeFromString(UpdateSerializer, response.body!!.string())
                update.updateUri = url
                update.lastUpdated = Date()
                val updateId = updateDao.insert(update).toInt()
                val inserted = updateDao.load(updateId)
                withContext (Dispatchers.Main) {
                    _updates.add(inserted)
                    callback.invoke(updateId)
                }
            }
        }
    }

    @ExperimentalSerializationApi
    @Suppress("BlockingMethodInNonBlockingContext")
    fun update() {
        launch {
            val request = Request.Builder()
                .url(currentUpdate!!.updateUri!!)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                val update: Update = Json.decodeFromString(UpdateSerializer, response.body!!.string())
                currentUpdate!!.let {
                    withContext (Dispatchers.Main) {
                        it.kernelName = update.kernelName
                        it.kernelVersion = update.kernelVersion
                        it.kernelLink = update.kernelLink
                        it.kernelChangelogUrl = update.kernelChangelogUrl
                        it.kernelDate = update.kernelDate
                        it.kernelSha1 = update.kernelSha1
                        it.supportLink = update.supportLink
                        it.lastUpdated = Date()
                        viewModelScope.launch(Dispatchers.IO) {
                            updateDao.update(it)
                        }
                    }
                }
            }
        }
    }

    @ExperimentalSerializationApi
    @Suppress("BlockingMethodInNonBlockingContext")
    fun downloadChangelog(callback: () -> Unit) {
        launch {
            val request = Request.Builder()
                .url(currentUpdate!!.kernelChangelogUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                changelog = response.body!!.string()
                withContext (Dispatchers.Main) {
                    callback.invoke()
                }
            }
        }
    }

    private fun insertDownload(context: Context, filename: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        return resolver.insert(MediaStore.Files.getContentUri("external"), values)
    }

    @SuppressLint("SdCardPath")
    @ExperimentalSerializationApi
    @Suppress("BlockingMethodInNonBlockingContext")
    fun downloadKernel(context: Context) {
        launch {
            val remoteUri = Uri.parse(currentUpdate!!.kernelLink)
            val filename = Path(remoteUri.path!!).name
            val localUri = insertDownload(context, filename)
            localUri!!.let { uri ->
                val request = Request.Builder()
                    .url(remoteUri.toString())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                    response.body!!.byteStream().use { inputStream ->
                        context.contentResolver.openOutputStream(uri)!!.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    log(context, "Saved $filename to Downloads")
                }
            }
        }
    }

    fun delete(callback: () -> Unit) {
        launch {
            updateDao.delete(currentUpdate!!)
            withContext (Dispatchers.Main) {
                _updates.remove(currentUpdate!!)
                callback.invoke()
                currentUpdate = null
            }
        }
    }
}