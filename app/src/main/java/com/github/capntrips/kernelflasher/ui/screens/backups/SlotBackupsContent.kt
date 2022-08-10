package com.github.capntrips.kernelflasher.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import com.github.capntrips.kernelflasher.ui.components.ViewButton
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel

@ExperimentalMaterial3Api
@Composable
fun ColumnScope.SlotBackupsContent(
    slotViewModel: SlotViewModel,
    backupsViewModel: BackupsViewModel,
    slotSuffix: String,
    navController: NavController
) {
    val context = LocalContext.current
    SlotCard(
        title = stringResource(if (slotSuffix == "_a") R.string.slot_a else R.string.slot_b),
        viewModel = slotViewModel,
        navController = navController,
        isSlotScreen = true
    )
    Spacer(Modifier.height(16.dp))
    if (backupsViewModel.currentBackup != null && backupsViewModel.backups.containsKey(backupsViewModel.currentBackup)) {
        val currentBackup = backupsViewModel.backups.getValue(backupsViewModel.currentBackup!!)
        DataCard (backupsViewModel.currentBackup!!) {
            DataRow(stringResource(R.string.backup_type), currentBackup.type)
            if (currentBackup.type == "raw") {
                DataRow(stringResource(R.string.boot_sha1), currentBackup.bootSha1!!.substring(0, 8))
            }
            DataRow(stringResource(R.string.kernel_version), currentBackup.kernelVersion)
        }
        AnimatedVisibility(!slotViewModel.isRefreshing) {
            Column {
                Spacer(Modifier.height(5.dp))
                if (!backupsViewModel.wasRestored) {
                    if (slotViewModel.isActive) {
                        if (currentBackup.type == "raw") {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = {
                                    backupsViewModel.restore(context, slotSuffix)
                                }
                            ) {
                                Text(stringResource(R.string.restore))
                            }
                        } else if (currentBackup.type == "ak3") {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = {
                                    slotViewModel.checkZip(context, backupsViewModel.currentBackup!!, currentBackup.filename!!) {
                                        navController.navigate("slot$slotSuffix/backups/${backupsViewModel.currentBackup!!}/flash")
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.flash))
                            }
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { backupsViewModel.delete(context) { navController.popBackStack() } }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { navController.navigate("reboot") }
                    ) {
                        Text(stringResource(R.string.reboot))
                    }
                }
            }
        }
    } else {
        DataCard(stringResource(R.string.backups))
        val backups = backupsViewModel.backups.filter { it.value.bootSha1.equals(slotViewModel.sha1) || it.value.type == "ak3" }
        if (backups.isNotEmpty()) {
            for (id in backups.keys.sortedByDescending { it }) {
                Spacer(Modifier.height(16.dp))
                DataCard(
                    title = id,
                    button = {
                        AnimatedVisibility(!slotViewModel.isRefreshing) {
                            ViewButton(onClick = {
                                navController.navigate("slot$slotSuffix/backups/$id")
                            })
                        }
                    }
                ) {
                    DataRow(stringResource(R.string.kernel_version), backups[id]!!.kernelVersion)
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