package com.github.capntrips.kernelflasher

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.github.capntrips.kernelflasher.ui.screens.RefreshableScreen
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsContent
import com.github.capntrips.kernelflasher.ui.screens.backups.SlotBackupsContent
import com.github.capntrips.kernelflasher.ui.screens.error.ErrorScreen
import com.github.capntrips.kernelflasher.ui.screens.main.MainContent
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModel
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModelFactory
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotFlashContent
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.topjohnwu.superuser.Shell
import java.io.File


@ExperimentalMaterial3Api
@ExperimentalAnimationApi
class MainActivity : ComponentActivity() {
    private lateinit var mainListener: MainListener
    var isAwaitingResult = false

    @Suppress("SameParameterValue")
    private fun copyAsset(filename: String) {
        val dest = File(filesDir, filename)
        assets.open(filename).use { inputStream ->
            dest.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Shell.cmd("chmod +x $dest").exec()
    }

    @Suppress("OPT_IN_MARKER_ON_OVERRIDE_WARNING")
    @ExperimentalUnitApi
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val scale = ObjectAnimator.ofPropertyValuesHolder(
                splashScreenView,
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

        setContent {
            KernelFlasherTheme {
                Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
                if (Shell.getShell().status > Shell.NON_ROOT_SHELL) {
                    Shell.cmd("cd $filesDir").exec()
                    copyAsset("lptools")


                    val navController = rememberAnimatedNavController()
                    val mainViewModel: MainViewModel by viewModels { MainViewModelFactory(this, navController) }
                    if (!mainViewModel.hasError) {
                        mainListener = MainListener {
                            mainViewModel.refresh(this)
                        }
                        val slotViewModelA = mainViewModel.slotA
                        val slotViewModelB = mainViewModel.slotB
                        val backupsViewModel = mainViewModel.backups
                        AnimatedNavHost(navController = navController, startDestination = "main") {
                            composable("main") {
                                RefreshableScreen(mainViewModel, navController) {
                                    MainContent(mainViewModel, navController)
                                }
                            }
                            composable("slot{slotSuffix}") { backStackEntry ->
                                val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                                val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                                if (slotSuffix == "_a") {
                                    slotViewModelA.clearFlash()
                                } else {
                                    slotViewModelB.clearFlash()
                                }
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotContent(slotViewModel, slotSuffix, navController)
                                }
                            }
                            composable("slot{slotSuffix}/flash") { backStackEntry ->
                                val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                                val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotFlashContent(slotViewModel, navController)
                                }
                            }
                            composable("slot{slotSuffix}/backups") { backStackEntry ->
                                val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                                val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                                backupsViewModel.clearCurrent()
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotBackupsContent(slotViewModel, backupsViewModel, slotSuffix, navController)
                                }
                            }
                            composable("slot{slotSuffix}/backups/{backupId}") { backStackEntry ->
                                val slotSuffix = backStackEntry.arguments?.getString("slotSuffix")!!
                                val slotViewModel = if (slotSuffix == "_a") slotViewModelA else slotViewModelB
                                backupsViewModel.currentBackup = backStackEntry.arguments?.getString("backupId")
                                if (backupsViewModel.backups.containsKey(backupsViewModel.currentBackup)) {
                                    RefreshableScreen(mainViewModel, navController) {
                                        SlotBackupsContent(slotViewModel, backupsViewModel, slotSuffix, navController)
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
                            composable("error/{error}") { backStackEntry ->
                                val error = backStackEntry.arguments?.getString("error")
                                ErrorScreen(error!!)
                            }
                        }
                    } else {
                        ErrorScreen(mainViewModel.error)
                    }
                } else {
                    ErrorScreen(stringResource(R.string.root_required))
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
