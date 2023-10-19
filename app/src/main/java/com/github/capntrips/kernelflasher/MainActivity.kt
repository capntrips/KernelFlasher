package com.github.capntrips.kernelflasher

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.capntrips.kernelflasher.ui.screens.RefreshableScreen
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsContent
import com.github.capntrips.kernelflasher.ui.screens.backups.SlotBackupsContent
import com.github.capntrips.kernelflasher.ui.screens.error.ErrorScreen
import com.github.capntrips.kernelflasher.ui.screens.main.MainContent
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModel
import com.github.capntrips.kernelflasher.ui.screens.reboot.RebootContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotFlashContent
import com.github.capntrips.kernelflasher.ui.screens.updates.UpdatesAddContent
import com.github.capntrips.kernelflasher.ui.screens.updates.UpdatesChangelogContent
import com.github.capntrips.kernelflasher.ui.screens.updates.UpdatesContent
import com.github.capntrips.kernelflasher.ui.screens.updates.UpdatesViewContent
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File


@ExperimentalAnimationApi
@ExperimentalMaterialApi
@ExperimentalMaterial3Api
@ExperimentalSerializationApi
@ExperimentalUnitApi
class MainActivity : ComponentActivity() {
    companion object {
        const val TAG: String = "MainActivity"
        init {
            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        }
    }

    private var rootServiceConnected: Boolean = false
    private var viewModel: MainViewModel? = null
    private lateinit var mainListener: MainListener
    var isAwaitingResult = false

    inner class AidlConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!rootServiceConnected) {
                val ipc: IFilesystemService = IFilesystemService.Stub.asInterface(service)
                val binder: IBinder = ipc.fileSystemService
                onAidlConnected(FileSystemManager.getRemote(binder))
                rootServiceConnected = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            setContent {
                KernelFlasherTheme {
                    ErrorScreen(stringResource(R.string.root_service_disconnected))
                }
            }
        }
    }

    private fun copyAsset(filename: String) {
        val dest = File(filesDir, filename)
        assets.open(filename).use { inputStream ->
            dest.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Shell.cmd("chmod +x $dest").exec()
    }

    private fun copyNativeBinary(filename: String) {
        val binary = File(applicationInfo.nativeLibraryDir, "lib$filename.so")
        println("binary: $binary")
        val dest = File(filesDir, filename)
        println("dest: $dest")
        binary.inputStream().use { inputStream ->
            dest.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Shell.cmd("chmod +x $dest").exec()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val scale = ObjectAnimator.ofPropertyValuesHolder(
                splashScreenView.view,
                PropertyValuesHolder.ofFloat(
                    View.SCALE_X,
                    1f,
                    0f
                ),
                PropertyValuesHolder.ofFloat(
                    View.SCALE_Y,
                    1f,
                    0f
                )
            )
            scale.interpolator = AccelerateInterpolator()
            scale.duration = 250L
            scale.doOnEnd { splashScreenView.remove() }
            scale.start()
        }

        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (viewModel?.isRefreshing == false || Shell.isAppGrantedRoot() == false) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        Shell.getShell()
        if (Shell.isAppGrantedRoot()!!) {
            val intent = Intent(this, FilesystemService::class.java)
            RootService.bind(intent, AidlConnection())
        } else {
            setContent {
                KernelFlasherTheme {
                    ErrorScreen(stringResource(R.string.root_required))
                }
            }
        }
    }

    fun onAidlConnected(fileSystemManager: FileSystemManager) {
        try {
            Shell.cmd("cd $filesDir").exec()
            copyNativeBinary("lptools_static") // v20220825
            copyNativeBinary("httools_static") // v3.2.0
            copyNativeBinary("magiskboot") // v25.2
            copyAsset("flash_ak3.sh")
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            setContent {
                KernelFlasherTheme {
                    ErrorScreen(e.message!!)
                }
            }
        }
        setContent {
            val navController = rememberNavController()
            viewModel = viewModel {
                val application = checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
                MainViewModel(application, fileSystemManager, navController)
            }
            val mainViewModel = viewModel!!
            KernelFlasherTheme {
                if (!mainViewModel.hasError) {
                    mainListener = MainListener {
                        mainViewModel.refresh(this)
                    }
                    val slotViewModelA = mainViewModel.slotA
                    val slotViewModelB = mainViewModel.slotB
                    val backupsViewModel = mainViewModel.backups
                    val updatesViewModel = mainViewModel.updates
                    val rebootViewModel = mainViewModel.reboot
                    BackHandler(enabled = mainViewModel.isRefreshing, onBack = {})
                    val slotFlashContent: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit = { backStackEntry ->
                        val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                        val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                        RefreshableScreen(mainViewModel, navController) {
                            SlotFlashContent(slotViewModel, slotSuffix, navController)
                        }
                    }
                    val slotBackupsContent: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit = { backStackEntry ->
                        val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                        val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                        if (backStackEntry.arguments?.getString("backupId") != null) {
                            backupsViewModel.currentBackup = backStackEntry.arguments?.getString("backupId")
                        } else {
                            backupsViewModel.clearCurrent()
                        }
                        RefreshableScreen(mainViewModel, navController) {
                            SlotBackupsContent(slotViewModel, backupsViewModel, slotSuffix, navController)
                        }
                    }
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            RefreshableScreen(mainViewModel, navController, swipeEnabled = true) {
                                MainContent(mainViewModel, navController)
                            }
                        }
                        composable("slot{slotSuffix}") { backStackEntry ->
                            val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                            val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                            if (slotViewModel.wasFlashSuccess != null && navController.currentDestination!!.route.equals("slot{slotSuffix}")) {
                                slotViewModel.clearFlash(this@MainActivity)
                            }
                            RefreshableScreen(mainViewModel, navController, swipeEnabled = true) {
                                SlotContent(slotViewModel, slotSuffix, navController)
                            }
                        }
                        composable("slot{slotSuffix}/flash", content = slotFlashContent)
                        composable("slot{slotSuffix}/flash/ak3", content = slotFlashContent)
                        composable("slot{slotSuffix}/flash/image", content = slotFlashContent)
                        composable("slot{slotSuffix}/flash/image/flash", content = slotFlashContent)
                        composable("slot{slotSuffix}/backup", content = slotFlashContent)
                        composable("slot{slotSuffix}/backup/backup", content = slotFlashContent)
                        composable("slot{slotSuffix}/backups", content = slotBackupsContent)
                        composable("slot{slotSuffix}/backups/{backupId}", content = slotBackupsContent)
                        composable("slot{slotSuffix}/backups/{backupId}/restore", content = slotBackupsContent)
                        composable("slot{slotSuffix}/backups/{backupId}/restore/restore", content = slotBackupsContent)
                        composable("slot{slotSuffix}/backups/{backupId}/flash/ak3") { backStackEntry ->
                            val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                            val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                            backupsViewModel.currentBackup = backStackEntry.arguments?.getString("backupId")
                            if (backupsViewModel.backups.containsKey(backupsViewModel.currentBackup)) {
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotFlashContent(slotViewModel, slotSuffix, navController)
                                }
                            }
                        }
                        composable("backups") {
                            backupsViewModel.clearCurrent()
                            RefreshableScreen(mainViewModel, navController) {
                                BackupsContent(backupsViewModel, navController)
                            }
                        }
                        composable("backups/{backupId}") { backStackEntry ->
                            backupsViewModel.currentBackup = backStackEntry.arguments?.getString("backupId")
                            if (backupsViewModel.backups.containsKey(backupsViewModel.currentBackup)) {
                                RefreshableScreen(mainViewModel, navController) {
                                    BackupsContent(backupsViewModel, navController)
                                }
                            }
                        }
                        composable("updates") {
                            updatesViewModel.clearCurrent()
                            RefreshableScreen(mainViewModel, navController) {
                                UpdatesContent(updatesViewModel, navController)
                            }
                        }
                        composable("updates/add") {
                            RefreshableScreen(mainViewModel, navController) {
                                UpdatesAddContent(updatesViewModel, navController)
                            }
                        }
                        composable("updates/view/{updateId}") { backStackEntry ->
                            val updateId = backStackEntry.arguments?.getString("updateId")!!.toInt()
                            val currentUpdate = updatesViewModel.updates.firstOrNull { it.id == updateId }
                            updatesViewModel.currentUpdate = currentUpdate
                            if (updatesViewModel.currentUpdate != null) {
                                // TODO: enable swipe refresh
                                RefreshableScreen(mainViewModel, navController) {
                                    UpdatesViewContent(updatesViewModel, navController)
                                }
                            }
                        }
                        composable("updates/view/{updateId}/changelog") { backStackEntry ->
                            val updateId = backStackEntry.arguments?.getString("updateId")!!.toInt()
                            val currentUpdate = updatesViewModel.updates.firstOrNull { it.id == updateId }
                            updatesViewModel.currentUpdate = currentUpdate
                            if (updatesViewModel.currentUpdate != null) {
                                RefreshableScreen(mainViewModel, navController) {
                                    UpdatesChangelogContent(updatesViewModel, navController)
                                }
                            }
                        }
                        composable("reboot") {
                            RefreshableScreen(mainViewModel, navController) {
                                RebootContent(rebootViewModel, navController)
                            }
                        }
                        composable("error/{error}") { backStackEntry ->
                            val error = backStackEntry.arguments?.getString("error")
                            ErrorScreen(error!!)
                        }
                    }
                } else {
                    ErrorScreen(mainViewModel.error)
                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        if (this::mainListener.isInitialized) {
            if (!isAwaitingResult) {
                mainListener.resume()
            }
        }
    }
}
