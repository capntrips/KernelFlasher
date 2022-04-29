package com.github.capntrips.kernelflasher.ui.screens.backups

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import com.github.capntrips.kernelflasher.ui.components.ViewButton
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModelInterface
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModelPreview
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme

@ExperimentalMaterial3Api
@Composable
fun SlotBackupsContent(
    slotViewModel: SlotViewModelInterface,
    backupsViewModel: BackupsViewModelInterface,
    slotSuffix: String,
    navController: NavController
) {
    val slotUiState by slotViewModel.uiState.collectAsState()
    val backupsUiState by backupsViewModel.uiState.collectAsState()
    val isRefreshing by slotUiState.isRefreshing.collectAsState()
    val context = LocalContext.current
    Column {
        SlotCard(
            title = stringResource(if (slotSuffix == "_a") R.string.slot_a else R.string.slot_b),
            slotStateFlow = slotViewModel.uiState,
            navController = navController,
            isSlotScreen = true
        )
        Spacer(Modifier.height(16.dp))
        if (backupsUiState.currentBackup != null && backupsUiState.backups.containsKey(backupsUiState.currentBackup)) {
            DataCard (backupsUiState.currentBackup!!) {
                val props = backupsUiState.backups.getValue(backupsUiState.currentBackup!!)
                DataRow(stringResource(R.string.boot_sha1), props.getProperty("sha1").substring(0, 8))
                DataRow(stringResource(R.string.kernel_version), props.getProperty("kernel"))
            }
            AnimatedVisibility(!isRefreshing) {
                Column {
                    Spacer(Modifier.height(5.dp))
                    if (!backupsUiState.wasRestored) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = {
                                backupsUiState.restore(context, slotSuffix)
                            }
                        ) {
                            Text(stringResource(R.string.restore))
                        }
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { backupsUiState.delete(context) { navController.popBackStack() } }
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { backupsUiState.reboot() }
                        ) {
                            Text(stringResource(R.string.reboot))
                        }
                    }
                }
            }
        } else {
            DataCard(stringResource(R.string.backups))
            val backups = backupsUiState.backups.filter { it.value.getValue("sha1").equals(slotUiState.sha1) }
            if (backups.isNotEmpty()) {
                for ((id, props) in backups) {
                    Spacer(Modifier.height(16.dp))
                    DataCard(
                        title = id,
                        button = {
                            AnimatedVisibility(!isRefreshing) {
                                ViewButton(onClick = {
                                    if (slotSuffix == "_a")
                                        navController.navigate("slotA/backups/$id")
                                    else
                                        navController.navigate("slotB/backups/$id")
                                })
                            }
                        }
                    ) {
                        DataRow(stringResource(R.string.kernel_version), props.getProperty("kernel"))
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.no_backups_found),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@ExperimentalMaterial3Api
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun SlotBackupsContentPreviewDark() {
    SlotBackupsContentPreviewLight()
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun SlotBackupsContentPreviewLight() {
    KernelFlasherTheme {
        Scaffold {
            val slotViewModel: SlotViewModelPreview = viewModel()
            val backupsViewModel: BackupsViewModelPreview = viewModel()
            val navController = rememberNavController()
            SlotBackupsContent(slotViewModel, backupsViewModel, "_a", navController)
        }
    }
}
