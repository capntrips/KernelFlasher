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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
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

    private fun copyAsset(filename: String) {
        val dest = File(filesDir, filename)
        assets.open(filename).use { inputStream ->
            dest.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Shell.cmd("chmod +x $dest").exec()
    }

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
                        val slotViewModelA = mainViewModel.toSlotViewModelA()
                        val slotStateA by slotViewModelA.uiState.collectAsState()
                        val slotViewModelB = mainViewModel.toSlotViewModelB()
                        val slotStateB by slotViewModelA.uiState.collectAsState()
                        val backupsViewModel = mainViewModel.toBackupsViewModel()
                        val backupsState by backupsViewModel.uiState.collectAsState()
                        AnimatedNavHost(navController = navController, startDestination = "main") {
                            composable("main") {
                                slotStateA.wasFlashed = false
                                slotStateB.wasFlashed = false
                                RefreshableScreen(mainViewModel, navController) {
                                    MainContent(mainViewModel, navController)
                                }
                            }
                            composable("slotA") {
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotContent(slotViewModelA, "_a", navController)
                                }
                            }
                            composable("slotA/backups") {
                                backupsState.clearCurrent()
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotBackupsContent(slotViewModelA, backupsViewModel, "_a", navController)
                                }
                            }
                            composable("slotA/backups/{backupId}") {backStackEntry ->
                                backupsState.currentBackup = backStackEntry.arguments?.getString("backupId")
                                if (backupsState.backups.containsKey(backupsState.currentBackup)) {
                                    RefreshableScreen(mainViewModel, navController) {
                                        SlotBackupsContent(slotViewModelA, backupsViewModel, "_a", navController)
                                    }
                                }
                            }
                            composable("slotB") {
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotContent(slotViewModelB, "_b", navController)
                                }
                            }
                            composable("slotB/backups") {
                                backupsState.clearCurrent()
                                RefreshableScreen(mainViewModel, navController) {
                                    SlotBackupsContent(slotViewModelB, backupsViewModel, "_b", navController)
                                }
                            }
                            composable("slotB/backups/{backupId}") {backStackEntry ->
                                backupsState.currentBackup = backStackEntry.arguments?.getString("backupId")
                                if (backupsState.backups.containsKey(backupsState.currentBackup)) {
                                    RefreshableScreen(mainViewModel, navController) {
                                        SlotBackupsContent(slotViewModelB, backupsViewModel, "_b", navController)
                                    }
                                }
                            }
                            composable("backups") {
                                backupsState.clearCurrent()
                                RefreshableScreen(mainViewModel, navController) {
                                    BackupsContent(backupsViewModel, navController)
                                }
                            }
                            composable("backups/{backupId}") {backStackEntry ->
                                backupsState.currentBackup = backStackEntry.arguments?.getString("backupId")
                                if (backupsState.backups.containsKey(backupsState.currentBackup)) {
                                    RefreshableScreen(mainViewModel, navController) {
                                        BackupsContent(backupsViewModel, navController)
                                    }
                                }
                            }
                            composable("error/{error}") {backStackEntry ->
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
